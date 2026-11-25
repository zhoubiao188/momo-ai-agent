package com.momo.ai.agent.core;

/**
 * 一条 SSE 事件。前端只依赖 type + 少量字段，新增字段向后兼容。
 */
public record AgentEvent(
        String type,
        String sessionId,
        Integer step,
        String content,
        String toolCallId,
        String toolName,
        String arguments,
        Boolean success,
        Long elapsedMs,
        String error
) {

    public static AgentEvent start(String sessionId) {
        return new AgentEvent("start", sessionId, null, null, null, null, null, null, null, null);
    }

    public static AgentEvent step(String sessionId, int step) {
        return new AgentEvent("step", sessionId, step, null, null, null, null, null, null, null);
    }

    public static AgentEvent delta(String sessionId, int step, String text) {
        return new AgentEvent("delta", sessionId, step, text, null, null, null, null, null, null);
    }

    public static AgentEvent toolCall(String sessionId, int step, String callId, String name, String args) {
        return new AgentEvent("tool_call", sessionId, step, null, callId, name, args, null, null, null);
    }

    public static AgentEvent toolResult(String sessionId, int step, String callId, String name,
                                        String content, boolean success, long elapsedMs) {
        return new AgentEvent("tool_result", sessionId, step, content, callId, name, null, success, elapsedMs, null);
    }

    public static AgentEvent finalAnswer(String sessionId, String text) {
        return new AgentEvent("final", sessionId, null, text, null, null, null, null, null, null);
    }

    public static AgentEvent error(String sessionId, String message) {
        return new AgentEvent("error", sessionId, null, null, null, null, null, null, null, message);
    }

    public static AgentEvent done(String sessionId) {
        return new AgentEvent("done", sessionId, null, null, null, null, null, null, null, null);
    }
}
