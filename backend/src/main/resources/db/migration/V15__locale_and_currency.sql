-- SmallBiz Manager · V15：用户界面语言、店铺币种确认

-- 用户界面语言：zh-CN / en；为空表示按浏览器语言
ALTER TABLE users ADD COLUMN locale VARCHAR(10) NULL AFTER display_name;

-- 店铺币种是否已由店主确认过。
-- 之前开店时没有地方选择币种，所有店铺都是人民币；未确认过的店铺即使已有业务数据，也允许修正一次币种
-- （只改显示的货币符号，不换算金额）。开店时选择或修改过一次之后即锁定（有业务数据时）。
ALTER TABLE tenants ADD COLUMN currency_confirmed BOOLEAN NOT NULL DEFAULT FALSE AFTER currency;
