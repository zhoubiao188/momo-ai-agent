package com.momo.ai.agent.tool.biz;

import com.fasterxml.jackson.databind.JsonNode;
import com.momo.ai.agent.tool.AgentTool;
import com.momo.ai.agent.tool.ToolContext;
import com.momo.ai.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLookupTool implements AgentTool {

    private final BizClient bizClient;

    @Override
    public String name() {
        return "order_lookup";
    }

    @Override
    public String description() {
        return "根据订单号查询订单详情（状态、金额、支付渠道、TraceId）。当用户提到订单问题、支付问题、退款前确认时调用。";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "orderNo": {
                      "type": "string",
                      "description": "订单号，格式形如 MO20260101001"
                    }
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
        JsonNode data = bizClient.get("/api/orders/" + orderNo);
        if (data.has("error")) {
            return ToolResult.fail(data.path("error").asText(), System.currentTimeMillis() - start);
        }
        return ToolResult.ok(data.toString(), System.currentTimeMillis() - start);
    }
}
