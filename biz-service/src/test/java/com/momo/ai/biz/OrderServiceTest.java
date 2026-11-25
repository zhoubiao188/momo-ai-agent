package com.momo.ai.biz;

import com.momo.ai.biz.domain.OrderRepository;
import com.momo.ai.biz.service.OrderService;
import com.momo.ai.common.BizException;
import com.momo.ai.common.OrderDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 每个用例一个事务并回滚，避免「退款用例改了共享数据，导致后面的查询用例失败」这类顺序耦合。
 */
@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository repository;

    @Test
    void shouldReturnSeededOrder() {
        OrderDto dto = orderService.get("MO20260101001");
        assertThat(dto.status()).isEqualTo("PAID");
        assertThat(dto.amount()).isEqualByComparingTo("199.00");
    }

    @Test
    void refundShouldMoveToRefunding() {
        OrderDto dto = orderService.refund("MO20260101001");
        assertThat(dto.status()).isEqualTo("REFUNDING");
    }

    @Test
    void refundShouldRejectCreatedOrder() {
        assertThatThrownBy(() -> orderService.refund("MO20260101004"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not refundable");
    }

    @Test
    void unknownOrderShouldThrow() {
        assertThatThrownBy(() -> orderService.get("NOT-EXIST")).isInstanceOf(BizException.class);
        assertThat(repository.count()).isEqualTo(6);
    }
}
