-- SmallBiz Manager · V13：客户协议价（专业版）与销售明细的价格来源

CREATE TABLE customer_prices (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    customer_id BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    price       DECIMAL(14,2) NOT NULL,
    notes       VARCHAR(200)  NULL,
    created_by  BIGINT        NULL,
    updated_by  BIGINT        NULL,
    version     INT           NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_prices (tenant_id, customer_id, product_id),
    KEY idx_customer_prices_product (tenant_id, product_id),
    CONSTRAINT fk_customer_prices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_customer_prices_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_customer_prices_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_customer_prices_price CHECK (price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 价格来源：RETAIL 零售价 / WHOLESALE 批发价 / CONTRACT 协议价 / MANUAL 手动改价
ALTER TABLE sales_order_items ADD COLUMN price_source VARCHAR(20) NULL AFTER unit_price;
-- 已有数据按订单的价格类型回填（无法区分当时是否手动改过价）
UPDATE sales_order_items i JOIN sales_orders o ON i.sales_order_id = o.id SET i.price_source = o.price_tier;
ALTER TABLE sales_order_items MODIFY COLUMN price_source VARCHAR(20) NOT NULL;
