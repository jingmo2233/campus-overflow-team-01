# Sprint 0 环境验证记录（T0-03）

> 目的：证明每位成员都能在本地从零启动系统、跑通测试、打开前端与 Swagger。
> 本页记录 `xy1897` 的实测结果；其他成员按同一脚本执行后补一行即可。

## 1. 版本矩阵

| 组件 | 指导书要求 | 本机实测 | 状态 |
| --- | --- | --- | --- |
| 操作系统 | Windows / macOS / Linux | Windows 11 家庭版 10.0.26200 | ✅ |
| JDK | 21 | Oracle JDK 21.0.1 LTS | ✅ |
| Maven | 3.9+ | 3.9.16 | ✅ |
| Node.js | 20 LTS | 24.18.0（Vite 5 构建通过；CI 仍用 20，建议本地装 20 对齐） | ⚠️ 可用 |
| npm | 10+ | 11.16.0 | ✅ |
| Docker | 24+ | 29.9.2 | ✅ |
| Docker Compose | v2+ | v5.6.1 | ✅ |
| MySQL | 8.0.39（compose） | `mysql:8.0.39` 容器，健康 | ✅ |
| Redis | 7（compose） | `redis:7-alpine` 容器，健康 | ✅ |
| Git | 2.x | 2.54.0.windows.1 | ✅ |

> 本机 3306 已被 Windows 服务 `MySQL80` 占用，因此 compose 的 MySQL 映射到 **3307**：
> `DB_HOST_PORT=3307 docker compose ... up -d mysql redis`，后端以 `DB_PORT=3307` 启动。
> `deploy/docker-compose.yml` 已把宿主端口改为 `${DB_HOST_PORT:-3306}`，默认行为不变。

## 2. 一键验证脚本

```bash
# 1) 中间件（本机 3306 被占用时加 DB_HOST_PORT=3307）
DB_HOST_PORT=3307 docker compose -f deploy/docker-compose.yml up -d mysql redis
docker ps --format '{{.Names}} {{.Status}}'

# 2) 后端：Sprint 0 范围测试（必须绿）
cd backend
mvn -B test -pl co-shared-kernel,co-identity/co-identity-domain,co-qa/co-qa-domain,co-qa/co-qa-application -am

# 3) 全量测试：S0 预期红（Reputation/Discovery 的 TODO）
mvn -B test || true

# 4) 启动后端（dev profile，不要加 demo）
DB_PORT=3307 mvn spring-boot:run -pl co-bootstrap -Dspring-boot.run.profiles=dev
# http://localhost:8080/actuator/health  → {"status":"UP"}
# http://localhost:8080/swagger-ui.html

# 5) 前端
cd ../frontend
npm install
npm run typecheck && npm run build
npm run dev     # http://localhost:5173
```

PowerShell 版本：

```powershell
$env:DB_HOST_PORT = '3307'
docker compose -f deploy/docker-compose.yml up -d mysql redis
$env:DB_PORT = '3307'
mvn -B spring-boot:run -pl co-bootstrap "-Dspring-boot.run.profiles=dev"
```

## 3. 实测结果（2026-10-26，xy1897）

### 3.1 中间件

```
co-mysql | Up (healthy) | 0.0.0.0:3307->3306/tcp
co-redis | Up (healthy) | 0.0.0.0:6379->6379/tcp
```

### 3.2 后端测试

Sprint 0 范围：

```
mvn -B test -pl co-shared-kernel,co-identity/co-identity-domain,co-qa/co-qa-domain,co-qa/co-qa-application -am
→ BUILD SUCCESS
  共享内核 7、Identity 9、Q&A domain 42、Q&A application 4，共 62 个测试全绿
```

全量（对照组，预期红）：

```
mvn -B test
→ co-reputation-domain FAILURE
  Tests run: 15, Failures: 1, Errors: 13
  根因：UnsupportedOperationException: TODO(S3)…
  （Discovery 尚未执行；Q&A 与 Identity 已绿）
```

ArchUnit：

```
mvn -B test -pl co-bootstrap -am -Dtest=ArchitectureTest \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false
→ BUILD SUCCESS（FF-1～FF-8）
```

### 3.3 前端

```
npm run typecheck  → 0 error
npm run build      → ✓ 42 modules transformed，dist 生成成功
npm run dev        → http://localhost:5173
```

### 3.4 接口文档

- `/swagger-ui.html` 可访问，Identity / Q&A / Reputation / Discovery 的控制器均出现在分组中。
- `/actuator/health` 返回 `UP`（见 `docs/sprint0/verification.md` 的实测记录）。

## 4. 成员确认

| 成员 | JDK | Maven | Docker | Node | 启动成功 | 日期 |
| --- | --- | --- | --- | --- | --- | --- |
| @xy1897 | 21.0.1 | 3.9.16 | 29.9.2 | 24.18.0 | ✅ 后端 + 前端 + Swagger | 2026-10-26 |
| @jingmo2233 |  |  |  |  |  |  |
| @yttt697 |  |  |  |  |  |  |
| @fxs0917 |  |  |  |  |  |  |

## 5. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `ports are not available: 3306` | 本机 MySQL80 占用 3306 | 用 `DB_HOST_PORT=3307` 起容器，后端 `DB_PORT=3307` |
| `docker` 命令存在但连不上 daemon | Docker Desktop 未启动 | 启动 Docker Desktop，等鲸鱼图标变绿；`docker info` 验证 |
| `mvn test` 全红 | S0 未实现的 Reputation/Discovery | 属预期；看 `docs/学生任务清单.md` |
| 前端 `npm run dev` 打不开 API | 后端未启动或端口不是 8080 | 先起后端，确认 Vite 代理目标 |
| 登录后改角色不生效 | 会话缓存了 Actor | 退出重新登录 |
