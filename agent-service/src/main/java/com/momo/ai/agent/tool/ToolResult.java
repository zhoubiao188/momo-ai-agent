package com.momo.ai.agent.tool;

public record ToolResult(String content, boolean success, long elapsedMs) {

    public static ToolResult ok(String content, long elapsedMs) {
        return new ToolResult(content, true, elapsedMs);
    }

    public static ToolResult fail(String message, long elapsedMs) {
        return new ToolResult("{\"error\":\"" + escape(message) + "\"}", false, elapsedMs);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
