package com.momo.ai.biz.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    List<OrderEntity> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
