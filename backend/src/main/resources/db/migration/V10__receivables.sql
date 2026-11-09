-- SmallBiz Manager · V10：应收账款（专业版）
-- 不变式：sales_orders.paid_amount = paid_at_sale + 该单所有有效冲抵之和；0 ≤ paid_amount ≤ total_amount

ALTER TABLE sales_orders
    ADD COLUMN paid_at_sale DECIMAL(14,2) NULL AFTER total_cost,
    ADD COLUMN last_payment_at DATETIME(6) NULL AFTER payment_status;
-- 已有销售单都是开单时全额收款
UPDATE sales_orders SET paid_at_sale = paid_amount;
ALTER TABLE sales_orders MODIFY COLUMN paid_at_sale DECIMAL(14,2) NOT NULL;
CREATE INDEX idx_sales_orders_tenant_customer_pay ON sales_orders (tenant_id, customer_id, payment_status);

CREATE TABLE customer_payments (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT        NOT NULL,
    payment_no   VARCHAR(30)   NOT NULL,
    customer_id  BIGINT        NOT NULL,
    amount       DECIMAL(14,2) NOT NULL,
    method       VARCHAR(20)   NOT NULL,
    received_at  DATETIME(6)   NOT NULL,
    notes        VARCHAR(500)  NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'VALID',
    voided_at    DATETIME(6)   NULL,
    voided_by    BIGINT        NULL,
    void_reason  VARCHAR(200)  NULL,
    created_by   BIGINT        NULL,
    version      INT           NOT NULL DEFAULT 0,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_payments_tenant_no (tenant_id, payment_no),
    KEY idx_customer_payments_tenant_customer (tenant_id, customer_id, received_at),
    KEY idx_customer_payments_tenant_received (tenant_id, received_at),
    CONSTRAINT fk_customer_payments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_customer_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_customer_payments_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payment_allocations (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT        NOT NULL,
    payment_id     BIGINT        NOT NULL,
    sales_order_id BIGINT        NOT NULL,
    amount         DECIMAL(14,2) NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_allocations (payment_id, sales_order_id),
    KEY idx_payment_allocations_order (tenant_id, sales_order_id),
    CONSTRAINT fk_payment_allocations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_payment_allocations_payment FOREIGN KEY (payment_id) REFERENCES customer_payments (id),
    CONSTRAINT fk_payment_allocations_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
    CONSTRAINT chk_payment_allocations_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 专业版随应收账款一起开卖（价格见 V9；不提供首月体验）
UPDATE plan_prices SET enabled = TRUE WHERE plan = 'PRO';