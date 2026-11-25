package com.momo.ai.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.ai.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议（/v1/chat/completions + stream=true + tools）的流式客户端。
 * 用 JDK HttpClient 手写 SSE 解析，目的有两个：
 * 1) 不引入额外 SDK，协议细节全部可见，出问题能立刻定位到字节；
 * 2) tool_calls 的分片拼接逻辑本来就是「自己写一次就懂」的东西，封装掉反而更糊涂。
 */
@Slf4j
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public OpenAiCompatibleClient(AgentProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String modelName() {
        return props.llm().model();
    }

    @Override
    public LlmResponse stream(List<ChatMessage> messages, JsonNode toolsSchema, Consumer<String> contentDelta) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.llm().model());
        body.put("messages", messages);
        body.put("stream", true);
        body.put("temperature", props.llm().temperature());
        if (toolsSchema != null && toolsSchema.size() > 0) {
            body.put("tools", toolsSchema);
            body.put("tool_choice", "auto");
        }

        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.llm().baseUrl()) + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + props.llm().apiKey())
                    .header("Accept", "text/event-stream")
                    .timeout(props.llm().timeout())
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + truncate(err, 500));
            }
            return parseStream(response.body(), contentDelta);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("LLM call failed: " + e.getMessage(), e);
        }
    }

    private LlmResponse parseStream(InputStream in, Consumer<String> contentDelta) throws Exception {
        StringBuilder content = new StringBuilder();
        Map<Integer, StringBuilder> argsByIndex = new LinkedHashMap<>();
        Map<Integer, String> idByIndex = new LinkedHashMap<>();
        Map<Integer, String> nameByIndex = new LinkedHashMap<>();
        String finishReason = null;
        int promptTokens = 0;
        int completionTokens = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode chunk = mapper.readTree(data);
                if (chunk.has("error")) {
                    throw new IllegalStateException("LLM stream error: " + chunk.get("error"));
                }
                if (chunk.hasNonNull("usage")) {
                    promptTokens = chunk.get("usage").path("prompt_tokens").asInt(promptTokens);
                    completionTokens = chunk.get("usage").path("completion_tokens").asInt(completionTokens);
                }
                for (JsonNode choice : chunk.path("choices")) {
                    JsonNode delta = choice.path("delta");
                    String text = delta.path("content").asText("");
                    if (!text.isEmpty()) {
                        content.append(text);
                        contentDelta.accept(text);
                    }
                    if (delta.has("tool_calls")) {
                        for (JsonNode tc : delta.get("tool_calls")) {
                            int idx = tc.path("index").asInt(0);
                            if (tc.hasNonNull("id")) {
                                idByIndex.put(idx, tc.get("id").asText());
                            }
                            JsonNode fn = tc.path("function");
                            if (fn.hasNonNull("name")) {
                                nameByIndex.put(idx, fn.get("name").asText());
                            }
                            if (fn.hasNonNull("arguments")) {
                                argsByIndex.computeIfAbsent(idx, k -> new StringBuilder())
                                        .append(fn.get("arguments").asText());
                            }
                        }
                    }
                    if (choice.hasNonNull("finish_reason")) {
                        finishReason = choice.get("finish_reason").asText();
                    }
                }
            }
        }

        List<ChatMessage.ToolCall> toolCalls = new ArrayList<>();
        for (Map.Entry<Integer, StringBuilder> e : argsByIndex.entrySet()) {
            int idx = e.getKey();
            toolCalls.add(ChatMessage.ToolCall.of(
                    idByIndex.getOrDefault(idx, "call_" + idx),
                    nameByIndex.get(idx),
                    e.getValue().toString()));
        }
        return new LlmResponse(content.toString(), toolCalls, finishReason, promptTokens, completionTokens);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
