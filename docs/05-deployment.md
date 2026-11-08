# 05 · 部署与运维

## 1. 架构

```
浏览器 ──HTTPS──▶ Caddy（自动申请证书，80/443）
                    └─▶ web：Nginx（前端静态文件；/api、/uploads 转发）
                           └─▶ backend：Spring Boot（8080，仅内网）
                                  ├─▶ mysql：MySQL 8.4（仅内网，数据卷 mysql-data）
                                  └─ 数据卷 app-data：/data/uploads（商品图片、收款码）、/data/private（付款截图）
```

只有 Caddy 对外开放端口；后端、数据库不暴露到公网，`/actuator` 也不会被 Nginx 转发。

相关文件：`deploy/docker-compose.yml`、`deploy/Caddyfile`、`deploy/.env.example`、`deploy/backup.sh`、`backend/Dockerfile`、`frontend/Dockerfile`、`frontend/nginx/`。

## 2. 服务器要求

- 1 台云服务器：2 核 CPU / 2GB 内存起步（推荐 4GB），40GB 硬盘，Ubuntu 24.04
- 已安装 Docker 和 Docker Compose 插件（`docker compose version` 能输出版本）
- 一个域名，A 记录解析到服务器公网 IP；安全组/防火墙放行 80 和 443
- **服务器在中国大陆**：域名必须先完成 ICP 备案，否则 80/443 端口会被拦截。**服务器在香港、新加坡、马来西亚等地**：不需要备案，但中国大陆访问速度可能较慢。

## 3. 首次部署

```bash
git clone <你的仓库地址> smallbiz-manager
cd smallbiz-manager/deploy
cp .env.example .env
```

编辑 `deploy/.env`：

| 变量 | 说明 |
|---|---|
| `DOMAIN` | 网站域名，例如 `app.example.com` |
| `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD` | `openssl rand -base64 24` 生成，两个不同 |
| `JWT_SECRET` | `openssl rand -base64 48` 生成；**更换后所有用户需要重新登录** |
| `PLATFORM_ADMIN_EMAILS` | 平台管理员邮箱，用它注册账号后即可进入 `/admin` |
| `SUPPORT_WECHAT_ID` | 人工客服微信号 |

```bash
chmod 600 .env
docker compose up -d --build
docker compose ps            # 等待 backend 显示 healthy（首次约 1–2 分钟）
docker compose logs -f backend
```

打开 `https://你的域名`，用 `PLATFORM_ADMIN_EMAILS` 中的邮箱注册，然后：
1. 进入「平台后台 → 价格与收款」，确认价格和两个收款码正确（首次启动会自动导入 `assets/payment/` 下的图片）
2. 在「客服话术」中检查内置话术
3. 自己注册一个测试店铺，走一遍首月体验付款和审核流程

## 4. 发布新版本

```bash
cd smallbiz-manager
./deploy/backup.sh                   # 先备份
git pull
cd deploy && docker compose up -d --build
```

数据库结构变更由 Flyway 在后端启动时自动执行（生产环境禁止 `clean`）。前端 `index.html` 不缓存，用户刷新即可看到新版本。

## 5. 备份与恢复

**自动备份**（每天 03:30，保留 14 天）：

```bash
chmod +x /path/to/smallbiz-manager/deploy/backup.sh
crontab -e
# 添加一行：
30 3 * * * /path/to/smallbiz-manager/deploy/backup.sh >> /var/log/smallbiz-backup.log 2>&1
```

备份文件在 `deploy/backups/`：`db-*.sql.gz`（数据库）和 `files-*.tar.gz`（商品图片、付款截图）。
**备份放在同一台服务器上不够安全**，请定期下载到本地，或同步到对象存储（如阿里云 OSS、腾讯云 COS），并确保备份文件不公开。

**恢复数据库**（会覆盖现有数据，先停后端）：

```bash
cd deploy
docker compose stop backend
gunzip -c backups/db-YYYYMMDD-HHMMSS.sql.gz | \
  docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" smallbiz'
docker compose start backend
```

**恢复上传文件**：

```bash
docker compose exec -T backend sh -c 'rm -rf /data/uploads /data/private'
docker compose exec -T backend tar xzf - -C /data < backups/files-YYYYMMDD-HHMMSS.tar.gz
```

建议每月在另一台机器上实际恢复一次，确认备份可用。

## 6. 安全检查清单（上线前）

- [ ] `deploy/.env` 权限为 600，且没有提交到 Git
- [ ] 浏览器访问是 HTTPS，地址栏有锁；`http://` 自动跳转到 `https://`
- [ ] 登录后 Cookie `sb_refresh` 带有 `Secure`、`HttpOnly`
- [ ] `https://域名/actuator/health` 返回首页（说明未对外暴露）
- [ ] 服务器只开放 22、80、443；SSH 使用密钥登录，禁用密码登录
- [ ] 备份脚本已加入 crontab，并成功执行过一次
- [ ] 平台管理员邮箱只包含你自己
- [ ] 用户协议、隐私政策已按实际运营情况审阅（运营主体、退款规则、服务器所在地）
- [ ] 正式运营建议把个人收款码换成经营收款码（见 01 文档 4.4）

## 7. 常用命令

```bash
docker compose ps                     # 查看状态
docker compose logs -f backend        # 后端日志
docker compose restart backend        # 重启后端
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" smallbiz'   # 进入数据库
docker system df                      # 查看磁盘占用
```

## 8. 本地验证情况

- 已在本机以 `SPRING_PROFILES_ACTIVE=prod` 运行打包后的 JAR，验证：健康检查正常、`/actuator/env` 等未暴露、登录 Cookie 带 `Secure`、经过代理时按 `X-Forwarded-For` 中的真实 IP 限速。
- 本机未安装 Docker，**Dockerfile 与 docker-compose 尚未实际构建运行过**，首次部署时请按第 3 节检查各容器状态与日志。
