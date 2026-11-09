package com.smallbiz.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;

import com.smallbiz.catalog.Product;
import com.smallbiz.catalog.ProductRepository;
import com.smallbiz.common.ApiException;
import com.smallbiz.support.IntegrationTest;
import com.smallbiz.tenant.TenantContext;

/** 并发扣减库存：不允许超卖，且库存始终等于流水之和。 */
class InventoryConcurrencyTest extends IntegrationTest {

    @Autowired
    InventoryService inventory;

    @Autowired
    ProductRepository products;

    @Autowired
    StockMovementRepository movements;

    @Autowired
    TransactionTemplate tx;

    /**
     * 多家店同时新建带初始库存的商品：新商品的仓库库存行都落在唯一索引的同一个间隙里，
     * 以前先对不存在的行 SELECT ... FOR UPDATE 会锁住间隙，并发插入时互相等待而死锁（500）。
     */
    @Test
    void manyStoresCreatingProductsAtOnceDoNotDeadlock() throws Exception {
        int threads = 12;
        List<Session> owners = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            owners.add(registerWithTenant("inv-dl" + i, "并发建商品店" + i));
        }
        for (int round = 0; round < 3; round++) {
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (Session s : owners) {
                final int r = round;
                results.add(pool.submit(() -> {
                    start.await();
                    return send(post("/api/v1/products"), s, """
                            {"name":"并发商品%d","unit":"件","costPrice":"1","retailPrice":"2","lowStockThreshold":0,"initialStock":5}
                            """.formatted(r)).getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get()).isEqualTo(201);
            }
            pool.shutdown();
        }
    }

    @Test
    void concurrentStockOutNeverOversells() throws Exception {
        Session s = registerWithTenant("inv-conc", "并发测试店");
        Long tenantId = currentTenantId(s, mvc);
        MockHttpServletResponse res = send(post("/api/v1/products"), s, """
                {"name":"抢购商品","unit":"件","costPrice":"1","retailPrice":"2","lowStockThreshold":0,"initialStock":10}
                """);
        Long productId = ((Number) read(res, "$.id")).longValue();

        int threads = 16; // 16 个请求同时各扣 1 件，库存只有 10 件
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(t -> inventory.apply(productId,
                            new InventoryService.Change(MovementType.STOCK_OUT, -1, null, null, null, "并发测试", null))));
                    return true;
                } catch (ApiException e) {
                    assertThat(e.getCode()).isEqualTo("INSUFFICIENT_STOCK");
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> f : results) {
            if (f.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(10);
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(t -> {
            Product p = products.findById(productId).orElseThrow();
            assertThat(p.getStockQty()).isZero();
            assertThat(movements.sumChangeByProductId(productId)).isZero();
        }));
    }
}
