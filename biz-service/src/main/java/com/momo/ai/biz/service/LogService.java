package com.momo.ai.biz.service;

import com.momo.ai.common.LogEntryDto;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 演示用的日志中心：真实项目里这里应该代理 Loki / ES。
 * 为了让 Agent 的 log_search 工具有可用的数据，启动时生成一批与订单 TraceId 关联的日志。
 */
@Service
public class LogService {

    private static final String[] LEVELS = {"INFO", "INFO", "INFO", "WARN", "ERROR"};
    private static final String[] SERVICES = {"order-service", "payment-service", "stock-service", "gateway"};

    private final List<LogEntryDto> store = new CopyOnWriteArrayList<>();

    public LogService() {
        seed();
    }

    public List<LogEntryDto> search(String traceId, String service, String level, String keyword,
                                    LocalDateTime from, LocalDateTime to, int limit) {
        String kw = keyword == null ? null : keyword.toLowerCase(Locale.ROOT);
        List<LogEntryDto> result = new ArrayList<>();
        for (LogEntryDto e : store) {
            if (traceId != null && !traceId.isBlank() && !traceId.equals(e.traceId())) {
                continue;
            }
            if (service != null && !service.isBlank() && !service.equalsIgnoreCase(e.service())) {
                continue;
            }
            if (level != null && !level.isBlank() && !level.equalsIgnoreCase(e.level())) {
                continue;
            }
            if (kw != null && !kw.isBlank() && !e.message().toLowerCase(Locale.ROOT).contains(kw)) {
                continue;
            }
            if (from != null && e.time().isBefore(from)) {
                continue;
            }
            if (to != null && e.time().isAfter(to)) {
                continue;
            }
            result.add(e);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    public void append(LogEntryDto entry) {
        store.add(entry);
    }

    private void seed() {
        Random random = new Random(20260101L);
        LocalDateTime base = LocalDateTime.now().minusHours(6);
        String[] traceIds = {"tr-9f2a41", "tr-9f2a42", "tr-7c1b08"};
        String[] messages = {
                "order created, amount=199.00",
                "payment callback received, channel=alipay",
                "payment timeout after 3000ms, retrying",
                "stock locked failed: sku=SKU-77 insufficient",
                "order status changed PAID -> SHIPPED",
                "downstream call /stock/lock returns 504",
                "refund request accepted, waiting for approval"
        };
        for (int i = 0; i < 240; i++) {
            String traceId = traceIds[random.nextInt(traceIds.length)];
            String level = LEVELS[random.nextInt(LEVELS.length)];
            String service = SERVICES[random.nextInt(SERVICES.length)];
            String message = messages[random.nextInt(messages.length)];
            store.add(new LogEntryDto(base.plusMinutes(i * 90L / 60), service, level, traceId, message));
        }
    }
}
