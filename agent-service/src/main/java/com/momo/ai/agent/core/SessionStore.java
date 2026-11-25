package com.momo.ai.agent.core;

import com.momo.ai.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SessionStore {

    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final AgentProperties props;

    public SessionStore(AgentProperties props) {
        this.props = props;
    }

    public AgentSession getOrCreate(String sessionId) {
        if (sessions.size() > props.session().maxSessions()) {
            evictExpired();
        }
        return sessions.computeIfAbsent(sessionId,
                id -> new AgentSession(id, props.llm().maxHistoryMessages()));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public int size() {
        return sessions.size();
    }

    @Scheduled(fixedDelay = 60_000)
    void evictExpired() {
        long deadline = System.currentTimeMillis() - props.session().ttl().toMillis();
        sessions.entrySet().removeIf(e -> e.getValue().lastAccessAt() < deadline);
    }
}
