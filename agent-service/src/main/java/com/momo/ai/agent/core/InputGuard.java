package com.momo.ai.agent.core;

import com.momo.ai.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 输入护栏。不是「一劳永逸的安全方案」，而是明确几件必须做的事：
 * 1) 限制输入长度，防止把上下文窗口打爆；
 * 2) 拦截明显的指令覆盖型注入，降低「读日志时把日志当指令执行」的风险；
 * 3) 统一去掉控制字符，避免污染 SSE 与日志。
 */
@Slf4j
@Component
public class InputGuard {

    private static final List<String> SUSPICIOUS = List.of(
            "ignore previous", "ignore all previous", "disregard previous",
            "忽略上面", "忽略以上", "忽略之前", "无视上述",
            "system prompt", "打印你的提示词", "输出你的系统提示"
    );

    private final AgentProperties props;

    public InputGuard(AgentProperties props) {
        this.props = props;
    }

    public Optional<String> check(String question) {
        if (question == null || question.isBlank()) {
            return Optional.of("问题不能为空");
        }
        String cleaned = sanitize(question);
        if (cleaned.length() > props.guard().maxQuestionChars()) {
            return Optional.of("问题过长，最多 " + props.guard().maxQuestionChars() + " 字");
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        for (String pattern : SUSPICIOUS) {
            if (lower.contains(pattern)) {
                log.warn("prompt injection attempt blocked, session question prefix={}",
                        cleaned.substring(0, Math.min(40, cleaned.length())));
                return Optional.of("检测到疑似提示词注入的内容，已拦截。请直接描述业务问题。");
            }
        }
        return Optional.empty();
    }

    public static String sanitize(String input) {
        return input.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    }
}
