package com.momo.ai.biz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "t_order")
@Getter
@Setter
public class OrderEntity {

    @Id
    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Column(name = "user_id", length = 32, nullable = false)
    private String userId;

    /** CREATED / PAID / SHIPPED / COMPLETED / REFUNDING / REFUNDED / CLOSED */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "channel", length = 16)
    private String channel;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "remark", length = 255)
    private String remark;
}
