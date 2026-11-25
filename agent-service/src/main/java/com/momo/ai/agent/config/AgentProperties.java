package com.momo.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Agent 的全部可调参数集中在这里，避免魔法数字散落在业务代码中。
 * 通过 application.yml 的 agent.* 前缀覆盖。
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        Llm llm,
        Tools tools,
        Session session,
        Guard guard
) {

    public record Llm(
            String mode,
            String baseUrl,
            String apiKey,
            String model,
            double temperature,
            Duration timeout,
            int maxSteps,
            int maxHistoryMessages
    ) {
        public boolean remoteEnabled() {
            return "openai".equalsIgnoreCase(mode) && apiKey != null && !apiKey.isBlank();
        }
    }

    public record Tools(String bizBaseUrl, Duration timeout, boolean allowMutating) {
    }

    public record Session(Duration ttl, int maxSessions) {
    }

    public record Guard(int maxQuestionChars, int maxToolArgumentsChars, int maxToolCallsPerStep) {
    }
}
