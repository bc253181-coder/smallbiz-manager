-- SmallBiz Manager · V18：普通会员 + 高级永久会员（docs/16-lifetime-membership.md）
-- 会员跟着账号走：永久会员名下的店铺 plan = PRO 且 plan_expires_at 为空（永久有效）

ALTER TABLE users
    ADD COLUMN membership     VARCHAR(20) NOT NULL DEFAULT 'FREE' AFTER is_platform_admin,
    ADD COLUMN lifetime_since DATETIME(6) NULL AFTER membership;

-- 普通会员的累计用量（按账号；删除不返还）
CREATE TABLE account_usage (
    user_id          BIGINT      NOT NULL,
    stores_created   INT         NOT NULL DEFAULT 0,
    products_created INT         NOT NULL DEFAULT 0,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_account_usage_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 现有账号按已有数据初始化：店主名下的店铺数、这些店铺里建过的商品数（含已删除）
INSERT INTO account_usage (user_id, stores_created, products_created, updated_at)
SELECT u.id,
       (SELECT COUNT(*) FROM tenant_members m WHERE m.user_id = u.id AND m.role = 'OWNER'),
       (SELECT COUNT(*) FROM products p JOIN tenant_members m ON m.tenant_id = p.tenant_id
         WHERE m.user_id = u.id AND m.role = 'OWNER'),
       UTC_TIMESTAMP(6)
FROM users u;

-- 普通会员配置（只有一行；平台后台可修改）
CREATE TABLE membership_config (
    id                        SMALLINT    NOT NULL,
    free_max_stores           INT         NOT NULL,
    free_max_products         INT         NOT NULL,
    free_report_days          INT         NOT NULL,
    free_advanced_tools       BOOLEAN     NOT NULL,
    free_advanced_templates   BOOLEAN     NOT NULL,
    free_export               BOOLEAN     NOT NULL,
    free_advanced_analytics   BOOLEAN     NOT NULL,
    updated_by                BIGINT      NULL,
    updated_at                DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO membership_config VALUES (1, 2, 3, 5, FALSE, FALSE, FALSE, FALSE, NULL, UTC_TIMESTAMP(6));

-- 会员变更记录（按账号）
CREATE TABLE membership_logs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    old_membership     VARCHAR(20)  NOT NULL,
    new_membership     VARCHAR(20)  NOT NULL,
    source             VARCHAR(20)  NOT NULL,
    payment_order_id   BIGINT       NULL,
    activation_code_id BIGINT       NULL,
    operator_id        BIGINT       NULL,
    note               VARCHAR(200) NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_membership_logs_user (user_id, created_at),
    CONSTRAINT fk_membership_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 价格：停止销售按月 / 按年订阅和首月体验，改卖高级永久会员（价格可在后台修改）
UPDATE plan_prices SET enabled = FALSE;
INSERT INTO plan_prices (plan, period, duration_days, currency, amount, enabled, sort_order) VALUES
('PRO', 'LIFETIME', 0, 'CNY', 29.90, TRUE, 0),
('PRO', 'LIFETIME', 0, 'MYR', 19.90, TRUE, 0);

-- 永久会员激活码
ALTER TABLE activation_code_batches ADD COLUMN lifetime BOOLEAN NOT NULL DEFAULT FALSE AFTER is_trial;

-- 客服话术：改为普通会员 / 高级永久会员
UPDATE support_faqs SET
  answer = '会员分两种：普通会员免费，可以开 2 家店、新建 3 个商品，开单、库存管理、扫码都能用；高级永久会员 29.9 元（马币 RM 19.9），一次性付款、永久有效、不自动续费，解锁全部功能：店铺和商品不限量（合理使用）、客户、供应商、采购、批发价、赊账收款、员工账号、多仓库、协议价、打印条码标签、导出 Excel、完整报表和数据分析，以及以后新增的高级功能。详情见「设置 → 会员」。',
  keywords = '价格,多少钱,收费,会员,高级会员,永久会员,普通会员,免费,费用,升级,续费,区别,套餐'
WHERE question = '套餐和价格';
UPDATE support_faqs SET question = '会员和价格' WHERE question = '套餐和价格';

UPDATE support_faqs SET
  question = '高级永久会员有什么功能？',
  answer = '高级永久会员解锁全部功能：店铺和商品不限量（合理使用）、客户和供应商、采购入库、批发价和客户协议价、赊账与收款（欠款、账龄、对账单）、员工账号（每家店店主以外最多 5 人）、多仓库与调拨、打印条码标签、导出 CSV / Excel、完整报表和数据分析，以及以后新增的高级功能。价格 29.9 元（RM 19.9），一次性付款，你名下所有店铺都有效。',
  keywords = '高级会员,永久会员,专业版,pro,高级版,会员功能,升级'
WHERE question = '专业版有什么功能？';

UPDATE support_faqs SET enabled = FALSE WHERE question = '首月 1.99 元怎么买？';

UPDATE support_faqs SET
  question = '会员到期或没升级，数据会丢吗？',
  answer = '不会。高级永久会员不会到期。以前买的标准版、专业版在有效期内照常使用，到期后回到普通会员，所有数据都保留：已建的商品、店铺、单据照常查看和编辑，只是高级功能和新建次数按普通会员计算，升级永久会员后立即恢复。'
WHERE question = '套餐到期了数据会丢吗？';

UPDATE support_faqs SET
  answer = '在「设置 → 会员」输入激活码，点「兑换」即可立即开通。激活码只能使用一次。永久会员激活码开通后，你名下所有店铺都成为高级会员。'
WHERE question = '激活码怎么用？';

UPDATE support_faqs SET
  answer = '可以。点左上角的店铺名称 →「新建店铺」。每个店铺的数据完全独立，可以随时切换。普通会员最多开 2 家店（关闭店铺不返还次数），高级永久会员不限（合理使用，每个账号最多 5 家）。'
WHERE question = '可以开多个店铺吗？';

UPDATE support_faqs SET
  answer = '可以。高级会员可以在「报表」页导出 CSV（能直接用 Excel 打开），也可以在商品、客户、供应商、销售、采购、库存流水等列表页点「导出 Excel」，得到 .xlsx 表格。导出是高级会员功能。'
WHERE question = '可以导出 Excel 吗？';

UPDATE support_faqs SET
  answer = REPLACE(REPLACE(answer, '免费版最多 100 个商品。', '普通会员最多新建 3 个商品（删除不返还次数），高级永久会员不限。'), '（需要标准版）', '（需要高级会员）')
WHERE question = '怎么批量导入商品？';

UPDATE support_faqs SET answer = REPLACE(answer, '员工账号是专业版功能，店主以外最多 5 人', '员工账号是高级会员功能，每家店店主以外最多 5 人')
WHERE question = '可以让员工一起用吗？';
UPDATE support_faqs SET answer = REPLACE(answer, '打印标签需要标准版。', '打印标签是高级会员功能。')
WHERE question = '没有条码的商品怎么办？能打印条码标签吗？';
UPDATE support_faqs SET answer = REPLACE(answer, '专业版可以启用多仓库', '高级会员可以启用多仓库')
WHERE question = '有多个仓库或门店怎么管理库存？';
UPDATE support_faqs SET answer = REPLACE(answer, '客户协议价是专业版功能', '客户协议价是高级会员功能')
WHERE question = '可以给老客户设置专门的价格吗？';
UPDATE support_faqs SET answer = REPLACE(answer, '赊账是专业版功能', '赊账是高级会员功能')
WHERE question = '客户赊账怎么记？';
UPDATE support_faqs SET answer = REPLACE(answer, '对账单是专业版功能', '对账单是高级会员功能')
WHERE question = '怎么给客户出对账单？';
UPDATE support_faqs SET answer = REPLACE(answer, '供应商管理需要标准版', '供应商管理是高级会员功能')
WHERE question = '怎么管理供应商？';
