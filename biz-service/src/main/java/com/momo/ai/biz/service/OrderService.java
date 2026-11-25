package com.momo.ai.biz.service;

import com.momo.ai.common.BizException;
import com.momo.ai.common.OrderDto;
import com.momo.ai.biz.domain.OrderEntity;
import com.momo.ai.biz.domain.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** 允许的退款前置状态，硬编码在这里是为了让 Agent 的 runbook 有明确依据。 */
    private static final Set<String> REFUNDABLE = Set.of("PAID", "SHIPPED", "COMPLETED");

    private final OrderRepository repository;

    public OrderDto get(String orderNo) {
        return repository.findById(orderNo)
                .map(OrderService::toDto)
                .orElseThrow(() -> new BizException(4040, "order not found: " + orderNo));
    }

    public List<OrderDto> listByUser(String userId) {
        return repository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OrderService::toDto)
                .toList();
    }

    @Transactional
    public OrderDto refund(String orderNo) {
        OrderEntity order = repository.findById(orderNo)
                .orElseThrow(() -> new BizException(4040, "order not found: " + orderNo));
        if (!REFUNDABLE.contains(order.getStatus())) {
            throw new BizException(4090, "order status " + order.getStatus() + " is not refundable");
        }
        order.setStatus("REFUNDING");
        order.setUpdatedAt(LocalDateTime.now());
        order.setRemark("refund requested by agent");
        repository.save(order);
        return toDto(order);
    }

    private static OrderDto toDto(OrderEntity e) {
        return new OrderDto(e.getOrderNo(), e.getUserId(), e.getStatus(), e.getAmount(),
                e.getChannel(), e.getTraceId(), e.getCreatedAt(), e.getRemark());
    }
}
