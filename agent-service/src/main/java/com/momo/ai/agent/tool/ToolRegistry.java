package com.momo.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具注册表：负责
 * 1) 启动时校验工具名唯一、Schema 可解析（早失败优于线上 400）；
 * 2) 生成 OpenAI tools 数组；
 * 3) 带超时地执行工具，把异常收敛成结构化失败结果。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();
    private final ObjectMapper mapper;
    private final ExecutorService pool = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "tool-exec");
        t.setDaemon(true);
        return t;
    });

    public ToolRegistry(List<AgentTool> discovered, ObjectMapper mapper) {
        this.mapper = mapper;
        for (AgentTool tool : discovered) {
            AgentTool prev = tools.putIfAbsent(tool.name(), tool);
            if (prev != null) {
                throw new IllegalStateException("duplicated tool name: " + tool.name());
            }
            try {
                mapper.readTree(tool.parametersJsonSchema());
            } catch (Exception e) {
                throw new IllegalStateException("tool " + tool.name() + " has invalid JSON schema", e);
            }
        }
        log.info("agent tools registered: {}", tools.keySet());
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    public JsonNode toolsSchema(boolean allowMutating) {
        ArrayNode array = mapper.createArrayNode();
        for (AgentTool tool : tools.values()) {
            if (tool.mutating() && !allowMutating) {
                continue;
            }
            ObjectNode item = mapper.createObjectNode();
            item.put("type", "function");
            ObjectNode fn = item.putObject("function");
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            try {
                fn.set("parameters", mapper.readTree(tool.parametersJsonSchema()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            array.add(item);
        }
        return array;
    }

    public ToolResult execute(AgentTool tool, ToolContext ctx, JsonNode args, long timeoutMillis) {
        long start = System.currentTimeMillis();
        Future<ToolResult> future = pool.submit(() -> tool.execute(ctx, args));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ToolResult.fail("tool timed out after " + timeoutMillis + "ms", System.currentTimeMillis() - start);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("tool {} failed: {}", tool.name(), cause.toString());
            return ToolResult.fail(cause.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
