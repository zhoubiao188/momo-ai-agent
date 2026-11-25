package com.momo.ai.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.ai.agent.config.AgentProperties;
import com.momo.ai.agent.llm.ChatMessage;
import com.momo.ai.agent.llm.LlmClient;
import com.momo.ai.agent.llm.LlmResponse;
import com.momo.ai.agent.tool.AgentTool;
import com.momo.ai.agent.tool.ToolContext;
import com.momo.ai.agent.tool.ToolRegistry;
import com.momo.ai.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Agent 主循环（ReAct + Function Calling）。
 *
 * 一次 run() 的完整生命周期：
 *   start -> [step -> delta* -> tool_call -> tool_result]* -> final -> done
 *
 * 设计上的几个取舍：
 * - 同步阻塞：LLM 调用与工具执行都在同一个工作线程里串行完成，逻辑线性、可读、易调试；
 *   并发能力交给 Web 容器（每个请求一个 SSE 工作线程），够用且不会踩响应式的坑。
 * - 步数上限：模型可能无限调用工具，maxSteps 是必须有的止损线。
 * - 工具失败不中断：把错误作为 tool 消息喂回模型，让它自己决定换策略还是直接回答。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentLoop {

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final SessionStore sessionStore;
    private final AgentProperties props;
    private final ObjectMapper mapper;
    private final InputGuard inputGuard;

    private volatile String systemPrompt;

    public void run(String sessionId, String question, Consumer<AgentEvent> sink) {
        AgentSession session = sessionStore.getOrCreate(sessionId);
        sink.accept(AgentEvent.start(sessionId));

        Optional<String> violation = inputGuard.check(question);
        if (violation.isPresent()) {
            sink.accept(AgentEvent.finalAnswer(sessionId, violation.get()));
            sink.accept(AgentEvent.done(sessionId));
            return;
        }

        String cleanQuestion = InputGuard.sanitize(question);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt()));
        messages.addAll(trimHistory(session.snapshot()));
        messages.add(ChatMessage.user(cleanQuestion));

        JsonNode tools = toolRegistry.toolsSchema(props.tools().allowMutating());
        ToolContext context = new ToolContext(sessionId, cleanQuestion);
        boolean answered = false;

        try {
            for (int step = 1; step <= props.llm().maxSteps(); step++) {
                sink.accept(AgentEvent.step(sessionId, step));

                LlmResponse response = callLlm(messages, tools, sessionId, step, sink);
                if (!response.hasToolCalls()) {
                    String answer = response.content() == null ? "" : response.content();
                    session.append(ChatMessage.user(cleanQuestion), ChatMessage.assistant(answer, List.of()));
                    sink.accept(AgentEvent.finalAnswer(sessionId, answer));
                    answered = true;
                    break;
                }

                messages.add(ChatMessage.assistant(response.content(), response.toolCalls()));
                session.append(ChatMessage.assistant(response.content(), response.toolCalls()));

                int calls = Math.min(response.toolCalls().size(), props.guard().maxToolCallsPerStep());
                for (int i = 0; i < calls; i++) {
                    ChatMessage.ToolCall call = response.toolCalls().get(i);
                    ChatMessage toolMessage = executeTool(session, context, call, step, call.arguments(), sink);
                    messages.add(toolMessage);
                }
                if (response.toolCalls().size() > calls) {
                    log.warn("tool calls truncated: {} -> {}", response.toolCalls().size(), calls);
                }
            }

            if (!answered) {
                String msg = "已达到最大推理步数（" + props.llm().maxSteps()
                        + "），我先把已知信息汇总给你。可以补充更具体的订单号或 TraceId 再试。";
                session.append(ChatMessage.assistant(msg, List.of()));
                sink.accept(AgentEvent.finalAnswer(sessionId, msg));
            }
        } catch (Exception e) {
            log.error("agent loop failed, session={}", sessionId, e);
            sink.accept(AgentEvent.error(sessionId, "Agent 执行失败：" + e.getMessage()));
        } finally {
            sink.accept(AgentEvent.done(sessionId));
        }
    }

    private LlmResponse callLlm(List<ChatMessage> messages, JsonNode tools,
                                String sessionId, int step, Consumer<AgentEvent> sink) {
        return llmClient.stream(messages, tools, delta -> sink.accept(AgentEvent.delta(sessionId, step, delta)));
    }

    private ChatMessage executeTool(AgentSession session, ToolContext context, ChatMessage.ToolCall call,
                                    int step, String rawArguments, Consumer<AgentEvent> sink) {
        String name = call.name();
        String args = rawArguments == null || rawArguments.isBlank() ? "{}" : rawArguments;
        sink.accept(AgentEvent.toolCall(session.id(), step, call.id(), name, args));

        if (args.length() > props.guard().maxToolArgumentsChars()) {
            ToolResult tooLarge = ToolResult.fail("tool arguments too large", 0);
            sink.accept(AgentEvent.toolResult(session.id(), step, call.id(), name,
                    tooLarge.content(), false, tooLarge.elapsedMs()));
            return ChatMessage.tool(call.id(), name, tooLarge.content());
        }

        Optional<AgentTool> found = toolRegistry.find(name);
        ToolResult result;
        if (found.isEmpty()) {
            result = ToolResult.fail("unknown tool: " + name, 0);
        } else if (found.get().mutating() && !props.tools().allowMutating()) {
            result = ToolResult.fail("tool disabled by policy: " + name, 0);
        } else {
            JsonNode parsed;
            try {
                parsed = mapper.readTree(args);
            } catch (Exception e) {
                result = ToolResult.fail("invalid JSON arguments: " + e.getMessage(), 0);
                sink.accept(AgentEvent.toolResult(session.id(), step, call.id(), name,
                        result.content(), false, result.elapsedMs()));
                return ChatMessage.tool(call.id(), name, result.content());
            }
            result = toolRegistry.execute(found.get(), context, parsed, props.tools().timeout().toMillis());
        }

        sink.accept(AgentEvent.toolResult(session.id(), step, call.id(), name,
                result.content(), result.success(), result.elapsedMs()));
        ChatMessage toolMessage = ChatMessage.tool(call.id(), name, result.content());
        session.append(toolMessage);
        return toolMessage;
    }

    /**
     * 历史裁剪必须按「整轮」来切，不能从中间截断：
     * 如果留下一条 tool 消息却丢了它对应的 assistant.tool_calls，OpenAI 兼容接口会直接 400。
     */
    private List<ChatMessage> trimHistory(List<ChatMessage> history) {
        int max = props.llm().maxHistoryMessages();
        if (history.size() <= max) {
            return history;
        }
        int start = history.size() - max;
        while (start < history.size() && !"user".equals(history.get(start).role())) {
            start++;
        }
        return start >= history.size() ? List.of() : history.subList(start, history.size());
    }

    public String systemPrompt() {
        if (systemPrompt == null) {
            try {
                systemPrompt = StreamUtils.copyToString(
                        new ClassPathResource("prompts/system.md").getInputStream(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                systemPrompt = "你是运维助手，优先使用工具获取事实，再给出结论。";
            }
        }
        return systemPrompt;
    }
}
