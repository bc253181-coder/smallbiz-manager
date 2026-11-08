-- 注册时同意的用户协议 / 隐私政策版本与时间（已有账号为 NULL，下次协议更新时可提示重新同意）
ALTER TABLE users
    ADD COLUMN terms_version VARCHAR(20) NULL AFTER is_platform_admin,
    ADD COLUMN terms_accepted_at DATETIME(6) NULL AFTER terms_version;