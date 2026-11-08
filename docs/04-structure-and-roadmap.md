# 04 · 项目目录与开发计划

## 1. 项目目录

```
smallbiz-manager/
├── README.md                     # 如何启动、环境要求
├── docs/                         # 本设计文档
├── assets/payment/               # 初始收款码图片（wechat-pay.jpg、tng-duitnow.jpg），首次启动时导入
├── backend/
│   ├── mvnw, mvnw.cmd, pom.xml   # Maven Wrapper，无需单独安装 Maven
│   └── src/
│       ├── main/java/com/smallbiz/
│       │   ├── SmallBizApplication.java
│       │   ├── common/           # 统一响应、分页、异常、BaseEntity、TenantEntity、金额工具
│       │   ├── config/           # CORS、Jackson、OpenAPI、文件存储配置
│       │   ├── security/         # JWT 生成/校验、过滤器、SecurityConfig、当前用户
│       │   ├── tenant/           # TenantContext、Hibernate 租户解析器、商户与成员、单号生成、套餐校验
│       │   ├── auth/             # 注册、登录、刷新、退出、个人资料
│       │   ├── catalog/          # 分类、商品、图片上传
│       │   ├── inventory/        # InventoryService（唯一改库存入口）、流水、低库存
│       │   ├── customer/
│       │   ├── supplier/
│       │   ├── sales/            # 销售订单
│       │   ├── purchasing/       # 采购订单
│       │   ├── dashboard/
│       │   ├── report/           # 聚合查询（原生 SQL，必须带 tenant_id）
│       │   ├── billing/          # PlanPolicy（套餐权益）、激活码兑换、套餐变更记录
│       │   ├── support/          # 客服话术匹配、未匹配问题记录
│       │   └── admin/            # 平台管理员接口（商户、激活码、话术）
│       │   # 每个业务包内：XxxController / XxxService / XxxRepository / Xxx(Entity) / dto/
│       ├── main/resources/
│       │   ├── application.yml, application-dev.yml, application-prod.yml
│       │   └── db/migration/     # Flyway：V1__init.sql, V2__…
│       └── test/java/com/smallbiz/
│           ├── support/          # 测试基类：建两个商户 A、B 并各自登录
│           └── …                 # 各模块集成测试 + TenantIsolationTest
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig.json, index.html
│   └── src/
│       ├── main.tsx, App.tsx, router.tsx
│       ├── lib/                  # api 客户端（自动带 token / 自动刷新）、格式化、工具
│       ├── components/
│       │   ├── ui/               # shadcn/ui 基础组件（按钮、输入框、弹窗、表格…）
│       │   └── layout/           # AppShell、Sidebar、Topbar、MobileNav、GlobalSearch
│       ├── features/
│       │   ├── auth/  onboarding/  dashboard/  products/  categories/
│       │   ├── inventory/  sales/  purchases/  customers/  suppliers/
│       │   ├── reports/  settings/  billing/
│       │   ├── support/          # 客服悬浮窗口
│       │   └── admin/            # 平台后台页面
│       │   # 每个功能内：api.ts / types.ts / hooks.ts / components/ / pages/
│       └── styles/index.css
└── e2e/                          # Playwright 端到端测试（注册→开店→建商品→采购→销售全流程）
```

## 2. 开发阶段与任务清单

每个阶段结束都执行同一套**完成检查**：
- ✅ 后端编译通过、全部测试通过（`mvnw test`）
- ✅ 前端类型检查与构建通过（`npm run build`）
- ✅ 在浏览器中实际操作该模块的全部按钮（桌面 + 手机尺寸）
- ✅ 数据一致性检查（库存 = 流水之和，订单金额 = 明细之和）
- ✅ 跨商户隔离测试：商户 B 无法读取/修改/删除商户 A 的数据（404）
- ✅ 修复问题后再进入下一阶段，并在本文档勾选

### 阶段 0 · 环境准备 ✅（2026-11-08 完成）
- [x] MySQL 8.4.9：Windows 服务 `MySQL84`（开机自启），仅监听 127.0.0.1:3306，utf8mb4，UTC；库 `smallbiz`、`smallbiz_test`，项目账号 `smallbiz`；凭据在 `.env.dev`（已被 `.gitignore` 排除）
- [x] Git 2.56，仓库已初始化（main 分支），每个阶段一个提交
- [x] 项目文件夹：`OneDrive\应用\smallbiz-manager`

### 阶段 1 · 项目骨架 + 认证 + 多商户（最关键）
- [ ] 后端：Spring Boot 项目、Flyway V1（users、tenants、tenant_members、refresh_tokens、tenant_sequences）
- [ ] JWT 认证、注册/登录/刷新/退出、BCrypt
- [ ] TenantEntity + Hibernate `@TenantId` 自动隔离；角色权限；平台管理员标识
- [ ] tenants 表包含套餐字段（plan、plan_expires_at、trial_used），新商户默认免费版
- [ ] 前端：Vite + React + TS + Tailwind + shadcn/ui；登录、注册、创建商户页面
- [ ] AppShell 布局（侧边栏、顶栏、手机底部导航），空白的仪表盘
- [ ] 测试：注册→创建商户→登录；token 过期刷新；隔离测试基类
- **验收**：两个账号分别注册并创建商户，各自登录后进入自己的空仪表盘

### 阶段 2 · 分类与商品
- [ ] categories、products 表；增删改查、搜索、筛选、排序、分页
- [ ] 图片上传（本地存储，接口抽象便于以后换对象存储）
- [ ] 前端：分类管理、商品列表（桌面表格/手机卡片）、商品表单、商品详情
- **验收**：商户 A 建的商品，商户 B 看不到、搜不到、按 id 访问返回 404；同商户 SKU 不能重复，不同商户可以重复

### 阶段 3 · 库存
- [ ] stock_movements；InventoryService（行锁、负库存校验、写流水）
- [ ] 入库、出库、盘点调整、流水查询、低库存列表、通知铃铛
- [ ] 并发测试：10 个线程同时出库同一商品，结果不出现超卖
- **验收**：任意操作后，商品库存 = 流水累计

### 阶段 4 · 客户与供应商
- [ ] 增删改查、搜索、软删除；详情页（历史订单区域先留空，阶段 5/6 接入）

### 阶段 5 · 采购
- [ ] 采购单创建/编辑/下单/收货/取消；收货增加库存、更新加权平均成本
- [ ] 前端：采购列表、采购表单（动态商品行）、详情及状态按钮；低库存"一键补货"生成采购草稿
- **验收**：收货前库存不变，收货后增加；重复点击"收货"不会重复入库

### 阶段 6 · 销售
- [ ] 开单（零售/批发价自动带出，可改单价、整单优惠）、扣库存、成本快照
- [ ] 订单列表、详情、取消并恢复库存、打印样式
- [ ] 客户详情显示历史订单和累计金额
- **验收**：库存不足时无法下单且不产生任何数据；取消后库存恢复且统计中排除

### 阶段 7 · 仪表盘与报表
- [ ] 仪表盘 8 项指标 + 趋势图 + 最近订单 + 低库存（全部来自真实数据）
- [ ] 报表页：日/月销售、商品排行、客户排行、采购统计、毛利、库存统计、CSV 导出
- [ ] 报表隔离测试（原生 SQL 必须带 tenant_id）
- **验收**：手工造一组已知数据，报表数字与手算一致

### 阶段 8 · 套餐、激活码、客服、平台后台
- [ ] `PlanPolicy` + `@RequiresFeature`：免费版 100 个商品上限、标准版/专业版功能拦截、到期自动降级（数据保留）
- [ ] 激活码：批次生成与 CSV 导出、兑换、首月体验每商户限一次、作废、套餐变更记录
- [ ] 价格表 `plan_prices`（人民币 + 马币）、收款方式 `payment_methods`（导入 `assets/payment/` 下的微信、TNG 收款码）
- [ ] 扫码付款单：创建 → 上传截图 → 待审核 → 通过/拒绝/过期/退款；通过后自动开通套餐
- [ ] 前端"套餐与续费"页、扫码付款页、套餐限制弹窗；后台付款审核页、收款方式与价格管理页
- [ ] 客服：话术匹配接口、悬浮窗口（含登录页）、👍👎 反馈、转人工微信卡片（微信号读配置）、未匹配问题记录
- [ ] 内置约 30 条初始话术（Flyway 数据脚本）
- [ ] 平台后台 `/admin`：商户列表、手动开通、激活码、话术管理
- **验收**：
  - 首月码兑换后获得 30 天标准版；同一商户再兑换首月码被拒绝；到期后自动回到免费版且数据都在
  - 同一个激活码不能被两个商户使用（并发兑换测试）
  - 付款单审核通过后套餐正确开通；重复点击"通过"只开通一次；同一交易单号不能提交两次
  - 商户 B 无法查看商户 A 的付款单和付款截图；截图没有公开链接
  - 非平台管理员访问 `/admin` 接口返回 403
  - 客服对 30 条话术的常见问法都能命中；无关问题能转人工并被记录

### 阶段 9 · 打磨与上线准备
- [ ] 全站响应式检查（375px / 768px / 1280px）、空状态、错误状态、加载状态
- [ ] Playwright 端到端测试：注册→开店→建商品→采购收货→销售→取消→看报表→兑换激活码
- [ ] 用户协议、隐私政策页面（注册时勾选同意）
- [ ] 生产配置、打包说明、部署方案（云服务器 + Nginx + HTTPS，或容器）

### 第二阶段（MVP 之后）
应收账款与收款记录 → 员工邀请与权限管理 → Excel 导入导出 → 条形码扫码 → 客户协议价 → 多仓库 → 多语言/多币种 → 在线支付（复用激活码开通逻辑） → 客服接入大模型（仅基于话术库回答） → PWA。

> 说明：专业版权益中的"应收账款、员工账号、Excel 导出、客户协议价"属于第二阶段功能。MVP 上线时先只售卖**首月体验**和**标准版**，专业版等这些功能完成后再开卖，避免卖出还没有的功能。

## 3. 需要提前提醒的事项

1. **定价**：已确定为 免费版 / 标准版 19.9 元月·168 元年 / 专业版 39.9 元月·328 元年，首月体验 1.99 元；马币：首月 RM 1.00 / 标准版 RM 12.90 月·RM 109 年 / 专业版 RM 25.90 月·RM 209 年（详见 01 文档第 4 节）。首月体验到期后的续费率是最关键的观察指标。
2. **收款**：微信个人收款码用于长期经营可能受限，正式运营建议换成经营收款码；扫码付款需人工审核，用户多了要接在线支付（见 01 文档 4.4）。
3. **小红书销售合规**：数字产品/软件服务能否上架、能否以激活码形式发货、平台抽成比例，需要按小红书当时的商家规则执行。客服微信和收款码只放在自己网站内，**不要**出现在小红书笔记、评论、私信和商品页中。
4. **数据安全与隐私**：商户的客户资料属于个人信息。正式运营前需要准备《用户协议》和《隐私政策》；如果服务器在中国大陆，网站还需要 ICP 备案。
5. **备份**：上线后数据库需要每日自动备份，这是 SaaS 的基本责任。
