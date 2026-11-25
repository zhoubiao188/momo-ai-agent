package com.momo.ai.agent.llm;

import com.momo.ai.agent.config.AgentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LlmConfig {

    /**
     * 有 Key 用真模型，没 Key 用剧本模型。两种实现共用同一套 AgentLoop，
     * 上层代码完全不知道自己面对的是 DeepSeek 还是本地 mock。
     */
    @Bean
    @Primary
    public LlmClient llmClient(AgentProperties props,
                               OpenAiCompatibleClient remote,
                               MockLlmClient mock) {
        return props.llm().remoteEnabled() ? remote : mock;
    }
}
