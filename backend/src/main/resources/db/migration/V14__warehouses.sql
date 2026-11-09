-- SmallBiz Manager · V14：多仓库
-- 不变量：products.stock_qty = 该商品各仓库 product_stocks.qty 之和；
--         product_stocks.qty = 该商品在该仓库的所有流水 qty_change 之和

CREATE TABLE warehouses (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT       NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    address     VARCHAR(300) NULL,
    notes       VARCHAR(500) NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    -- 每家店只有一个默认仓库
    default_key BIGINT GENERATED ALWAYS AS (IF(is_default, tenant_id, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_warehouses_tenant_name (tenant_id, name),
    UNIQUE KEY uk_warehouses_default (default_key),
    CONSTRAINT fk_warehouses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE product_stocks (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT      NOT NULL,
    product_id   BIGINT      NOT NULL,
    warehouse_id BIGINT      NOT NULL,
    qty          INT         NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_stocks (product_id, warehouse_id),
    KEY idx_product_stocks_warehouse (tenant_id, warehouse_id),
    CONSTRAINT fk_product_stocks_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_product_stocks_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_stocks_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stock_transfers (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT       NOT NULL,
    transfer_no       VARCHAR(30)  NOT NULL,
    from_warehouse_id BIGINT       NOT NULL,
    to_warehouse_id   BIGINT       NOT NULL,
    notes             VARCHAR(500) NULL,
    item_count        INT          NOT NULL,
    total_quantity    INT          NOT NULL,
    created_by        BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_transfers_no (tenant_id, transfer_no),
    KEY idx_stock_transfers_tenant_created (tenant_id, created_at),
    CONSTRAINT fk_stock_transfers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_stock_transfers_from FOREIGN KEY (from_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_stock_transfers_to FOREIGN KEY (to_warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT chk_stock_transfers_diff CHECK (from_warehouse_id <> to_warehouse_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE stock_transfer_items (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id             BIGINT       NOT NULL,
    transfer_id           BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    sku_snapshot          VARCHAR(64)  NOT NULL,
    unit_snapshot         VARCHAR(20)  NOT NULL,
    quantity              INT          NOT NULL,
    line_no               INT          NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stock_transfer_items_transfer (transfer_id),
    CONSTRAINT fk_stock_transfer_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_stock_transfer_items_transfer FOREIGN KEY (transfer_id) REFERENCES stock_transfers (id),
    CONSTRAINT fk_stock_transfer_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_stock_transfer_items_qty CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 每家店一个「主仓库」，现有库存、流水、销售单、已收货采购单全部归入
INSERT INTO warehouses (tenant_id, name, is_default, active, sort_order, created_at, updated_at)
SELECT id, '主仓库', TRUE, TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM tenants;

INSERT INTO product_stocks (tenant_id, product_id, warehouse_id, qty, created_at, updated_at)
SELECT p.tenant_id, p.id, w.id, p.stock_qty, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM products p JOIN warehouses w ON w.tenant_id = p.tenant_id AND w.is_default;

ALTER TABLE stock_movements ADD COLUMN warehouse_id BIGINT NULL AFTER product_id;
UPDATE stock_movements m JOIN warehouses w ON w.tenant_id = m.tenant_id AND w.is_default SET m.warehouse_id = w.id;
ALTER TABLE stock_movements
    MODIFY COLUMN warehouse_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_stock_movements_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    ADD KEY idx_stock_movements_warehouse (tenant_id, warehouse_id, created_at);

ALTER TABLE sales_orders ADD COLUMN warehouse_id BIGINT NULL AFTER customer_name_snapshot;
UPDATE sales_orders o JOIN warehouses w ON w.tenant_id = o.tenant_id AND w.is_default SET o.warehouse_id = w.id;
ALTER TABLE sales_orders
    MODIFY COLUMN warehouse_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_sales_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

-- 采购单：收货时才确定仓库（未收货的单为空）
ALTER TABLE purchase_orders ADD COLUMN warehouse_id BIGINT NULL AFTER supplier_name_snapshot,
    ADD CONSTRAINT fk_purchase_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);
UPDATE purchase_orders o JOIN warehouses w ON w.tenant_id = o.tenant_id AND w.is_default
SET o.warehouse_id = w.id WHERE o.status = 'RECEIVED';
