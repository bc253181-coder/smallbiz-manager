-- SmallBiz Manager · V1：账号、商户、成员、刷新令牌、单号序列
-- 时间统一存 UTC；字符集 utf8mb4

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(191) NOT NULL,
    password_hash     VARCHAR(100) NOT NULL,
    display_name      VARCHAR(100) NOT NULL,
    phone             VARCHAR(30)  NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_platform_admin BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at     DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tenants (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(100) NOT NULL,
    business_type        VARCHAR(30)  NOT NULL,
    currency             CHAR(3)      NOT NULL DEFAULT 'CNY',
    timezone             VARCHAR(50)  NOT NULL DEFAULT 'Asia/Shanghai',
    allow_negative_stock BOOLEAN      NOT NULL DEFAULT FALSE,
    plan                 VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    plan_expires_at      DATETIME(6)  NULL,
    trial_used           BOOLEAN      NOT NULL DEFAULT FALSE,
    contact_phone        VARCHAR(30)  NULL,
    address              VARCHAR(300) NULL,
    created_by           BIGINT       NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    version              INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tenant_members (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_members_tenant_user (tenant_id, user_id),
    KEY idx_tenant_members_user (user_id),
    CONSTRAINT fk_tenant_members_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tenant_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    token_hash        CHAR(64)    NOT NULL,
    current_tenant_id BIGINT      NULL,
    expires_at        DATETIME(6) NOT NULL,
    revoked_at        DATETIME(6) NULL,
    created_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE tenant_sequences (
    tenant_id  BIGINT      NOT NULL,
    seq_key    VARCHAR(30) NOT NULL,
    next_value INT         NOT NULL,
    PRIMARY KEY (tenant_id, seq_key),
    CONSTRAINT fk_tenant_sequences_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
