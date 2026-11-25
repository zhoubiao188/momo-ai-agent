package com.momo.ai.common;

/**
 * 统一返回结构。Agent 工具在解析业务服务返回时会依赖这个契约，
 * 所以它被放在 common 模块里，由 biz-service 与 agent-service 共同引用。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public boolean success() {
        return code == 0;
    }
}
