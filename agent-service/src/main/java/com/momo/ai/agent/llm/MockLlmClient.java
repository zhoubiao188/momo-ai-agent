package com.momo.ai.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无 API Key 时使用的本地「剧本模型」。
 *
 * 它不是在假装自己是大模型，而是把真实 Agent 的行为固定下来：
 * 1. 第一轮：根据问题里的线索决定调用哪些工具（订单号 -> order_lookup，报错/超时 -> log_search，永远带一次 runbook_search）；
 * 2. 第二轮：读取工具结果，如果用户要退款且订单可退，再调用 refund_order；
 * 3. 第三轮：把工具结果整理成结论。
 *
 * 这样 clone 下来就能跑通完整的 Function Calling 编排，联调前端 SSE 也不需要先充钱。
 */
@Component
public class MockLlmClient implements LlmClient {

    private static final Pattern ORDER_NO = Pattern.compile("MO\\d{6,}");
    private static final Pattern TRACE_ID = Pattern.compile("tr-[a-z0-9]{4,}");

    private final ObjectMapper mapper;
    private final long pauseMillis;

    @Autowired
    public MockLlmClient(ObjectMapper mapper) {
        this(mapper, 8L);
    }

    MockLlmClient(ObjectMapper mapper, long pauseMillis) {
        this.mapper = mapper;
        this.pauseMillis = pauseMillis;
    }

    @Override
    public String modelName() {
        return "mock-agent-model";
    }

    @Override
    public LlmResponse stream(List<ChatMessage> messages, JsonNode toolsSchema, Consumer<String> contentDelta) {
        String question = lastUserMessage(messages);
        List<ChatMessage> toolMessages = lastRoundToolMessages(messages);
        List<ChatMessage> assistantMessages = messages.stream()
                .filter(m -> "assistant".equals(m.role()))
                .toList();
        int round = assistantMessages.size();

        if (toolMessages.isEmpty()) {
            return firstRound(question, contentDelta);
        }
        if (round == 1) {
            LlmResponse refund = maybeRefund(question, toolMessages, contentDelta);
            if (refund != null) {
                return refund;
            }
        }
        // 最终回答要看「本轮用户提问以来的全部工具结果」，而不是只看最后一次调用，
        // 否则先查订单再退款时，最终答案会丢掉订单信息。
        return finalAnswer(question, toolMessagesSinceLastUser(messages), contentDelta);
    }

    private LlmResponse firstRound(String question, Consumer<String> delta) {
        String orderNo = find(ORDER_NO, question);
        String traceId = find(TRACE_ID, question);
        boolean needLogs = question.contains("日志") || question.contains("报错") || question.contains("超时")
                || question.contains("失败") || question.contains("error") || traceId != null;

        List<ChatMessage.ToolCall> calls = new ArrayList<>();
        if (orderNo != null) {
            calls.add(ChatMessage.ToolCall.of("call_order_1", "order_lookup",
                    json(Map.of("orderNo", orderNo))));
        }
        if (needLogs || orderNo == null) {
            Map<String, Object> args = new java.util.LinkedHashMap<>();
            if (traceId != null) {
                args.put("traceId", traceId);
            }
            args.put("limit", 8);
            String keyword = pickKeyword(question);
            if (keyword != null) {
                args.put("keyword", keyword);
            }
            calls.add(ChatMessage.ToolCall.of("call_log_1", "log_search", json(args)));
        }
        calls.add(ChatMessage.ToolCall.of("call_runbook_1", "runbook_search",
                json(Map.of("query", question, "topK", 2))));

        emit(delta, "我先把订单、日志和运行手册查一下，稍等。\n");
        return new LlmResponse("", calls, "tool_calls", 0, 0);
    }

    private LlmResponse maybeRefund(String question, List<ChatMessage> toolMessages, Consumer<String> delta) {
        if (!question.contains("退款") && !question.contains("refund")) {
            return null;
        }
        String orderNo = find(ORDER_NO, question);
        if (orderNo == null) {
            return null;
        }
        for (ChatMessage m : toolMessages) {
            if (!"order_lookup".equals(m.name())) {
                continue;
            }
            try {
                JsonNode node = mapper.readTree(m.content());
                String status = node.path("status").asText("");
                if (List.of("PAID", "SHIPPED", "COMPLETED").contains(status)) {
                    emit(delta, "订单状态 " + status + " 符合退款条件，我发起退款。\n");
                    return new LlmResponse("", List.of(ChatMessage.ToolCall.of(
                            "call_refund_1", "refund_order", json(Map.of("orderNo", orderNo)))),
                            "tool_calls", 0, 0);
                }
            } catch (Exception ignored) {
                // 工具结果不是合法 JSON 时直接下沉到最终回答，保证不崩
            }
        }
        return null;
    }

    private LlmResponse finalAnswer(String question, List<ChatMessage> toolMessages, Consumer<String> delta) {
        StringBuilder sb = new StringBuilder();
        JsonNode order = null;
        List<JsonNode> logs = new ArrayList<>();
        JsonNode refund = null;
        String runbook = null;

        for (ChatMessage m : toolMessages) {
            try {
                if ("order_lookup".equals(m.name())) {
                    JsonNode node = mapper.readTree(m.content());
                    if (!node.has("error")) {
                        order = node;
                    }
                } else if ("log_search".equals(m.name())) {
                    JsonNode node = mapper.readTree(m.content());
                    if (node.isArray()) {
                        node.forEach(logs::add);
                    }
                } else if ("refund_order".equals(m.name())) {
                    refund = mapper.readTree(m.content());
                } else if ("runbook_search".equals(m.name())) {
                    runbook = m.content();
                }
            } catch (Exception e) {
                sb.append("（工具 ").append(m.name()).append(" 返回内容无法解析，已忽略）\n");
            }
        }

        // 只调用了退款、没单独查订单时，退款结果里就带着订单快照
        if (order == null && refund != null) {
            order = refund;
        }

        sb.append("## 结论\n\n");
        if (order != null) {
            sb.append("订单 **").append(order.path("orderNo").asText())
                    .append("** 当前状态 `").append(order.path("status").asText()).append("`")
                    .append("，金额 ").append(order.path("amount").asText()).append(" 元")
                    .append("，渠道 ").append(order.path("channel").asText()).append("。\n\n");
        }
        if (refund != null) {
            sb.append("已经发起退款，订单状态变更为 `").append(refund.path("status").asText())
                    .append("`（REFUNDING 表示等待支付渠道回调）。\n\n");
        }
        if (order == null && refund == null) {
            sb.append("这次没能拿到订单数据（工具调用失败），下面的建议仅基于运行手册，请人工核实订单状态。\n\n");
        }
        long errors = logs.stream().filter(l -> "ERROR".equals(l.path("level").asText())).count();
        if (!logs.isEmpty()) {
            sb.append("## 日志线索\n\n共命中 ").append(logs.size()).append(" 条，其中 ERROR ").append(errors).append(" 条：\n\n");
            logs.stream().limit(5).forEach(l -> sb.append("- `")
                    .append(l.path("level").asText()).append("` ")
                    .append(l.path("service").asText()).append(" ")
                    .append(l.path("message").asText()).append("\n"));
            sb.append('\n');
        }
        if (runbook != null && !runbook.isBlank()) {
            sb.append("## 处置建议（来自运行手册 RAG）\n\n")
                    .append(runbook.lines().limit(8).reduce((a, b) -> a + "\n" + b).orElse(runbook))
                    .append("\n\n");
        }
        sb.append("## 下一步\n\n");
        if (refund != null) {
            sb.append("1. 订单已进入 `REFUNDING`，等待支付中心异步回调；\n")
                    .append("2. 30 分钟内未回调由对账任务补偿，**不要重复发起退款**；\n")
                    .append("3. 回调成功后订单会变成 `REFUNDED`，可让用户查看原支付渠道的到账通知。\n");
        } else if (order != null && "CREATED".equals(order.path("status").asText())) {
            sb.append("1. 订单未支付，先确认用户是否已完成付款；\n2. 若 30 分钟内未支付，跑批量关单任务；\n3. 需要退款时请先让订单进入 PAID 状态。\n");
        } else if (errors > 0) {
            sb.append("1. 按 TraceId 拿到下游调用链，重点看 stock-service 的超时；\n2. 确认库存服务 P99 与连接池配置；\n3. 必要时对 `lockStock` 做降级返回，避免订单大量失败。\n");
        } else {
            sb.append("当前没有发现异常，建议持续观察 5 分钟窗口内的错误率。\n");
        }

        String text = sb.toString();
        emit(delta, text);
        return new LlmResponse(text, List.of(), "stop", 0, 0);
    }

    private void emit(Consumer<String> delta, String text) {
        if (pauseMillis <= 0) {
            delta.accept(text);
            return;
        }
        for (int i = 0; i < text.length(); i += 6) {
            String piece = text.substring(i, Math.min(text.length(), i + 6));
            delta.accept(piece);
            try {
                Thread.sleep(pauseMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ObjectNode emptyObject() {
        return mapper.createObjectNode();
    }

    private static String lastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                return messages.get(i).content() == null ? "" : messages.get(i).content();
            }
        }
        return "";
    }

    /**
     * 取出「本轮」的工具结果：从最后一条 assistant 消息之后的所有 tool 消息。
     */
    private static List<ChatMessage> toolMessagesSinceLastUser(List<ChatMessage> messages) {
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                lastUser = i;
                break;
            }
        }
        List<ChatMessage> result = new ArrayList<>();
        for (int i = lastUser + 1; i < messages.size(); i++) {
            if ("tool".equals(messages.get(i).role())) {
                result.add(messages.get(i));
            }
        }
        return result;
    }

    private static List<ChatMessage> lastRoundToolMessages(List<ChatMessage> messages) {
        int lastAssistant = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("assistant".equals(messages.get(i).role())) {
                lastAssistant = i;
                break;
            }
        }
        List<ChatMessage> result = new ArrayList<>();
        for (int i = lastAssistant + 1; i < messages.size(); i++) {
            if ("tool".equals(messages.get(i).role())) {
                result.add(messages.get(i));
            }
        }
        return result;
    }

    private static String find(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group() : null;
    }

    private static String pickKeyword(String question) {
        String lower = question.toLowerCase(Locale.ROOT);
        for (String kw : List.of("timeout", "504", "refund", "insufficient", "failed")) {
            if (lower.contains(kw)) {
                return kw;
            }
        }
        return null;
    }
}
