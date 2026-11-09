-- SmallBiz Manager · V20：发码台（docs/17-code-dispenser.md）
-- 激活码原文加密保存（AES-256-GCM，密钥在服务器环境变量），记录发给了哪个小红书订单
ALTER TABLE activation_codes
    ADD COLUMN code_encrypted VARBINARY(256) NULL AFTER code_hint,
    ADD COLUMN order_ref      VARCHAR(100)   NULL AFTER redeemed_at,
    ADD COLUMN dispensed_at   DATETIME(6)    NULL AFTER order_ref,
    ADD COLUMN dispensed_by   BIGINT         NULL AFTER dispensed_at,
    ADD KEY idx_activation_codes_order (order_ref),
    ADD KEY idx_activation_codes_dispense (status, dispensed_at);

-- 发码台操作记录：取码、查看原码、作废
CREATE TABLE code_dispense_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code_id     BIGINT       NOT NULL,
    action      VARCHAR(20)  NOT NULL,
    order_ref   VARCHAR(100) NULL,
    operator_id BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_code_dispense_logs_code (code_id, created_at),
    CONSTRAINT fk_code_dispense_logs_code FOREIGN KEY (code_id) REFERENCES activation_codes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
