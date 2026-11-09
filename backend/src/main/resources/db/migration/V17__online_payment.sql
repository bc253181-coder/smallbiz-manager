-- SmallBiz Manager · V17：在线支付
-- 在线支付的付款单不对应站内收款码：payment_method_id 可为空，改由 channel 表示支付渠道
ALTER TABLE payment_orders
    MODIFY payment_method_id BIGINT NULL,
    ADD COLUMN channel         VARCHAR(30)  NULL AFTER payment_method_id,
    ADD COLUMN provider_txn_id VARCHAR(100) NULL AFTER channel,
    ADD COLUMN checkout_ref    VARCHAR(200) NULL AFTER provider_txn_id,
    ADD COLUMN paid_at         DATETIME(6)  NULL AFTER submitted_at,
    ADD COLUMN anomaly         VARCHAR(200) NULL AFTER reject_reason,
    ADD COLUMN anomaly_resolved_at DATETIME(6) NULL AFTER anomaly,
    ADD UNIQUE KEY uk_payment_orders_provider_txn (channel, provider_txn_id),
    ADD KEY idx_payment_orders_channel_status (channel, status, created_at);

-- 支付渠道发来的付款通知原文（排查用，90 天后自动删除）
CREATE TABLE payment_notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    channel     VARCHAR(30)  NOT NULL,
    order_no    VARCHAR(30)  NULL,
    body        TEXT         NOT NULL,
    verified    BOOLEAN      NOT NULL,
    result      VARCHAR(200) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_payment_notifications_created (created_at),
    KEY idx_payment_notifications_order (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 对账任务的运行记录（后台显示最近一次时间）
CREATE TABLE payment_reconcile_runs (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    checked     INT         NOT NULL,
    fixed       INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_payment_reconcile_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 注意：客服话术「付款后多久开通？」等接入真实支付渠道、正式开放在线支付时再更新（模拟支付不对外）
