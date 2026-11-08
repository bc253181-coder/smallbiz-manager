# SmallBiz Manager

中小商户在线经营管理系统（多商户 SaaS）：商品、库存、采购、销售、客户、供应商、报表。

- 设计文档：[docs/](docs/)
- 后端：Spring Boot 4.1 · Java 25 · MySQL 8.4 · Flyway · JWT
- 前端：React 19 · TypeScript · Vite · Tailwind CSS 4 · TanStack Query

## 本地运行

**前置条件**：JDK 21+、Node.js 20+、MySQL 8.4（已创建 `smallbiz` 与 `smallbiz_test` 库）。
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
```

## 开发进度

见 [docs/04-structure-and-roadmap.md](docs/04-structure-and-roadmap.md)。
