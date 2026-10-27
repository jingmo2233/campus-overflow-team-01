#!/usr/bin/env bash
# CampusOverflow 一键部署脚本（在部署机仓库根目录执行）
# 用法：
#   bash deploy/deploy.sh            # 构建并启动 + 健康检查
#   bash deploy/deploy.sh rollback   # 回滚到上一次成功部署的镜像
#   bash deploy/deploy.sh down       # 停止并移除容器（数据卷保留）
#   bash deploy/deploy.sh logs       # 跟踪后端日志
set -euo pipefail

cd "$(dirname "$0")"           # 进入 deploy/
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env"
BACKEND=co-backend-prod

log()  { printf '\033[1;34m[deploy]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[deploy] 失败:\033[0m %s\n' "$*" >&2; exit 1; }

# ── 前置检查 ──
preflight() {
  command -v docker >/dev/null || fail "未检测到 docker，请先安装 Docker Engine"
  docker info >/dev/null 2>&1 || fail "docker 守护进程不可用（当前用户是否在 docker 组？）"
  [ -f .env ] || fail "缺少 deploy/.env，请先执行: cp deploy/.env.example deploy/.env 并填写密码"
  grep -q "change_me" .env && fail ".env 中仍存在 change_me 占位值，请先修改全部密码/密钥"
}

deploy() {
  preflight
  log "校验 compose 配置..."
  $COMPOSE config -q || fail "docker-compose.prod.yml 或 .env 配置有误"

  log "记录当前镜像（用于回滚）..."
  docker image inspect campus-overflow/backend:previous >/dev/null 2>&1 \
    && docker tag campus-overflow/backend:previous campus-overflow/backend:rollback || true
  docker image inspect campus-overflow/backend:latest >/dev/null 2>&1 \
    && docker tag campus-overflow/backend:latest campus-overflow/backend:previous || true

  log "构建镜像（首次构建约 3-10 分钟）..."
  $COMPOSE build

  log "启动服务..."
  $COMPOSE up -d

  log "等待后端就绪（最多 300 秒）..."
  if wait_healthy; then
    log "部署完成 ✔  前端: http://<服务器IP>:$(grep -oP '^WEB_PORT=\K.*' .env 2>/dev/null || echo 80)"
    log "接口文档: http://<服务器IP>:<WEB_PORT>/swagger-ui.html"
  else
    log "后端健康检查超时，输出最近日志用于排查："
    docker logs --tail 50 "$BACKEND" || true
    fail "健康检查未通过，请根据上方日志排查；镜像已保留，可执行 bash deploy/deploy.sh rollback 回滚"
  fi
}

wait_healthy() {
  for _ in $(seq 1 30); do
    status=$(docker inspect -f '{{.State.Health.Status}}' "$BACKEND" 2>/dev/null || echo "missing")
    [ "$status" = "healthy" ] && return 0
    sleep 10
  done
  return 1
}

rollback() {
  docker image inspect campus-overflow/backend:rollback >/dev/null 2>&1 \
    || fail "没有可回滚的镜像（rollback 标签不存在）"
  docker tag campus-overflow/backend:rollback campus-overflow/backend:latest
  $COMPOSE up -d --no-deps backend web
  wait_healthy && log "已回滚并恢复健康 ✔" || fail "回滚后健康检查仍未通过，请查看 docker logs $BACKEND"
}

case "${1:-deploy}" in
  deploy)  deploy ;;
  rollback) preflight; rollback ;;
  down)    $COMPOSE down ;;
  logs)    docker logs -f --tail 100 "$BACKEND" ;;
  *)       echo "用法: bash deploy/deploy.sh [deploy|rollback|down|logs]"; exit 1 ;;
esac
