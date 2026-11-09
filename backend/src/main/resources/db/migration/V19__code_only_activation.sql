-- SmallBiz Manager · V19：高级永久会员只通过激活码开通（在小红书销售激活码）
-- 网站不再提供扫码付款：停用收款方式（以前的付款单和记录保留；平台后台可以重新启用）
UPDATE payment_methods SET enabled = FALSE;

-- 客服话术：开通方式只有激活码
UPDATE support_faqs SET
  question = '怎么开通高级永久会员？',
  answer = '在小红书购买「SmallBiz 高级永久会员」激活码，收到后登录网站，在「设置 → 会员」输入激活码并点「兑换并开通」，激活码正确立即开通，你名下所有店铺都成为高级会员。目前只能用激活码开通。',
  keywords = '付款后,多久开通,没开通,怎么开通,怎么买,购买会员,开通会员,付款,支付,扫码付款,微信支付'
WHERE question = '付款后多久开通？';

UPDATE support_faqs SET
  answer = '在小红书购买「SmallBiz 高级永久会员」激活码，收到后在「设置 → 会员」输入兑换，立即开通。网站上只能用激活码开通。'
WHERE question = '激活码在哪里买？';

UPDATE support_faqs SET
  answer = '在「设置 → 会员」输入激活码，点「兑换并开通」即可立即开通。激活码只能使用一次；兑换后你名下所有店铺都成为高级会员。激活码提示不正确时，请检查是否输错（不区分大小写），或联系人工客服。'
WHERE question = '激活码怎么用？';

UPDATE support_faqs SET
  answer = '如需退款，请联系购买激活码的小红书店铺，或联系人工客服说明情况，我们会按《用户协议》处理。'
WHERE question = '可以退款吗？';
