package com.momo.ai.agent.tool.biz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momo.ai.agent.config.AgentProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 访问业务微服务的最小客户端。
 * 注意：Agent 绝不会直接连数据库——所有副作用都必须经过业务服务的 API，
 * 这样鉴权、审计、幂等、限流这些能力才能复用，而不是在 Agent 里重写一遍。
 */
@Component
public class BizClient {

    private final RestClient client;
    private final ObjectMapper mapper;

    public BizClient(AgentProperties props, ObjectMapper mapper) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(props.tools().timeout())
                .withReadTimeout(props.tools().timeout());
        this.client = RestClient.builder()
                .baseUrl(props.tools().bizBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
        this.mapper = mapper;
    }

    public JsonNode get(String path) {
        String body = client.get().uri(path).retrieve().body(String.class);
        return unwrap(body);
    }

    public JsonNode post(String path) {
        String body = client.post().uri(path).retrieve().body(String.class);
        return unwrap(body);
    }

    /**
     * 业务服务统一返回 {code, message, data}，这里只把 data 交给模型，
     * 减少 token 浪费，也避免模型被 code 字段干扰。
     */
    private JsonNode unwrap(String body) {
        try {
            JsonNode root = mapper.readTree(body == null ? "{}" : body);
            if (root.has("code") && root.path("code").asInt() != 0) {
                return mapper.createObjectNode()
                        .put("error", root.path("message").asText("unknown biz error"))
                        .put("code", root.path("code").asInt());
            }
            return root.has("data") ? root.get("data") : root;
        } catch (Exception e) {
            return mapper.createObjectNode().put("error", "invalid biz response");
        }
    }
}
