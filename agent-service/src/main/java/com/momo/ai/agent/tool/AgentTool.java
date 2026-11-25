package com.momo.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一个工具 = 一段可被模型调用的确定性代码。
 * 设计约束：
 * - parametersJsonSchema 必须是严格 JSON Schema，模型靠它决定怎么填参数；
 * - execute 不允许抛受检异常，内部错误一律转成 success=false 的结构化结果，
 *   因为「工具失败」本身就是 Agent 需要看到的信息，而不是要打断整个会话的异常。
 */
public interface AgentTool {

    String name();

    String description();

    String parametersJsonSchema();

    default boolean mutating() {
        return false;
    }

    ToolResult execute(ToolContext context, JsonNode arguments);
}
