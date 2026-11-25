package com.momo.ai.agent.llm;

import java.util.List;

public record LlmResponse(
        String content,
        List<ChatMessage.ToolCall> toolCalls,
        String finishReason,
        int promptTokens,
        int completionTokens
) {

    public static LlmResponse text(String content, String finishReason) {
        return new LlmResponse(content, List.of(), finishReason, 0, 0);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
