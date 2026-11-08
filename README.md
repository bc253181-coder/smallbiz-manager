# SmallBiz Manager

中小商户在线经营管理系统（多商户 SaaS）：商品、库存、采购、销售、客户、供应商、报表。

- 设计文档：[docs/](docs/)
- 后端：Spring Boot 4.1 · Java 25 · MySQL 8.4 · Flyway · JWT
- 前端：React 19 · TypeScript · Vite · Tailwind CSS 4 · TanStack Query

## 本地运行

**前置条件**：JDK 25、Node.js 22+、MySQL 8.4（已创建 `smallbiz` 与 `smallbiz_test` 库）。
复制 `.env.example` 为 `.env.dev` 并填写数据库账号和 `JWT_SECRET`。

```bash
# 后端（http://localhost:8080）
cd backend
./mvnw spring-boot:run      # Windows: mvnw.cmd spring-boot:run
```

```bash
# 前端（http://localhost:5173，/api 自动转发到后端）
cd frontend
npm install
npm run dev
```

## 测试

```bash
cd backend
./mvnw test                 # 使用 smallbiz_test 库，每次运行前自动清空重建
```

```bash
cd frontend
npm run build               # 类型检查 + 生产构建
npm run lint
```

端到端测试（Playwright，使用系统自带的 Edge；需先启动上面的后端和前端）：

```bash
cd e2e
npm install
npm test                    # 完整商户流程（桌面 + 手机）+ 全站 375/768/1280 布局检查
```

e2e 会读取 `.env.dev` 中的 `DEV_TEST_EMAIL` / `DEV_TEST_PASSWORD`（需在 `PLATFORM_ADMIN_EMAILS` 中）生成激活码；每次运行会在开发库中新建测试账号和店铺（邮箱以 `@e2e.test` 结尾）。

## 部署

Docker Compose（Caddy 自动 HTTPS + Nginx + Spring Boot + MySQL）与每日备份，见 [docs/05-deployment.md](docs/05-deployment.md)。

## 开发进度

见 [docs/04-structure-and-roadmap.md](docs/04-structure-and-roadmap.md)。
