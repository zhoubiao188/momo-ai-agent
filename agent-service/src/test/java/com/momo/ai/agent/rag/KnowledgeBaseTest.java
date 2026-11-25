package com.momo.ai.agent.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBaseTest {

    private final KnowledgeBase kb = KnowledgeBase.forTest(Map.of(
            "order.md", """
                    # 订单手册
                    ## 订单状态机
                    订单状态从 CREATED 流转到 PAID，再到 SHIPPED。CREATED 状态不能直接退款。
                    ## 退款流程
                    退款前必须校验订单状态属于 PAID、SHIPPED 或 COMPLETED，然后调用支付中心 refund 接口。
                    """,
            "stock.md", """
                    # 库存手册
                    ## 库存锁定失败
                    库存锁定失败时先对比 Redis 与数据库库存，缓存不一致执行 syncCache 运维接口。
                    """
    ));

    @Test
    void shouldSplitBySection() {
        assertThat(kb.chunkCount()).isEqualTo(3);
    }

    /**
     * 检索的目标是「召回」，不是「Top1 精确命中」：
     * 工具会带 topK=2 一起给模型，只要正确章节在候选里，模型自己会挑。
     * 硬要求 Top1 反而会把测试变成对 BM25 参数的过拟合。
     */
    @Test
    void shouldRecallRefundSection() {
        List<KnowledgeBase.Hit> hits = kb.search("订单退款需要什么条件", 2);
        assertThat(hits).isNotEmpty();
        assertThat(hits).extracting(KnowledgeBase.Hit::section).contains("退款流程");
    }

    @Test
    void shouldHitStockSection() {
        List<KnowledgeBase.Hit> hits = kb.search("库存锁定失败怎么排查", 1);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).doc()).isEqualTo("stock.md");
    }

    @Test
    void shouldReturnEmptyForUnrelatedQuery() {
        assertThat(kb.search("今天天气怎么样", 3)).isEmpty();
    }

    @Test
    void tokenizeShouldProduceCjkBigrams() {
        List<String> tokens = KnowledgeBase.tokenize("订单退款");
        assertThat(tokens).contains("订", "订单", "退", "退款");
    }
}
