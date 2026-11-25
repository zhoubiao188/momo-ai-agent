package com.momo.ai.agent.core;

import com.momo.ai.agent.llm.ChatMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 会话上下文。刻意做得很「笨」：只存消息列表 + 最后访问时间，
 * 复杂的会话存储（Redis / 数据库）在生产里替换掉这个类即可，接口不变。
 */
public class AgentSession {

    private final String id;
    private final Deque<ChatMessage> history = new ArrayDeque<>();
    private final int maxMessages;
    private volatile long lastAccessAt = System.currentTimeMillis();

    public AgentSession(String id, int maxMessages) {
        this.id = id;
        this.maxMessages = maxMessages;
    }

    public String id() {
        return id;
    }

    public long lastAccessAt() {
        return lastAccessAt;
    }

    public synchronized void touch() {
        this.lastAccessAt = System.currentTimeMillis();
    }

    public synchronized void append(ChatMessage... messages) {
        for (ChatMessage message : messages) {
            history.addLast(message);
        }
        while (history.size() > maxMessages) {
            history.removeFirst();
        }
        touch();
    }

    public synchronized List<ChatMessage> snapshot() {
        touch();
        return new ArrayList<>(history);
    }

    public synchronized void clear() {
        history.clear();
        touch();
    }
}
