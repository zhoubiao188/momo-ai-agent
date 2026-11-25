package com.momo.ai.agent.tool.biz;

import com.fasterxml.jackson.databind.JsonNode;
import com.momo.ai.agent.tool.AgentTool;
import com.momo.ai.agent.tool.ToolContext;
import com.momo.ai.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 唯一带副作用的工具。
 * 生产环境建议再加一层「人审」：模型只生成待办，由人点击确认后才真正执行。
 * 这里用 agent.tools.allow-mutating 开关控制是否把该工具暴露给模型。
 */
@Component
@RequiredArgsConstructor
public class RefundOrderTool implements AgentTool {

    private final BizClient bizClient;

    @Override
    public String name() {
        return "refund_order";
    }

    @Override
    public String description() {
        return "对订单发起退款（状态会变为 REFUNDING）。仅在用户明确要求退款、且订单状态为 PAID/SHIPPED/COMPLETED 时调用。";
    }

    @Override
    public boolean mutating() {
        return true;
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "orderNo": {"type": "string", "description": "订单号"}
                  },
                  "required": ["orderNo"],
                  "additionalProperties": false
                }
                """;
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode args) {
        long start = System.currentTimeMillis();
        String orderNo = args.path("orderNo").asText("");
        if (orderNo.isBlank()) {
            return ToolResult.fail("orderNo is required", 0);
        }
        JsonNode data = bizClient.post("/api/orders/" + orderNo + "/refund");
        if (data.has("error")) {
            return ToolResult.fail(data.path("error").asText(), System.currentTimeMillis() - start);
        }
        return ToolResult.ok(data.toString(), System.currentTimeMillis() - start);
    }
}
