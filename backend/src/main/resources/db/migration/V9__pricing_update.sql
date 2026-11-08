-- 2026-11-09 调价：标准版降价；专业版（含应收账款）定价，功能完成前保持停用
-- 首月体验只针对标准版：¥1.99 / RM 1.00（不变）
UPDATE plan_prices SET amount =   6.99 WHERE plan = 'STANDARD' AND period = 'MONTH' AND currency = 'CNY';
UPDATE plan_prices SET amount =  69.90 WHERE plan = 'STANDARD' AND period = 'YEAR'  AND currency = 'CNY';
UPDATE plan_prices SET amount =   4.99 WHERE plan = 'STANDARD' AND period = 'MONTH' AND currency = 'MYR';
UPDATE plan_prices SET amount =  49.90 WHERE plan = 'STANDARD' AND period = 'YEAR'  AND currency = 'MYR';
UPDATE plan_prices SET amount =   9.90 WHERE plan = 'PRO'      AND period = 'MONTH' AND currency = 'CNY';
UPDATE plan_prices SET amount = 109.90 WHERE plan = 'PRO'      AND period = 'YEAR'  AND currency = 'CNY';
UPDATE plan_prices SET amount =   6.90 WHERE plan = 'PRO'      AND period = 'MONTH' AND currency = 'MYR';
UPDATE plan_prices SET amount =  69.90 WHERE plan = 'PRO'      AND period = 'YEAR'  AND currency = 'MYR';

-- 客服话术中的价格
UPDATE support_faqs
SET answer = '免费版长期可用（最多 100 个商品）。标准版 6.99 元/月或 69.9 元/年（马币 RM 4.99/月、RM 49.9/年），开放采购、客户、供应商、批发价开单、完整报表和导出。新店铺首月体验仅 1.99 元（RM 1.00）。详情见「设置 → 套餐与续费」。'
WHERE question = '套餐和价格';