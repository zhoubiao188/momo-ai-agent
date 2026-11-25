package com.momo.ai.common;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderDto(
        String orderNo,
        String userId,
        String status,
        BigDecimal amount,
        String channel,
        String traceId,
        LocalDateTime createdAt,
        String remark
) {
}
