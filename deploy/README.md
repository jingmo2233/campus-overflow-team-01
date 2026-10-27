# 生产部署指南（deploy/）

`docker-compose.yml` 是**开发/演示环境**（随课程初始代码提供）；本目录新增的是**生产部署**所需文件，两者互不影响。

## 文件清单

| 文件 | 用途 |
| --- | --- |
| `docker-compose.prod.yml` | 生产编排：不暴露 3306/6379、restart 策略、日志轮转、内存限制、监控端口只绑 127.0.0.1 |
| `deploy.sh` | 一键部署 / 回滚 / 停止 / 看日志，自带健康检查与失败排查输出 |
| `.env.example` | 环境变量模板（真实密码放 `deploy/.env`，不入库） |

## 服务器首次部署

```bash
# 1) 拉代码并准备配置
git clone https://github.com/jingmo2233/campus-overflow-team-01.git && cd campus-overflow-team-01
cp deploy/.env.example deploy/.env
# 修改 .env 中全部 change_me_*，并生成加密密钥：
#   openssl rand -base64 32   → 填入 CO_FIELD_KEY

# 2) 一键部署（构建 + 启动 + 健康检查）
bash deploy/deploy.sh
```

部署成功后：

- 前端：`http://<服务器IP>:<WEB_PORT>`（默认 80）
- 接口文档：`http://<服务器IP>:<WEB_PORT>/swagger-ui.html`
- 健康检查：`http://<服务器IP>:<WEB_PORT>/actuator/health`

## 日常运维

```bash
bash deploy/deploy.sh logs      # 跟踪后端日志
bash deploy/deploy.sh rollback  # 回滚到上一次成功部署
bash deploy/deploy.sh down      # 停止（数据卷保留）

# 更新版本：git pull 后重新执行部署脚本
bash deploy/deploy.sh
```

## 开启监控（可选）

```bash
cd deploy && docker compose -f docker-compose.prod.yml --env-file .env --profile observability up -d
```

Prometheus（9090）与 Grafana（3000）只绑定服务器本机，本机访问需 SSH 隧道：

```bash
ssh -L 9090:127.0.0.1:9090 -L 3000:127.0.0.1:3000 <user>@<服务器IP>
```

## 安全约定

- 3306（MySQL）、6379（Redis）**不向宿主机暴露**，仅容器网络内互通
- 所有密码/密钥集中在 `deploy/.env`（git 已忽略），`grep change_me` 未清空时脚本拒绝启动
- `CO_FIELD_KEY` 首次生成后不可更换（否则已加密字段无法解密），请离线备份
- 走 HTTPS 发布时把 `.env` 中 `COOKIE_SECURE` 改为 `true`
- 生产 profile 为 `prod`（不含 demo 演示数据）
