-- SmallBiz Manager · V5：采购单
-- 状态：DRAFT 草稿 → ORDERED 已下单 → RECEIVED 已收货；DRAFT/ORDERED 可 CANCELLED
-- 名称快照：供应商/商品改名或删除后，历史单据仍显示下单时的信息

CREATE TABLE purchase_orders (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id              BIGINT        NOT NULL,
    order_no               VARCHAR(30)   NOT NULL,
    supplier_id            BIGINT        NOT NULL,
    supplier_name_snapshot VARCHAR(100)  NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    order_date             DATETIME(6)   NOT NULL,
    expected_date          DATE          NULL,
    total_amount           DECIMAL(14,2) NOT NULL DEFAULT 0,
    item_count             INT           NOT NULL DEFAULT 0,
    total_quantity         INT           NOT NULL DEFAULT 0,
    notes                  VARCHAR(500)  NULL,
    submitted_at           DATETIME(6)   NULL,
    received_at            DATETIME(6)   NULL,
    received_by            BIGINT        NULL,
    cancelled_at           DATETIME(6)   NULL,
    cancelled_by           BIGINT        NULL,
    cancel_reason          VARCHAR(200)  NULL,
    created_by             BIGINT        NULL,
    version                INT           NOT NULL DEFAULT 0,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_orders_tenant_no (tenant_id, order_no),
    KEY idx_purchase_orders_tenant_date (tenant_id, order_date),
    KEY idx_purchase_orders_tenant_supplier (tenant_id, supplier_id),
    KEY idx_purchase_orders_tenant_status (tenant_id, status),
    CONSTRAINT fk_purchase_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order_items (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT        NOT NULL,
    purchase_order_id     BIGINT        NOT NULL,
    product_id            BIGINT        NOT NULL,
    product_name_snapshot VARCHAR(200)  NOT NULL,
    sku_snapshot          VARCHAR(64)   NOT NULL,
    unit_snapshot         VARCHAR(20)   NOT NULL,
    quantity              INT           NOT NULL,
    received_qty          INT           NOT NULL DEFAULT 0,
    unit_cost             DECIMAL(14,2) NOT NULL,
    line_total            DECIMAL(14,2) NOT NULL,
    line_no               INT           NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_purchase_items_order (purchase_order_id),
    KEY idx_purchase_items_tenant_product (tenant_id, product_id),
    CONSTRAINT fk_purchase_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_purchase_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
