-- SmallBiz Manager · V11：员工邀请与成员管理（专业版）

ALTER TABLE tenant_members
    ADD COLUMN joined_at  DATETIME(6) NULL AFTER status,
    ADD COLUMN removed_at DATETIME(6) NULL AFTER joined_at,
    ADD COLUMN removed_by BIGINT      NULL AFTER removed_at;
UPDATE tenant_members SET joined_at = created_at;
ALTER TABLE tenant_members MODIFY COLUMN joined_at DATETIME(6) NOT NULL;
CREATE INDEX idx_tenant_members_tenant_status ON tenant_members (tenant_id, status);

CREATE TABLE invitations (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    role        VARCHAR(20) NOT NULL,
    note        VARCHAR(50) NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at  DATETIME(6) NOT NULL,
    created_by  BIGINT      NULL,
    accepted_by BIGINT      NULL,
    accepted_at DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invitations_token (token_hash),
    KEY idx_invitations_tenant_status (tenant_id, status, expires_at),
    CONSTRAINT fk_invitations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
