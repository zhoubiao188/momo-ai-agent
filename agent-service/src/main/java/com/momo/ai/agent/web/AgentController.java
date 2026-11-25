package com.momo.ai.agent.web;

import com.momo.ai.agent.core.AgentEvent;
import com.momo.ai.agent.core.AgentLoop;
import com.momo.ai.agent.core.SessionStore;
import com.momo.ai.agent.config.AgentProperties;
import com.momo.ai.agent.rag.KnowledgeBase;
import com.momo.ai.agent.tool.ToolRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentLoop agentLoop;
    private final ToolRegistry toolRegistry;
    private final SessionStore sessionStore;
    private final KnowledgeBase knowledgeBase;
    private final AgentProperties props;
    private final ExecutorService workers = Executors.newFixedThreadPool(16, r -> {
        Thread t = new Thread(r, "agent-sse");
        t.setDaemon(true);
        return t;
    });

    public AgentController(AgentLoop agentLoop, ToolRegistry toolRegistry, SessionStore sessionStore,
                           KnowledgeBase knowledgeBase, AgentProperties props) {
        this.agentLoop = agentLoop;
        this.toolRegistry = toolRegistry;
        this.sessionStore = sessionStore;
        this.knowledgeBase = knowledgeBase;
        this.props = props;
    }

    public record ChatRequest(String sessionId, String question) {
    }

    /**
     * POST + SSE。之所以不用 EventSource，是因为它只支持 GET，
     * 而把问题放进 query string 会带来长度限制和日志泄露两个问题。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : request.sessionId();
        String question = request.question() == null ? "" : request.question();

        SseEmitter emitter = new SseEmitter(props.llm().timeout().toMillis() * 3);
        AtomicBoolean closed = new AtomicBoolean(false);

        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(e -> closed.set(true));

        workers.submit(() -> {
            try {
                agentLoop.run(sessionId, question, event -> send(emitter, closed, event));
                emitter.complete();
            } catch (Exception e) {
                log.error("sse push failed, session={}", sessionId, e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AtomicBoolean closed, AgentEvent event) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(event.type())
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端关掉页面 / 断网：停止推送，但让 Agent 继续跑完以便落库与审计
            closed.set(true);
        }
    }

    /** 同步接口，方便 curl 与自动化测试。 */
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString().substring(0, 8) : request.sessionId();
        List<AgentEvent> events = new ArrayList<>();
        agentLoop.run(sessionId, request.question() == null ? "" : request.question(), events::add);

        String answer = events.stream()
                .filter(e -> "final".equals(e.type()))
                .map(AgentEvent::content)
                .reduce((a, b) -> b)
                .orElse("");
        long toolCalls = events.stream().filter(e -> "tool_result".equals(e.type())).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("answer", answer);
        result.put("toolCalls", toolCalls);
        result.put("events", events.size());
        return result;
    }

    @GetMapping("/tools")
    public Map<String, Object> tools() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", toolRegistry.names());
        result.put("mutatingAllowed", props.tools().allowMutating());
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("llmMode", props.llm().remoteEnabled() ? "remote" : "mock");
        result.put("model", agentLoop.getClass().getSimpleName().isEmpty() ? "" : props.llm().model());
        result.put("sessions", sessionStore.size());
        result.put("knowledgeChunks", knowledgeBase.chunkCount());
        return result;
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> clear(@PathVariable String sessionId) {
        sessionStore.remove(sessionId);
        return Map.of("removed", sessionId);
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }
}
