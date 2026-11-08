-- 刷新令牌轮换宽限期：记录令牌因"轮换"（而非退出登录/改密码）被作废的时间。
-- 轮换后短时间内旧令牌仍可使用一次，避免请求被中断（页面跳转、网络抖动、多标签页）时用户被意外登出。
ALTER TABLE refresh_tokens ADD COLUMN rotated_at DATETIME(6) NULL AFTER revoked_at;
