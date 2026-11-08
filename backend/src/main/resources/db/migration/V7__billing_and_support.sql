-- SmallBiz Manager · V7：套餐价格、激活码、扫码付款、套餐变更记录、客服话术
-- 价格、收款方式、话术为平台级（无 tenant_id）；付款单属于商户

CREATE TABLE plan_prices (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    plan          VARCHAR(20)   NOT NULL,
    period        VARCHAR(20)   NOT NULL,
    duration_days INT           NOT NULL,
    currency      CHAR(3)       NOT NULL,
    amount        DECIMAL(10,2) NOT NULL,
    enabled       BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order    INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_prices (plan, period, currency)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 专业版的应收账款、员工账号等功能完成前暂不开售（enabled = FALSE）
INSERT INTO plan_prices (plan, period, duration_days, currency, amount, enabled, sort_order) VALUES
('STANDARD', 'TRIAL_MONTH', 30,  'CNY',   1.99, TRUE,  1),
('STANDARD', 'MONTH',       30,  'CNY',  19.90, TRUE,  2),
('STANDARD', 'YEAR',        365, 'CNY', 168.00, TRUE,  3),
('PRO',      'MONTH',       30,  'CNY',  39.90, FALSE, 4),
('PRO',      'YEAR',        365, 'CNY', 328.00, FALSE, 5),
('STANDARD', 'TRIAL_MONTH', 30,  'MYR',   1.00, TRUE,  1),
('STANDARD', 'MONTH',       30,  'MYR',  12.90, TRUE,  2),
('STANDARD', 'YEAR',        365, 'MYR', 109.00, TRUE,  3),
('PRO',      'MONTH',       30,  'MYR',  25.90, FALSE, 4),
('PRO',      'YEAR',        365, 'MYR', 209.00, FALSE, 5);

CREATE TABLE activation_code_batches (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    plan          VARCHAR(20)  NOT NULL,
    duration_days INT          NOT NULL,
    is_trial      BOOLEAN      NOT NULL DEFAULT FALSE,
    quantity      INT          NOT NULL,
    expires_at    DATETIME(6)  NULL,
    created_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE activation_codes (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    batch_id           BIGINT      NOT NULL,
    code_hash          CHAR(64)    NOT NULL,
    code_hint          VARCHAR(8)  NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'UNUSED',
    redeemed_tenant_id BIGINT      NULL,
    redeemed_by        BIGINT      NULL,
    redeemed_at        DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activation_codes_hash (code_hash),
    KEY idx_activation_codes_batch (batch_id),
    KEY idx_activation_codes_hint (code_hint),
    CONSTRAINT fk_activation_codes_batch FOREIGN KEY (batch_id) REFERENCES activation_code_batches (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE payment_methods (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(30)  NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    currency      CHAR(3)      NOT NULL,
    qr_image_path VARCHAR(300) NULL,
    payee_name    VARCHAR(100) NULL,
    instructions  VARCHAR(500) NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_methods_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 收款码图片在首次启动时从 assets/payment 导入（见 PaymentMethodBootstrap）
INSERT INTO payment_methods (code, display_name, currency, payee_name, instructions, sort_order) VALUES
('WECHAT_PAY', '微信支付', 'CNY', '晏(**森)', '打开微信「扫一扫」付款，并在付款备注中填写备注码。', 1),
('TNG_DUITNOW', 'Touch ''n Go / DuitNow', 'MYR', 'ZHANG YISEN', '可用任意马来西亚银行 App 或电子钱包扫码（DuitNow QR），并在备注中填写备注码。', 2);

CREATE TABLE payment_orders (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id         BIGINT        NOT NULL,
    order_no          VARCHAR(30)   NOT NULL,
    reference_code    VARCHAR(12)   NOT NULL,
    plan_price_id     BIGINT        NOT NULL,
    plan              VARCHAR(20)   NOT NULL,
    period            VARCHAR(20)   NOT NULL,
    duration_days     INT           NOT NULL,
    currency          CHAR(3)       NOT NULL,
    amount            DECIMAL(10,2) NOT NULL,
    payment_method_id BIGINT        NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    proof_image_path  VARCHAR(300)  NULL,
    transaction_ref   VARCHAR(100)  NULL,
    payer_note        VARCHAR(300)  NULL,
    expires_at        DATETIME(6)   NOT NULL,
    submitted_at      DATETIME(6)   NULL,
    reviewed_by       BIGINT        NULL,
    reviewed_at       DATETIME(6)   NULL,
    reject_reason     VARCHAR(200)  NULL,
    created_by        BIGINT        NULL,
    version           INT           NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_orders_no (order_no),
    UNIQUE KEY uk_payment_orders_ref (reference_code),
    UNIQUE KEY uk_payment_orders_txn (transaction_ref),
    KEY idx_payment_orders_status (status, submitted_at),
    KEY idx_payment_orders_tenant (tenant_id, created_at),
    CONSTRAINT fk_payment_orders_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_payment_orders_price FOREIGN KEY (plan_price_id) REFERENCES plan_prices (id),
    CONSTRAINT fk_payment_orders_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plan_change_logs (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id          BIGINT       NOT NULL,
    source             VARCHAR(20)  NOT NULL,
    activation_code_id BIGINT       NULL,
    payment_order_id   BIGINT       NULL,
    old_plan           VARCHAR(20)  NOT NULL,
    new_plan           VARCHAR(20)  NOT NULL,
    old_expires_at     DATETIME(6)  NULL,
    new_expires_at     DATETIME(6)  NULL,
    operator_id        BIGINT       NULL,
    note               VARCHAR(200) NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_plan_change_logs_tenant (tenant_id, created_at),
    CONSTRAINT fk_plan_change_logs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE support_faqs (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    category        VARCHAR(30)  NOT NULL,
    question        VARCHAR(200) NOT NULL,
    keywords        VARCHAR(500) NOT NULL,
    answer          TEXT         NOT NULL,
    action_label    VARCHAR(30)  NULL,
    action_path     VARCHAR(100) NULL,
    is_quick_reply  BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order      INT          NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    helpful_count   INT          NOT NULL DEFAULT 0,
    unhelpful_count INT          NOT NULL DEFAULT 0,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE support_unmatched_queries (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    query_text      VARCHAR(300) NOT NULL,
    normalized_text VARCHAR(300) NOT NULL,
    occurrences     INT          NOT NULL DEFAULT 1,
    last_seen_at    DATETIME(6)  NOT NULL,
    resolved        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_support_unmatched_norm (normalized_text)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO support_faqs (category, question, keywords, answer, action_label, action_path, is_quick_reply, sort_order, updated_at) VALUES
('商品', '怎么添加商品？', '添加商品,新增商品,上架,录入商品,新建商品,加商品', '进入「商品」→ 点右上角「新增商品」，填写名称、价格和初始库存即可。SKU 可以留空，系统会自动编号。', '去添加商品', '/products/new', TRUE, 1, NOW(6)),
('销售', '怎么开单？', '开单,卖货,销售,收银,下单,开销售单,记账', '点「开单」（手机底部中间的 ＋ 按钮），搜索商品加入订单，选择客户或散客，确认金额后点「完成开单」。开单后库存会自动扣减。', '去开单', '/sales/new', TRUE, 2, NOW(6)),
('套餐', '套餐和价格', '价格,多少钱,收费,套餐,标准版,专业版,免费版,费用,升级,续费', '免费版长期可用（最多 100 个商品）。标准版 19.9 元/月或 168 元/年，开放采购、客户、供应商、完整报表和导出。新店铺首月体验仅 1.99 元。详情见「设置 → 套餐与续费」。', '查看套餐', '/settings/billing', TRUE, 3, NOW(6)),
('套餐', '激活码怎么用？', '激活码,兑换码,卡密,兑换,激活', '在「设置 → 套餐与续费」输入激活码，点「兑换」即可立即开通。激活码只能使用一次；首月体验码每个店铺限用一次。', '去兑换', '/settings/billing', TRUE, 4, NOW(6)),
('账号', '忘记密码怎么办？', '忘记密码,找回密码,重置密码,密码忘了,登不上,登录不了', '目前可以联系人工客服协助重置密码。登录后可在「设置 → 修改密码」自行修改密码。', NULL, NULL, TRUE, 5, NOW(6)),
('库存', '怎么入库、出库、盘点？', '入库,出库,盘点,库存调整,进货入库,损耗,调库存', '进入「库存」，在商品右侧点「入库 / 出库 / 盘点」。入库可以填单价，系统会自动按加权平均更新成本；盘点只需填实际清点的数量。', '去库存', '/inventory', FALSE, 6, NOW(6)),
('库存', '低库存提醒怎么设置？', '低库存,预警,库存不足,缺货提醒,补货提醒', '在商品资料里设置「低库存预警值」。库存小于或等于这个数时，顶部铃铛和仪表盘会提醒你补货。', NULL, NULL, FALSE, 7, NOW(6)),
('库存', '库存不足还能开单吗？', '负库存,先卖后补,库存不够,超卖', '默认不允许，以免库存出错。如果你经常先卖后补货，可以在「设置」中开启「允许负库存」。', '去设置', '/settings', FALSE, 8, NOW(6)),
('采购', '怎么进货 / 采购？', '采购,进货,采购单,补货,下单进货,供应商下单', '进入「采购」→「新建采购单」，选择供应商和商品。到货后在采购单上点「确认收货」，库存会自动增加，成本自动更新。可用「添加低库存商品」一键补货。', '去采购', '/purchases/new', FALSE, 9, NOW(6)),
('采购', '采购单下单后库存没有增加？', '采购没入库,库存没变,下单没加库存,收货', '采购单「下单」只代表发给了供应商。货到之后需要在采购单上点「确认收货」，库存才会增加。', NULL, NULL, FALSE, 10, NOW(6)),
('销售', '怎么取消订单 / 退货？', '取消订单,退货,退款,撤销订单,作废', '打开销售单，点「取消订单」，填写原因。取消后商品库存会自动恢复，这笔销售不再计入统计。只有店主和管理员可以取消。', NULL, NULL, FALSE, 11, NOW(6)),
('销售', '怎么用批发价？', '批发价,批发,批发客户,零售价,价格类型', '在商品资料中填写批发价；把客户的「默认价格」设为批发价。开单选择该客户时会自动使用批发价，也可以在开单页手动切换。', NULL, NULL, FALSE, 12, NOW(6)),
('销售', '可以打印销售单吗？', '打印,小票,单据,打印订单', '打开销售单，点「打印」即可。打印时会显示店铺名称、电话和地址（在「设置」中填写）。', NULL, NULL, FALSE, 13, NOW(6)),
('销售', '可以改价或打折吗？', '改价,打折,优惠,折扣,便宜,抹零', '开单时可以直接修改每个商品的单价，也可以在右侧填写「整单优惠」。', NULL, NULL, FALSE, 14, NOW(6)),
('报表', '在哪里看销售额和利润？', '报表,利润,毛利,销售额,营业额,赚了多少,统计', '仪表盘显示今日和本月的销售额与毛利；「报表」页可以按日期查看销售趋势、商品排行、客户排行，并导出 CSV。', '去报表', '/reports', FALSE, 15, NOW(6)),
('报表', '可以导出 Excel 吗？', '导出,excel,表格,下载数据,csv', '在「报表」页的销售、商品排行、客户排行中点「导出 CSV」，可以直接用 Excel 打开。导出功能需要标准版。', NULL, NULL, FALSE, 16, NOW(6)),
('客户', '怎么添加客户？', '添加客户,新增客户,客户资料,登记客户', '进入「客户」→「新增客户」，也可以在开单页直接点「新增客户」。客户可以设置默认价格（零售或批发）。', '去客户', '/customers', FALSE, 17, NOW(6)),
('客户', '怎么查看客户买过什么？', '客户记录,客户消费,购买记录,客户订单,累计消费', '打开客户详情，可以看到订单数、累计消费金额和全部购买记录（不含已取消的订单）。', NULL, NULL, FALSE, 18, NOW(6)),
('商品', '怎么批量导入商品？', '批量导入,导入商品,excel导入,批量上传', '批量导入功能正在开发中，目前请逐个添加。如商品很多，可以联系人工客服协助。', NULL, NULL, FALSE, 19, NOW(6)),
('商品', '商品图片上传失败？', '图片,上传失败,传不上,照片', '图片支持 JPG、PNG、WebP 格式，大小不超过 2MB。请压缩后重试。', NULL, NULL, FALSE, 20, NOW(6)),
('商品', '删除商品会影响历史订单吗？', '删除商品,删商品,误删', '不会。删除的商品不再显示，但历史订单和库存记录会完整保留。删除后该 SKU 可以给新商品使用。', NULL, NULL, FALSE, 21, NOW(6)),
('店铺', '可以开多个店铺吗？', '多个店铺,多店,分店,新建店铺,切换店铺', '可以。点左上角的店铺名称 →「新建店铺」。每个店铺的数据完全独立，可以随时切换。', NULL, NULL, FALSE, 22, NOW(6)),
('店铺', '可以让员工一起用吗？', '员工,店员,多人,子账号,权限,同事', '员工账号功能将在专业版中提供，目前正在开发中。店员角色可以开单，但看不到成本和利润。', NULL, NULL, FALSE, 23, NOW(6)),
('套餐', '首月 1.99 元怎么买？', '1.99,首月,体验,试用,优惠', '新店铺首月体验仅需 1.99 元（马币 RM 1.00），享受标准版全部功能 30 天，每个店铺限一次，到期不会自动扣费。可在「设置 → 套餐与续费」扫码付款，或在小红书店铺购买激活码。', '去开通', '/settings/billing', FALSE, 24, NOW(6)),
('套餐', '付款后多久开通？', '付款后,多久开通,没开通,审核,付了钱', '扫码付款并上传截图后，客服会在 24 小时内核对开通。使用激活码则是立即开通。', NULL, NULL, FALSE, 25, NOW(6)),
('套餐', '套餐到期了数据会丢吗？', '到期,过期,数据丢失,不续费,降级', '不会。到期后会回到免费版，所有数据都保留，标准版功能变为不可用，续费后立即恢复。', NULL, NULL, FALSE, 26, NOW(6)),
('套餐', '可以退款吗？', '退款,退钱,不想用了', '如需退款，请联系人工客服说明情况，我们会按《用户协议》处理。', NULL, NULL, FALSE, 27, NOW(6)),
('安全', '我的数据安全吗？', '安全,隐私,数据泄露,别人看到,保密', '每个店铺的数据严格隔离，其他商户无法查看。密码加密存储，登录凭证定期更换。', NULL, NULL, FALSE, 28, NOW(6)),
('使用', '手机上可以用吗？', '手机,app,下载,ios,安卓,平板', '可以，直接用手机浏览器打开网址即可，无需下载 App。建议在浏览器中「添加到主屏幕」，使用更方便。', NULL, NULL, FALSE, 29, NOW(6)),
('使用', '成本价是怎么算的？', '成本,加权平均,成本价,进价', '采购收货或入库时填写单价，系统按移动加权平均自动计算成本：(现库存×原成本 + 入库数×单价) ÷ 总数量。毛利 = 实收 − 销售时的成本。', NULL, NULL, FALSE, 30, NOW(6));
