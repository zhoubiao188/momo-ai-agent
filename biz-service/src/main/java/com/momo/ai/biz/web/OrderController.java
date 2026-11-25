package com.momo.ai.biz.web;

import com.momo.ai.biz.service.OrderService;
import com.momo.ai.common.ApiResponse;
import com.momo.ai.common.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDto> get(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.get(orderNo));
    }

    @GetMapping
    public ApiResponse<List<OrderDto>> listByUser(@RequestParam String userId) {
        return ApiResponse.ok(orderService.listByUser(userId));
    }

    @PostMapping("/{orderNo}/refund")
    public ApiResponse<OrderDto> refund(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.refund(orderNo));
    }
}
