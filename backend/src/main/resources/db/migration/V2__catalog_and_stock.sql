-- SmallBiz Manager · V2：商品分类、商品、库存流水

CREATE TABLE categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT      NOT NULL,
    parent_id  BIGINT      NULL,
    name       VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- parent_id 为 NULL 时 MySQL 唯一索引不生效，一级分类重名由应用层校验
    UNIQUE KEY uk_categories_tenant_parent_name (tenant_id, parent_id, name),
    KEY idx_categories_tenant (tenant_id),
    CONSTRAINT fk_categories_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT        NOT NULL,
    category_id         BIGINT        NULL,
    sku                 VARCHAR(64)   NOT NULL,
    name                VARCHAR(200)  NOT NULL,
    description         TEXT          NULL,
    image_path          VARCHAR(300)  NULL,
    unit                VARCHAR(20)   NOT NULL DEFAULT '件',
    barcode             VARCHAR(64)   NULL,
    cost_price          DECIMAL(14,2) NOT NULL DEFAULT 0,
    retail_price        DECIMAL(14,2) NOT NULL DEFAULT 0,
    wholesale_price     DECIMAL(14,2) NULL,
    stock_qty           INT           NOT NULL DEFAULT 0,
    low_stock_threshold INT           NOT NULL DEFAULT 0,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version             INT           NOT NULL DEFAULT 0,
    deleted_at          DATETIME(6)   NULL,
    created_by          BIGINT        NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_tenant_sku (tenant_id, sku),
    KEY idx_products_tenant_category (tenant_id, category_id),
    KEY idx_products_tenant_name (tenant_id, name),
    KEY idx_products_tenant_barcode (tenant_id, barcode),
    CONSTRAINT fk_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 库存流水：只增不改；任何库存变化都必须在同一事务内写入一条
CREATE TABLE stock_movements (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    type       VARCHAR(20)   NOT NULL,
    qty_change INT           NOT NULL,
    qty_before INT           NOT NULL,
    qty_after  INT           NOT NULL,
    unit_cost  DECIMAL(14,2) NULL,
    ref_type   VARCHAR(20)   NULL,
    ref_id     BIGINT        NULL,
    reason     VARCHAR(200)  NULL,
    created_by BIGINT        NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stock_movements_product (tenant_id, product_id, created_at),
    KEY idx_stock_movements_created (tenant_id, created_at),
    CONSTRAINT fk_stock_movements_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
