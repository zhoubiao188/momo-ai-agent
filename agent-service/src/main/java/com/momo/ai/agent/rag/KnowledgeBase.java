package com.momo.ai.agent.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 轻量 RAG：不引向量库，用 BM25 + 中文 bigram 分词。
 *
 * 为什么不用 embedding？
 * - 运行手册这类语料只有几十 KB，BM25 的召回已经足够；
 * - 免去额外模型调用与向量库运维，clone 下来零依赖可跑。
 * 需要语义召回时，只要把 search() 换成向量检索，接口不变。
 */
@Slf4j
@Component
public class KnowledgeBase {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    public record Hit(String doc, String section, String content, double score) {
    }

    private record Chunk(String doc, String section, String content, Map<String, Integer> tf, int length) {
    }

    private final List<Chunk> chunks = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();
    private double avgLength = 1.0;

    @PostConstruct
    void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:knowledge/*.md");
            for (Resource resource : resources) {
                String text = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                index(resource.getFilename(), text);
            }
        } catch (Exception e) {
            log.warn("knowledge base not loaded: {}", e.getMessage());
        }
        if (!chunks.isEmpty()) {
            avgLength = chunks.stream().mapToInt(Chunk::length).average().orElse(1);
        }
        log.info("knowledge base loaded: {} docs, {} chunks", df.size() > 0 ? "n" : "0", chunks.size());
    }

    /** 按二级标题切片：每个 ## 小节就是一个独立的检索单元。 */
    void index(String doc, String markdown) {
        String normalized = markdown.replace("\r\n", "\n");
        String[] parts = normalized.split("(?m)^##\\s+");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            String section = i == 0 ? "概述" : part.lines().findFirst().orElse("概述").trim();
            String body = i == 0 ? part : part.substring(part.indexOf('\n') + 1);
            // 文档开头通常只有一行 H1 标题，没有正文就不要建索引，否则会产生大量低信息量切片
            if (i == 0 && body.replaceAll("(?m)^#+.*$", "").isBlank()) {
                continue;
            }
            Map<String, Integer> tf = new HashMap<>();
            int len = 0;
            // 标题命中比正文命中更能代表主题，这里给标题词 3 倍权重（字段加权的最简实现）
            List<String> tokens = new ArrayList<>(tokenize(body));
            for (int repeat = 0; repeat < 3; repeat++) {
                tokens.addAll(tokenize(section));
            }
            for (String token : tokens) {
                tf.merge(token, 1, Integer::sum);
                len++;
            }
            if (len == 0) {
                continue;
            }
            chunks.add(new Chunk(doc, section, body.trim(), tf, len));
            tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
        }
    }

    public List<Hit> search(String query, int topK) {
        List<String> qTokens = tokenize(query);
        if (qTokens.isEmpty() || chunks.isEmpty()) {
            return List.of();
        }
        int n = chunks.size();
        List<Hit> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            double score = 0;
            for (String token : qTokens) {
                Integer f = chunk.tf().get(token);
                if (f == null) {
                    continue;
                }
                int d = df.getOrDefault(token, 1);
                double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
                double denom = f + K1 * (1 - B + B * chunk.length() / avgLength);
                score += idf * f * (K1 + 1) / denom;
            }
            if (score > 0) {
                scored.add(new Hit(chunk.doc(), chunk.section(), chunk.content(), round(score)));
            }
        }
        return scored.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(Math.max(1, topK))
                .toList();
    }

    public int chunkCount() {
        return chunks.size();
    }

    /**
     * 中文没有空格，直接按空白切词会让 BM25 完全失效。
     * 这里做最简处理：英文/数字按词，中文按「单字 + 相邻 bigram」切分。
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        String s = text.toLowerCase(Locale.ROOT);
        StringBuilder word = new StringBuilder();
        StringBuilder cjk = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCjk(c)) {
                flush(word, tokens);
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk(cjk, tokens);
                word.append(c);
            } else {
                flush(word, tokens);
                flushCjk(cjk, tokens);
            }
        }
        flush(word, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    private static void flush(StringBuilder sb, List<String> tokens) {
        if (sb.length() > 0) {
            tokens.add(sb.toString());
            sb.setLength(0);
        }
    }

    private static void flushCjk(StringBuilder sb, List<String> tokens) {
        if (sb.length() == 0) {
            return;
        }
        String s = sb.toString();
        for (int i = 0; i < s.length(); i++) {
            tokens.add(String.valueOf(s.charAt(i)));
            if (i < s.length() - 1) {
                tokens.add(s.substring(i, i + 2));
            }
        }
        sb.setLength(0);
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /** 单测用：避免依赖 Spring 容器。 */
    public static KnowledgeBase forTest(Map<String, String> docs) {
        KnowledgeBase kb = new KnowledgeBase();
        docs.forEach(kb::index);
        kb.avgLength = kb.chunks.stream().mapToInt(Chunk::length).average().orElse(1);
        return kb;
    }

    public Map<String, String> debugSections() {
        Map<String, String> map = new LinkedHashMap<>();
        chunks.forEach(c -> map.put(c.doc() + "#" + c.section(), c.content()));
        return map;
    }
}
