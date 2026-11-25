package com.momo.ai.agent.tool.biz;

import com.fasterxml.jackson.databind.JsonNode;
import com.momo.ai.agent.tool.AgentTool;
import com.momo.ai.agent.tool.ToolContext;
import com.momo.ai.agent.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class LogSearchTool implements AgentTool {

    private final BizClient bizClient;

    @Override
    public String name() {
        return "log_search";
    }

    @Override
    public String description() {
        return "按 TraceId / 服务名 / 级别 / 关键字检索最近 6 小时的日志，返回最多 20 条。排查报错、超时、支付回调失败时使用。";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "traceId": {"type": "string", "description": "链路 ID，形如 tr-9f2a41"},
                    "service": {"type": "string", "description": "服务名，例如 payment-service"},
                    "level": {"type": "string", "enum": ["INFO", "WARN", "ERROR"], "description": "日志级别"},
                    "keyword": {"type": "string", "description": "消息关键字，例如 timeout、504"},
                    "limit": {"type": "integer", "minimum": 1, "maximum": 20, "default": 8}
                  },
                  "additionalProperties": false
                }
                """;
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode args) {
        long start = System.currentTimeMillis();
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/logs/search");
        copyString(args, builder, "traceId");
        copyString(args, builder, "service");
        copyString(args, builder, "level");
        copyString(args, builder, "keyword");
        int limit = Math.min(Math.max(args.path("limit").asInt(8), 1), 20);
        builder.queryParam("limit", limit);

        JsonNode data = bizClient.get(builder.build().toUriString());
        if (data.has("error")) {
            return ToolResult.fail(data.path("error").asText(), System.currentTimeMillis() - start);
        }
        return ToolResult.ok(data.toString(), System.currentTimeMillis() - start);
    }

    private static void copyString(JsonNode args, UriComponentsBuilder builder, String field) {
        String value = args.path(field).asText("");
        if (!value.isBlank()) {
            builder.queryParam(field, value);
        }
    }
}
