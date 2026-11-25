package com.momo.ai.agent.tool.biz;

import com.fasterxml.jackson.databind.JsonNode;
import com.momo.ai.agent.tool.AgentTool;
import com.momo.ai.agent.tool.ToolContext;
import com.momo.ai.agent.tool.ToolResult;
import com.momo.ai.agent.rag.KnowledgeBase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RunbookSearchTool implements AgentTool {

    private final KnowledgeBase knowledgeBase;

    @Override
    public String name() {
        return "runbook_search";
    }

    @Override
    public String description() {
        return "在团队运行手册（订单、支付、库存、值班流程）中检索处置步骤。给出结论前应先查手册，避免给出与团队规范冲突的建议。";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {"type": "string", "description": "自然语言问题或关键字"},
                    "topK": {"type": "integer", "minimum": 1, "maximum": 5, "default": 2}
                  },
                  "required": ["query"],
                  "additionalProperties": false
                }
                """;
    }

    @Override
    public ToolResult execute(ToolContext context, JsonNode args) {
        long start = System.currentTimeMillis();
        String query = args.path("query").asText("");
        if (query.isBlank()) {
            return ToolResult.fail("query is required", 0);
        }
        List<KnowledgeBase.Hit> hits = knowledgeBase.search(query, args.path("topK").asInt(2));
        if (hits.isEmpty()) {
            return ToolResult.ok("未在运行手册中找到相关内容。", System.currentTimeMillis() - start);
        }
        StringBuilder sb = new StringBuilder();
        for (KnowledgeBase.Hit hit : hits) {
            sb.append("【").append(hit.doc()).append(" / ").append(hit.section()).append("】\n")
                    .append(summarize(hit.content()))
                    .append("\n\n");
        }
        return ToolResult.ok(sb.toString().trim(), System.currentTimeMillis() - start);
    }

    /** 控制返回长度：手册片段太长会把上下文挤爆，只取前 600 字。 */
    private static String summarize(String content) {
        String text = content.replaceAll("(?m)^#+\\s+", "").replaceAll("\\n{2,}", "\n").trim();
        return text.length() <= 600 ? text : text.substring(0, 600) + "…";
    }
}
