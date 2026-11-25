package com.momo.ai.agent.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * OpenAI / DeepSeek / Qwen 通用的消息结构。
 * tool_calls 只在 assistant 消息上出现，tool_call_id 只在 tool 消息上出现。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String role,
        String content,
        String toolCallId,
        String name,
        List<ToolCall> toolCalls
) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, null, null,
                toolCalls == null || toolCalls.isEmpty() ? null : toolCalls);
    }

    public static ChatMessage tool(String toolCallId, String name, String content) {
        return new ChatMessage("tool", content, toolCallId, name, null);
    }

    /** 送给模型时只需 name/arguments，id 由 tool_calls 容器携带。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolCall(String id, String type, Function function) {

        public static ToolCall of(String id, String name, String arguments) {
            return new ToolCall(id, "function", new Function(name, arguments));
        }

        public String name() {
            return function == null ? null : function.name();
        }

        public String arguments() {
            return function == null ? null : function.arguments();
        }
    }

    public record Function(String name, String arguments) {
    }

    /** 供 Jackson 反序列化 tool_calls（id 可能缺失，type 可能为 null）。 */
    public static ToolCall raw(String id, String type, Map<String, Object> function) {
        Object n = function == null ? null : function.get("name");
        Object a = function == null ? null : function.get("arguments");
        return new ToolCall(id, type == null ? "function" : type,
                new Function(n == null ? null : String.valueOf(n), a == null ? null : String.valueOf(a)));
    }
}
