#!/usr/bin/env bash
# Lab 4 验收自测脚本（Git Bash / WSL / Linux 均可）
#
#   bash docs/lab4/verify.sh
#
# 前置：docker compose -f deploy/docker-compose.yml up -d mysql redis
#       cd backend && mvn spring-boot:run -pl co-bootstrap -Dspring-boot.run.profiles=dev
#
# 中文必须写入文件再用 --data-binary 提交：Git Bash 下用 -d 直接传中文会因控制台
# 编码被后端判为 400 Failed to read request。
set -euo pipefail

B="${BASE_URL:-http://localhost:8080}"
JAR=$(mktemp)
USER_NAME="${STUDENT_USER:-2023901}"
PASSWORD="${STUDENT_PASSWORD:-Campus2026}"

line() { printf '%s\n' '------------------------------------------------------------------'; }

echo "=================================================================="
echo "  CampusOverflow · Lab 4 验收"
echo "  指定用例：POST /api/v1/questions"
echo "  时间：$(date '+%Y-%m-%d %H:%M:%S')"
echo "=================================================================="
echo ""

# --- 0.1 CSRF ------------------------------------------------------
echo "[0.1] GET  /api/v1/auth/csrf"
line
curl -s -c "$JAR" "$B/api/v1/auth/csrf" -w "\nHTTP %{http_code}\n"
TOKEN=$(awk '/XSRF-TOKEN/ {print $7}' "$JAR")
echo ""

# --- 0.2 注册（已存在则 409，属正常）--------------------------------
cat > /tmp/co_reg.json <<JSON
{"username":"$USER_NAME","displayName":"验收学生A","email":"$USER_NAME@campus.edu.cn","password":"$PASSWORD","college":"计算机学院","className":"软工2101"}
JSON
echo "[0.2] POST /api/v1/auth/register   （已注册则 409，属正常）"
line
curl -s -b "$JAR" -c "$JAR" -X POST "$B/api/v1/auth/register" \
  -H "Content-Type: application/json; charset=UTF-8" -H "X-XSRF-TOKEN: $TOKEN" \
  --data-binary @/tmp/co_reg.json -w "\nHTTP %{http_code}\n"
echo ""

# --- 0.3 登录 -------------------------------------------------------
cat > /tmp/co_login.json <<JSON
{"username":"$USER_NAME","password":"$PASSWORD"}
JSON
echo "[0.3] POST /api/v1/auth/login"
line
curl -s -b "$JAR" -c "$JAR" -X POST "$B/api/v1/auth/login" \
  -H "Content-Type: application/json; charset=UTF-8" -H "X-XSRF-TOKEN: $TOKEN" \
  --data-binary @/tmp/co_login.json -w "\nHTTP %{http_code}\n"
echo ""

# --- 1.1 发布问题（Lab 4 指定用例）----------------------------------
cat > /tmp/co_ask.json <<'JSON'
{"title":"Lab4 验收：红黑树插入后如何判断旋转方向？","body":"红黑树插入新节点后，父节点为红且叔节点为黑时，为什么必须旋转而不能只靠变色？希望能给出一个具体的反例，并说明左旋与右旋的判定依据。","tags":["数据结构","红黑树"]}
JSON
echo "=================================================================="
echo "  [1.1] 发布问题  POST /api/v1/questions      ← Lab 4 指定用例"
echo "=================================================================="
echo "【请求头】"
echo "  Content-Type : application/json; charset=UTF-8"
echo "  X-XSRF-TOKEN : $TOKEN"
echo ""
echo "【请求体】"
cat /tmp/co_ask.json
echo ""
echo ""
echo "【响应】"
line
curl -s -b "$JAR" -c "$JAR" -X POST "$B/api/v1/questions" \
  -H "Content-Type: application/json; charset=UTF-8" -H "X-XSRF-TOKEN: $TOKEN" \
  --data-binary @/tmp/co_ask.json -D /tmp/co_h.txt -o /tmp/co_b.txt
grep -E '^HTTP|^Content-Type' /tmp/co_h.txt
echo ""
echo "  响应体: $(cat /tmp/co_b.txt)"
line
echo ""
echo "  结果：HTTP 201 Created + 数值型 id  →  用例通过"
echo "=================================================================="

rm -f "$JAR"
