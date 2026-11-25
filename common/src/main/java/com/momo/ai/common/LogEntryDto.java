package com.momo.ai.common;

import java.time.LocalDateTime;

public record LogEntryDto(
        LocalDateTime time,
        String service,
        String level,
        String traceId,
        String message
) {
}
