#!/usr/bin/env bash
# 每日备份：数据库 + 上传文件（商品图片、付款截图）。保留最近 14 天。
# 用法：在 deploy 目录执行 ./backup.sh；建议用 crontab 每天凌晨执行（见 docs/05-deployment.md）
set -euo pipefail
cd "$(dirname "$0")"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

docker compose exec -T mysql sh -c \
  'exec mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF -uroot -p"$MYSQL_ROOT_PASSWORD" smallbiz' \
  | gzip > "$BACKUP_DIR/db-$STAMP.sql.gz"

docker compose exec -T backend tar czf - -C /data . > "$BACKUP_DIR/files-$STAMP.tar.gz"

# 校验备份不是空文件
for f in "$BACKUP_DIR/db-$STAMP.sql.gz" "$BACKUP_DIR/files-$STAMP.tar.gz"; do
  if [ ! -s "$f" ]; then echo "备份失败：$f 为空" >&2; exit 1; fi
done
gzip -t "$BACKUP_DIR/db-$STAMP.sql.gz"

find "$BACKUP_DIR" -name 'db-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
find "$BACKUP_DIR" -name 'files-*.tar.gz' -mtime +"$KEEP_DAYS" -delete
echo "备份完成：$BACKUP_DIR/*-$STAMP.*"