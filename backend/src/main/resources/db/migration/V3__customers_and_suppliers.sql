-- SmallBiz Manager · V3：客户、供应商（均为软删除，历史订单仍可引用）

CREATE TABLE customers (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    phone         VARCHAR(30)  NULL,
    email         VARCHAR(191) NULL,
    address       VARCHAR(300) NULL,
    customer_type VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL',
    price_tier    VARCHAR(20)  NOT NULL DEFAULT 'RETAIL',
    notes         TEXT         NULL,
    version       INT          NOT NULL DEFAULT 0,
    deleted_at    DATETIME(6)  NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_customers_tenant_name (tenant_id, name),
    KEY idx_customers_tenant_phone (tenant_id, phone),
    CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE suppliers (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id              BIGINT       NOT NULL,
    name                   VARCHAR(100) NOT NULL,
    contact_person         VARCHAR(50)  NULL,
    phone                  VARCHAR(30)  NULL,
    email                  VARCHAR(191) NULL,
    address                VARCHAR(300) NULL,
    supplied_products_note VARCHAR(500) NULL,
    notes                  TEXT         NULL,
    version                INT          NOT NULL DEFAULT 0,
    deleted_at             DATETIME(6)  NULL,
    created_by             BIGINT       NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_suppliers_tenant_name (tenant_id, name),
    CONSTRAINT fk_suppliers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
