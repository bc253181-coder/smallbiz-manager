# 03 · 前端页面结构与后端 API 设计

## Part A · 前端页面结构

### A.1 布局

```
桌面端（≥1024px）                              手机端（<768px）
┌──────────┬────────────────────────────────┐   ┌──────────────────────┐
│ Logo     │ 🔍 全局搜索      🔔  店铺▾ 头像▾ │   │ ☰  店铺名       🔔 👤 │
│──────────│────────────────────────────────│   ├──────────────────────┤
│ 仪表盘    │                                │   │                      │
│ 商品      │        页面内容区                │   │   页面内容（单列）     │
│ 库存      │  卡片 / 图表 / 表格              │   │   表格 → 卡片列表     │
│ 销售      │                                │   │                      │
│ 采购      │                                │   │                      │
│ 客户      │                                │   ├──────────────────────┤
│ 供应商    │                                │   │ 首页 商品 ＋开单 销售 更多 │ ← 底部导航
│ 报表      │                                │   └──────────────────────┘
│ 设置      │                                │
└──────────┴────────────────────────────────┘
平板（768–1023px）：侧边栏收起为图标栏
```

- 配色：白色/浅灰（`slate-50` 背景、白色卡片）+ 主色蓝（`blue-600`），状态色：绿=完成、橙=低库存/部分付款、红=取消/欠款。
- 顶部搜索：输入商品名/SKU/订单号/客户名，下拉显示分组结果，点击跳转。
- 通知铃铛（MVP）：显示低库存商品数量，点开列出商品，可一键"创建采购单"。
- 手机端底部中间的"＋开单"是最常用操作：快速创建销售订单。

### A.2 路由表

| 路由 | 页面 | 主要内容与操作 | 权限 |
|---|---|---|---|
| `/login` | 登录 | 邮箱 + 密码 | 公开 |
| `/register` | 注册 | 昵称、邮箱、密码 | 公开 |
| `/onboarding` | 创建商户 | 店铺名称、经营类型；完成后进入仪表盘 | 已登录 |
| `/select-tenant` | 选择商户 | 用户属于多个商户时选择 | 已登录 |
| `/` | 仪表盘 | 8 个指标卡片、近 30 天销售趋势图、最近销售订单、低库存列表 | 全员（店员不显示成本/利润） |
| `/products` | 商品列表 | 搜索、分类筛选、状态筛选、排序、分页；新增/编辑/删除 | 全员查看，ADMIN+ 编辑 |
| `/products/new`、`/products/:id/edit` | 商品表单 | 基本信息、图片上传、价格、初始库存、预警值 | ADMIN+ |
| `/products/:id` | 商品详情 | 信息 + 库存流水 + 最近销售 | 全员 |
| `/categories` | 分类管理 | 两级树，增删改、排序 | ADMIN+ |
| `/inventory` | 库存总览 | 当前库存、库存价值、低库存筛选；行内"入库/出库/调整"弹窗 | 全员 |
| `/inventory/movements` | 库存流水 | 按商品、类型、日期筛选 | 全员 |
| `/sales` | 销售订单列表 | 按日期、客户、状态、单号筛选 | 全员 |
| `/sales/new` | 开单 | 选客户（自动带价格类型）→ 搜索/添加商品 → 数量/单价 → 优惠 → 实时合计 → 提交 | 全员 |
| `/sales/:id` | 订单详情 | 明细、金额、状态；取消订单（二次确认）；打印 | 取消：ADMIN+ |
| `/purchases` | 采购订单列表 | 筛选状态、供应商、日期 | ADMIN+ |
| `/purchases/new`、`/purchases/:id/edit` | 采购单表单 | 选供应商、添加商品、数量、单价；保存草稿 / 下单 | ADMIN+ |
| `/purchases/:id` | 采购单详情 | 状态流转按钮：下单 → 确认收货 / 取消 | ADMIN+ |
| `/customers` | 客户列表 | 搜索、类型筛选；显示累计消费 | 全员 |
| `/customers/:id` | 客户详情 | 资料、累计金额、订单历史 | 全员 |
| `/suppliers` | 供应商列表 | 搜索 | ADMIN+ |
| `/suppliers/:id` | 供应商详情 | 资料、采购历史、供应过的商品 | ADMIN+ |
| `/reports` | 经营报表 | 日期范围选择；销售趋势（日/月）、商品排行、客户排行、采购统计、毛利估算、库存统计；导出 CSV（专业版） | ADMIN+ |
| `/settings` | 设置 | 店铺资料、时区、负库存开关、个人资料/改密码 | 店铺设置：OWNER |
| `/settings/billing` | 套餐与续费 | 当前套餐、到期时间、剩余天数；各套餐权益对比（人民币/马币切换）；两种开通入口：**输入激活码**、**扫码付款**；付款单与开通记录 | OWNER |
| `/settings/billing/pay` | 扫码付款 | ① 选套餐与周期 ② 选付款方式（微信 / TNG·DuitNow）③ 显示金额、收款码、备注码（可复制）和倒计时 ④「我已付款」→ 上传截图、填交易单号 ⑤ 显示审核状态 | OWNER |
| `/admin` | 平台后台概览 | 商户总数、付费商户数、本月新增、即将到期 | 平台管理员 |
| `/admin/tenants` | 商户管理 | 搜索商户；查看套餐/到期；手动开通/延期（需填写原因） | 平台管理员 |
| `/admin/payments` | 付款审核 | 待审核列表（金额、币种、备注码、截图大图、交易单号）→ 通过 / 拒绝（填原因）；历史记录；退款作废 | 平台管理员 |
| `/admin/payment-settings` | 收款方式与价格 | 上传/更换收款码、启用停用；修改各币种价格 | 平台管理员 |
| `/admin/codes` | 激活码 | 创建批次（套餐、天数、是否首月体验、数量）→ 导出 CSV；查询/作废单个码 | 平台管理员 |
| `/admin/support` | 客服话术 | 话术增删改、启用/停用、👍👎 统计；未匹配问题列表（按次数排序）→ 一键转为新话术 | 平台管理员 |
| `*` | 404 | | |

### A.2.1 全局组件：客服窗口
- 所有页面（含登录、注册页）右下角悬浮 💬 按钮；手机端位于底部导航上方，不遮挡"＋开单"。
- 窗口内容：欢迎语 → 快捷问题按钮 → 输入框；回答下方显示 👍 / 👎 和可选的"去操作"按钮。
- 转人工卡片：显示客服微信号、[复制微信号] 按钮、备注格式提示。
- 套餐限制提示：当用户触发套餐限制（如免费版第 101 个商品）时，弹窗说明原因，并提供"查看套餐"和"问问客服"两个按钮。

### A.3 前端工程约定

- 每个功能一个目录（`features/products/…`），包含 api 调用、hooks、组件、页面。
- 所有接口类型（TypeScript）与后端 DTO 一一对应，放在 `features/*/types.ts`。
- 表格组件统一：桌面显示表格，手机自动切换为卡片列表。
- 所有表单用 Zod 校验，规则与后端一致；后端返回的字段错误显示在对应输入框下。
- 金额在前端只做显示格式化（`¥1,234.50`），不做最终计算。
- 每个页面都有：加载骨架屏、空状态（带"新增第一个商品"引导按钮）、错误状态（可重试）。

---

## Part B · 后端 RESTful API

### B.1 通用约定

- 前缀：`/api/v1`；JSON；时间 ISO-8601；金额以字符串传输（如 `"128.50"`）避免精度丢失。
- 认证：`Authorization: Bearer <accessToken>`。
- 列表分页：`?page=0&size=20&sort=createdAt,desc`，返回：
  ```json
  { "items": [...], "page": 0, "size": 20, "totalItems": 135, "totalPages": 7 }
  ```
- 错误：
  ```json
  { "code": "INSUFFICIENT_STOCK", "message": "商品库存不足", "details": { "productId": 12, "available": 3, "requested": 5 } }
  ```
- 状态码：200 成功、201 创建、204 删除、400 校验失败、401 未登录、403 无权限/套餐不支持、404 不存在（含跨商户访问）、409 冲突（SKU 重复、状态不允许、乐观锁冲突）。

### B.2 认证与商户

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | `{email, password, displayName}` → 创建用户，返回 token（尚无商户） |
| POST | `/auth/login` | `{email, password}` → `{accessToken, user, tenants[]}`；只有一个商户时自动选中 |
| POST | `/auth/refresh` | 用 HttpOnly Cookie 中的 refresh token 换新的 access token |
| POST | `/auth/logout` | 吊销 refresh token |
| GET | `/auth/me` | 当前用户、当前商户、角色、套餐 |
| PUT | `/auth/me` | 修改昵称/手机 |
| PUT | `/auth/me/password` | `{oldPassword, newPassword}` |
| POST | `/tenants` | `{name, businessType}` 创建商户，创建者成为 OWNER，返回绑定该商户的新 token |
| POST | `/tenants/{id}/switch` | 切换当前商户（校验成员关系），返回新 token |
| GET | `/tenant` | 当前商户资料 |
| PUT | `/tenant` | 修改店铺资料与设置（OWNER） |

### B.3 仪表盘

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/dashboard/summary` | `{productCount, totalStockQty, todaySales, monthSales, monthPurchases, inventoryValue, lowStockCount, todayOrderCount}` |
| GET | `/dashboard/sales-trend?days=30` | `[{date, amount, orderCount}]`，缺失日期补 0 |
| GET | `/dashboard/recent-orders?limit=5` | 最近销售订单 |

### B.4 分类与商品

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/categories` | 返回两级树 |
| POST | `/categories` | `{name, parentId?, sortOrder?}` |
| PUT | `/categories/{id}` | |
| DELETE | `/categories/{id}` | 有商品时 409 `CATEGORY_NOT_EMPTY` |
| GET | `/products?q=&categoryId=&status=&lowStock=true&page=&size=&sort=` | 搜索名称/SKU/条码 |
| GET | `/products/{id}` | |
| POST | `/products` | 可带 `initialStock`，会生成一条 STOCK_IN 流水 |
| PUT | `/products/{id}` | 不能直接改库存（库存字段被忽略，必须走库存接口）；带 `version` |
| DELETE | `/products/{id}` | 软删除 |
| POST | `/products/{id}/image` | multipart 上传，返回 `imageUrl` |
| GET | `/products/lookup?q=` | 开单时的轻量搜索（id、名称、SKU、价格、库存） |

### B.5 库存

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/inventory/stock-in` | `{productId, quantity, unitCost?, reason?}` |
| POST | `/inventory/stock-out` | `{productId, quantity, reason}` |
| POST | `/inventory/adjust` | `{productId, actualQty, reason}` —— 盘点：填实际数量，系统算差额 |
| GET | `/inventory/movements?productId=&type=&from=&to=&page=` | 流水 |
| GET | `/inventory/low-stock` | `stock_qty <= low_stock_threshold` 的商品 |
| GET | `/inventory/summary` | 总数量、总价值（按成本）、按分类汇总 |

### B.6 销售

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/sales-orders?q=&customerId=&status=&from=&to=&page=` | |
| GET | `/sales-orders/{id}` | 含明细 |
| POST | `/sales-orders` | `{customerId?, priceTier, orderDate?, items:[{productId, quantity, unitPrice?}], discountAmount?, notes?}`；未传 unitPrice 时按价格类型取商品价；**一个事务内**完成：取单号、校验库存、扣库存、写流水、保存订单 |
| POST | `/sales-orders/{id}/cancel` | `{reason?}`；恢复库存；已取消返回 409 |

### B.7 采购

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/purchase-orders?q=&supplierId=&status=&from=&to=&page=` | |
| GET | `/purchase-orders/{id}` | |
| POST | `/purchase-orders` | `{supplierId, expectedDate?, items:[{productId, quantity, unitCost}], notes?, submit?:bool}`；submit=true 直接为 ORDERED |
| PUT | `/purchase-orders/{id}` | 仅 DRAFT / ORDERED 可编辑 |
| POST | `/purchase-orders/{id}/submit` | DRAFT → ORDERED |
| POST | `/purchase-orders/{id}/receive` | ORDERED → RECEIVED：增加库存、写流水、更新加权平均成本 |
| POST | `/purchase-orders/{id}/cancel` | DRAFT/ORDERED → CANCELLED |
| DELETE | `/purchase-orders/{id}` | 仅 DRAFT 可删 |

### B.8 客户与供应商

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/customers?q=&type=&page=` | 列表带 `totalSpent`、`orderCount` |
| GET/POST/PUT/DELETE | `/customers[/{id}]` | 标准增删改查（删除为软删除） |
| GET | `/customers/{id}/orders?page=` | 历史订单 |
| GET | `/customers/{id}/stats` | 累计消费、订单数、最近下单时间 |
| GET | `/suppliers?q=&page=` | |
| GET/POST/PUT/DELETE | `/suppliers[/{id}]` | |
| GET | `/suppliers/{id}/purchase-orders?page=` | 采购历史 |
| GET | `/suppliers/{id}/products` | 从该供应商采购过的商品 |

### B.9 报表（日期参数 `from`、`to`，按商户时区）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/reports/sales?from=&to=&groupBy=day\|month` | 销售额、订单数、毛利 |
| GET | `/reports/top-products?from=&to=&limit=10&by=amount\|qty` | 商品销售排行 |
| GET | `/reports/top-customers?from=&to=&limit=10` | 客户消费排行 |
| GET | `/reports/purchases?from=&to=&groupBy=day\|month` | 采购成本统计 |
| GET | `/reports/gross-profit?from=&to=` | 销售额 − 销售成本（使用成本快照） |
| GET | `/reports/inventory` | 按分类的库存数量与价值 |
| GET | `/reports/stock-trend?productId=&from=&to=` | 库存变化趋势（由流水计算） |
| GET | `/reports/{type}/export?format=csv&…` | 导出（专业版） |

> 已取消的订单不计入任何统计。

### B.10 套餐与激活码

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/billing/plans` | 各套餐权益与价格（展示用，数据来自 `PlanPolicy`） |
| GET | `/billing/current` | 当前套餐、到期时间、是否可用首月体验、用量（商品数/账号数 vs 上限） |
| POST | `/billing/redeem` | `{code}` → 兑换激活码；失败返回 `CODE_INVALID` / `CODE_USED` / `CODE_EXPIRED` / `TRIAL_ALREADY_USED`；每商户每小时最多尝试 10 次（OWNER） |
| GET | `/billing/history` | 本商户的套餐变更记录 |
| GET | `/billing/prices?currency=CNY\|MYR` | 可购买的价格列表 |
| GET | `/billing/payment-methods` | 启用的收款方式（名称、币种、收款码、说明） |
| POST | `/billing/payment-orders` | `{planPriceId, paymentMethodId}` → 付款单（金额、备注码、收款码、过期时间）；首月体验已用过返回 409 `TRIAL_ALREADY_USED`；已有未完成付款单返回 409 |
| GET | `/billing/payment-orders`、`/billing/payment-orders/{id}` | 本商户付款单及状态 |
| POST | `/billing/payment-orders/{id}/proof` | multipart：截图（jpg/png，≤ 5MB）+ `transactionRef?` + `note?` → 状态变为 PENDING_REVIEW |
| POST | `/billing/payment-orders/{id}/cancel` | 未付款时取消 |
| GET | `/billing/payment-orders/{id}/proof-image` | 查看自己上传的截图（鉴权后返回，非公开链接） |

套餐不足时，业务接口返回 **403** `{ "code": "PLAN_LIMIT_REACHED", "details": { "limit": "products", "max": 100, "requiredPlan": "STANDARD" } }`，前端据此弹出升级提示。

### B.11 客服（无需登录，按 IP 限速）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/support/welcome` | 欢迎语 + 快捷问题列表 |
| POST | `/support/ask` | `{message}` → `{matched: true, faqId, answer, action?}` 或 `{matched: false, handoff: {wechatId, tip}}` |
| POST | `/support/faqs/{id}/feedback` | `{helpful: true/false}` |
| GET | `/support/contact` | 人工客服信息（微信号来自配置） |

匹配逻辑：将用户问题与每条话术的关键词计算命中分数，取最高分；分数低于阈值则视为未匹配，记录到 `support_unmatched_queries` 并返回转人工信息。

### B.12 平台管理（仅平台管理员，路径前缀 `/admin`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/stats` | 平台概况 |
| GET | `/admin/tenants?q=&plan=&expiringInDays=` | 商户列表（只含名称、套餐、到期、注册时间、成员数） |
| POST | `/admin/tenants/{id}/plan` | `{plan, expiresAt, note}` 手动调整，写入 plan_change_logs |
| POST | `/admin/code-batches` | `{name, plan, durationDays, isTrial, quantity, expiresAt?}` → 返回一次性 CSV 下载（含激活码原文，之后无法再次查看） |
| GET | `/admin/code-batches`、`/admin/code-batches/{id}/codes` | 批次与使用情况 |
| POST | `/admin/codes/void` | `{codeHint 或 code}` 作废（退款场景） |
| GET | `/admin/payment-orders?status=PENDING_REVIEW&currency=` | 付款单列表 |
| GET | `/admin/payment-orders/{id}/proof-image` | 查看付款截图 |
| POST | `/admin/payment-orders/{id}/approve` | 通过 → 开通/续期套餐（同一事务，带乐观锁防止重复开通） |
| POST | `/admin/payment-orders/{id}/reject` | `{reason}` |
| POST | `/admin/payment-orders/{id}/refund` | `{note}` 标记退款并回退套餐 |
| GET/POST/PUT | `/admin/payment-methods[/{id}]`、`POST /admin/payment-methods/{id}/qr` | 收款方式与收款码上传 |
| GET/PUT | `/admin/plan-prices[/{id}]` | 价格管理 |
| GET/POST/PUT/DELETE | `/admin/support/faqs[/{id}]` | 话术管理 |
| GET | `/admin/support/unmatched` | 未匹配问题；`POST /admin/support/unmatched/{id}/resolve` |

### B.13 权限矩阵（MVP）

| 操作 | OWNER | ADMIN | STAFF |
|---|:-:|:-:|:-:|
| 查看商品/库存/客户 | ✅ | ✅ | ✅ |
| 查看成本价、毛利、报表 | ✅ | ✅ | ❌ |
| 新增/编辑/删除商品、分类 | ✅ | ✅ | ❌ |
| 入库/出库/盘点 | ✅ | ✅ | ❌ |
| 创建销售订单 | ✅ | ✅ | ✅ |
| 取消销售订单 | ✅ | ✅ | ❌ |
| 采购、供应商 | ✅ | ✅ | ❌ |
| 店铺设置、员工、套餐、兑换激活码 | ✅ | ❌ | ❌ |
| 使用客服机器人 | ✅ | ✅ | ✅（未登录访客也可以） |

> 以上是商户内角色；能否使用某功能还要同时满足**套餐**条件（见 01 文档第 4 节）。平台管理员是独立身份，不属于任何商户角色。
