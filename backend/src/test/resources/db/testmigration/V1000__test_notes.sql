-- 仅测试使用：一个最简单的商户数据表，用于验证 @TenantId 自动隔离机制
CREATE TABLE test_notes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT       NOT NULL,
    content    VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_test_notes_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
