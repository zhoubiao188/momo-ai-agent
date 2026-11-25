package com.momo.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 冒烟测试：真正把 Spring 上下文拉起来。
 * 目的不是测业务，而是拦住「构造器注入配错 / 配置项缺失」这类只有启动时才暴露的问题。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "agent.llm.mode=mock",
        // 指向一个必然连不上的端口，验证「工具失败不中断主流程」
        "agent.tools.biz-base-url=http://127.0.0.1:1"
})
@AutoConfigureMockMvc
class AgentApplicationTest {

    @LocalServerPort
    private int port;

    private String baseUrl;

    @Autowired
    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Autowired
    private ObjectMapper mapper;

    @Test
    void healthShouldReportMockModeAndKnowledge() throws Exception {
        mockMvc.perform(get("/api/agent/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.llmMode").value("mock"))
                .andExpect(jsonPath("$.knowledgeChunks").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void toolsShouldBeRegistered() throws Exception {
        mockMvc.perform(get("/api/agent/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tools").isArray())
                .andExpect(jsonPath("$.tools.length()").value(4));
    }

    @Test
    void chatShouldStillAnswerWhenToolsFail() throws Exception {
        String body = """
                {"sessionId":"it-1","question":"订单 MO20260101001 为什么一直没发货？"}
                """;
        String response = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = mapper.readTree(response);
        assertThat(json.path("answer").asText()).isNotBlank();
        assertThat(json.path("toolCalls").asInt()).isGreaterThanOrEqualTo(2);
    }

    /**
     * SSE 必须用真实 HTTP 连接测：MockMvc 拿不到异步分片完整体，
     * 而且会把 text/event-stream 按 ISO-8859-1 解码，中文会变乱码。
     */
    @Test
    void streamEndpointShouldEmitSseFrames() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/agent/chat/stream"))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"sessionId\":\"it-2\",\"question\":\"看看 tr-9f2a41 的报错\"}", StandardCharsets.UTF_8))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("text/event-stream");

        List<String> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    events.add(line.substring(6).trim());
                }
                if (line.startsWith("data:") && line.contains("\"type\":\"final\"")) {
                    assertThat(line).contains("结论");
                }
            }
        }
        assertThat(events).containsSubsequence("start", "step", "tool_call", "tool_result", "final", "done");
    }
}
