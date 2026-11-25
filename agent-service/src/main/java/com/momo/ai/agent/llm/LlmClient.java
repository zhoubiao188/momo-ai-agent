package com.momo.ai.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 与具体厂商解耦的 LLM 抽象。
 * 只暴露一个「阻塞式流式调用」：内容增量通过回调吐给 SSE，最终结果一次性返回，
 * 这样上层 AgentLoop 的编排逻辑可以是完全同步的，便于阅读与测试。
 */
public interface LlmClient {

    String modelName();

    LlmResponse stream(List<ChatMessage> messages,
                       JsonNode toolsSchema,
                       Consumer<String> contentDelta);
}
