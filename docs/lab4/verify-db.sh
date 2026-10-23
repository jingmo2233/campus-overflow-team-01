#!/usr/bin/env bash
# Lab 4 落库验证（Git Bash / WSL / Linux 均可）
#
#   bash docs/lab4/verify-db.sh
#
# 前置：docker compose -f deploy/docker-compose.yml up -d mysql redis
#
# 必须加 --default-character-set=utf8mb4，否则中文在 Windows 控制台显示成 ?
# （只是显示问题，不是存储问题）。
set -euo pipefail

CONTAINER="${MYSQL_CONTAINER:-co-mysql}"
DB_USER="${DB_USER:-campus}"
DB_PASSWORD="${DB_PASSWORD:-campus_dev_pwd}"
DB_NAME="${DB_NAME:-campus_overflow}"

echo "=================================================================="
echo "  CampusOverflow · Lab 4 落库验证"
echo "  时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo "=================================================================="
echo ""
echo "【最近发布的问题】标签在 qa_question_tag 关联表，qa_question 无 tags 列"
echo "------------------------------------------------------------------"
docker exec "$CONTAINER" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  --default-character-set=utf8mb4 -e "
SELECT q.id, q.title, q.answer_count, q.created_at, GROUP_CONCAT(qt.tag) AS tags
FROM qa_question q
LEFT JOIN qa_question_tag qt ON qt.question_id = q.id
GROUP BY q.id ORDER BY q.id DESC LIMIT 1\G" 2>/dev/null

echo ""
echo "【对应的回答】"
echo "------------------------------------------------------------------"
docker exec "$CONTAINER" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  --default-character-set=utf8mb4 -e "
SELECT id, question_id, author_id, LEFT(body, 30) AS body, created_at
FROM qa_answer ORDER BY id DESC LIMIT 1\G" 2>/dev/null

echo ""
echo "【utf8mb4 存储校验】bytes > chars 说明是多字节存储；汉字 HEX 为 3 字节（E4-E9 开头）"
echo "------------------------------------------------------------------"
docker exec "$CONTAINER" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  --default-character-set=utf8mb4 -N -e "
SELECT CONCAT('id=', id,
              '  chars=', CHAR_LENGTH(title),
              '  bytes=', LENGTH(title),
              '  首个汉字=', SUBSTRING(title, -2, 1),
              '  HEX=', HEX(SUBSTRING(title, -2, 1)))
FROM qa_question ORDER BY id DESC LIMIT 1;" 2>/dev/null

echo "=================================================================="
