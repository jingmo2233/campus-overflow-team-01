# Lab 4 交付物：后端工程骨架与领域代码

> 对应 PDF Lab 4（第 7 周，Sprint 2）。本文件把指导书的 4 项提交物逐条映射到仓库中的实际产物。

| Lab 4 要求 | 本仓库产物 | 状态 |
| --- | --- | --- |
| 可运行的多模块 Maven 工程（`mvn clean install` 全绿） | 27 模块骨架 + Q&A 领域/应用层实现 | ⚠️ 编译与安装通过（57s）；**全仓测试要等 S3 的 Reputation/Discovery TODO 清空才会全绿**，见第 2 节 |
| 通过 `application.yml` 启动并连上数据库 | `co-bootstrap/src/main/resources/application.yml`（dev 默认 profile） | ✅ **已端到端实测**，证据见第 5 节 |
| `ADR-002-package-structure` 记录分模块决策 | [`docs/adr/009-多模块工程结构.md`](../adr/009-多模块工程结构.md) | ✅ 编号说明见该文档开头（002 已被发件箱占用） |
| Postman Collection：`POST /api/v1/questions` 成功用例 | [`docs/postman/CampusOverflow.postman_collection.json`](../postman/CampusOverflow.postman_collection.json) | ✅ 6 个文件夹 / 23 个请求，含正向、投票切换用户与错误码负向用例 |

---

## 1. 起环境

```powershell
# 1) 中间件（MySQL 8 带 ngram 分词配置 + Redis 7）
docker compose -f deploy/docker-compose.yml up -d mysql redis

# 2) 后端（dev profile，默认 8080）；首次启动前先 mvn -B clean install -DskipTests
cd backend
mvn spring-boot:run -pl co-bootstrap -Dspring-boot.run.profiles=dev

# 3) 健康检查
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

首次启动会自动执行 5 套 Flyway 迁移（platform / identity / qa / reputation / discovery）。

> **不要用 `demo` profile**：`DemoDataSeeder` 会调用 S3 的 `BountyService.open`，在 S3 完成前会启动失败。
> `deploy/docker-compose.yml` 默认的 `prod,demo` 也因此在现阶段不可用，Lab 8 之前需要把它改成 `prod`。

## 2. 构建验证

```powershell
cd backend

# 编译 + 安装（当前阶段跳过测试，因为 S3 的测试还是红的）
mvn -B clean install -DskipTests

# Q&A 领域测试（42 个，秒级）
mvn -B test -pl co-qa/co-qa-domain -am

# Q&A 应用层测试（4 个）
mvn -B test -pl co-qa/co-qa-application -am

# 架构约束 FF-1 ~ FF-6
mvn -B test -pl co-bootstrap -am `
  '-Dtest=ArchitectureTest' '-DfailIfNoTests=false' '-Dsurefire.failIfNoSpecifiedTests=false'
```

实测通过的测试（S2 范围）：`co-shared-kernel` 7 个、`co-qa-domain` **42 个**、`co-qa-application` 4 个、
`ArchitectureTest`（FF-1 ~ FF-6）1 个，全部 BUILD SUCCESS。

等 S3 结束后，把第一条命令的 `-DskipTests` 去掉即可得到 Lab 4 要求的「全绿」。

> **待 SM 决策**：在 S3 完成前，全仓 `mvn test` 必然因 Reputation/Discovery 的 `TODO(S3)` 而失败，
> 导致每个 PR 的 CI 都挂红叉。建议把 CI 的测试步骤临时限定到已完成模块，S3 合并后再放开：
> `mvn -B test -pl co-shared-kernel,co-qa/co-qa-domain,co-qa/co-qa-application -am`

## 3. 跑 Postman 集合

1. Postman → Import → 选择 `docs/postman/CampusOverflow.postman_collection.json`
2. 打开集合变量，确认 `baseUrl = http://localhost:8080`（端口不同就改这里）
3. 按文件夹顺序执行：`00 认证` → `01 问题` → `02 回答与评论` → `03 投票` → `04 负向用例` → `05 清理`

**关键设计**

- 写请求的 CSRF：`0.1` 把响应体里的 token 存进集合变量 `csrfToken`，集合级 pre-request 再把它写入
  `X-XSRF-TOKEN` 请求头。**不要改成 `pm.cookies.get()`** —— 它在 pre-request 阶段读不到 Cookie jar，
  会让所有写请求 403（这是本集合早期版本的真实缺陷）。
- 投票不能投给自己，所以 `03` 会注册并登录第二个学生（学生 B），跑完再切回学生 A。
- `questionId` / `answerId` / `commentId` 由前置请求自动写入变量，不要手工填写。

**Lab 4 要求的那条用例**：`01 · 问题 / 1.1 发布问题（POST /api/v1/questions）`
断言：HTTP 201 + 响应体含数值型 `id`，并把 id 写入 `{{questionId}}`，供后续详情/回答/投票复用。
该路径已用 curl 实测返回 201，见第 5 节。

集合内 23 个请求的方法与路径已与各 `*Controller` 的映射逐条核对一致
（`AuthController` / `QuestionController` / `AnswerController` / `QaCommentController` / `VoteController` / `TagController`）。

## 4. 数据库落库验证（截图用）

> 容器里查询时加上 `--default-character-set=utf8mb4`，否则中文在 Windows 控制台显示成 `?`
> （只是显示问题，不是存储问题）：
> `docker exec co-mysql mysql -ucampus -pcampus_dev_pwd campus_overflow --default-character-set=utf8mb4 -e "..."`

```sql
-- 最近发布的问题（标签在 qa_question_tag 关联表里，qa_question 本身没有 tags 列）
SELECT q.id, q.title, q.answer_count, q.created_at, GROUP_CONCAT(qt.tag) AS tags
FROM qa_question q
LEFT JOIN qa_question_tag qt ON qt.question_id = q.id
GROUP BY q.id ORDER BY q.id DESC LIMIT 1;

-- 对应的回答
SELECT id, question_id, author_id, LEFT(body, 30) AS body, created_at
FROM qa_answer ORDER BY id DESC LIMIT 1;
```

建议 Lab 4 提交的截图：`actuator/health` 返回 UP、Postman 1.1 的 201 响应、上面两条 SQL 的结果。

## 5. 端到端实测记录（2026-10-22）

在 Windows 11 家庭版 + Docker Desktop 29.9.2（WSL 2 后端）上完整跑通，逐项结果：

| 环节 | 命令 / 请求 | 实测结果 |
| --- | --- | --- |
| 中间件 | `docker compose -f deploy/docker-compose.yml up -d mysql redis` | `co-mysql`、`co-redis` 均 **healthy** |
| 后端构建 | `mvn -B clean install -DskipTests` | 27 模块 **BUILD SUCCESS**，57.5s |
| Flyway | 启动时自动执行 | **Successfully applied 5 migrations**，版本 `v202609010400` |
| 健康检查 | `GET /actuator/health` | `{"status":"UP","groups":["liveness","readiness"]}` |
| 注册 | `POST /api/v1/auth/register` | **201** `{"id":1}` |
| 登录 | `POST /api/v1/auth/login` | **200**，返回 `MeView`，会话 Cookie + CSRF 正常 |
| **发布问题** | `POST /api/v1/questions` | **201** `{"id":1}` ← **Lab 4 指定用例** |
| 列表 | `GET /api/v1/questions?page=1` | 200，`total=1`，标签与作者正确 |
| 详情 | `GET /api/v1/questions/1` | 200，`bodyHtml` 已净化，`permissions` 四个字段正确 |
| 落库 | `SELECT ... FROM qa_question` | 1 行；`CHAR_LENGTH=17 / LENGTH=51`，`HEX(LEFT(title,2))=E7BAA2E9BB91`（「红黑」），utf8mb4 存储无误 |
| 前端 | `pnpm dev` | 首页 **200**；经 Vite `/api` 代理取到后端数据，同源下 Cookie 与 CSRF 正常 |

**命令行自测脚本**：[`docs/lab4/verify.sh`](verify.sh)，一条命令跑完 `0.1 → 0.2 → 0.3 → 1.1`：

```bash
bash docs/lab4/verify.sh
```

输出已排版，可直接截图作为交付证据（最后一段就是指定用例的请求头、请求体与 `HTTP/1.1 201`）。
不想装 Postman、或 Postman 的 Cookie/脚本出问题时，用它即可 —— 验收看的是
`POST /api/v1/questions` 返回 201 这个事实，不限定工具。

> 中文必须写进文件再用 `--data-binary` 提交。Git Bash 下用 `-d` 直接传中文会因控制台编码
> 被后端判为 `400 Failed to read request`，脚本里已经处理好。

> 启动后每隔一段时间日志会出现一次 `UnsupportedOperationException: TODO(S3)：BountyService.expireDue 尚未实现`。
> 这是 S3 的定时任务（Dev-C 范围），**不影响 Q&A 链路**，S3 完成后自然消失。

## 6. 当前覆盖范围与限制

| 能力 | 状态 |
| --- | --- |
| 认证（注册/登录/会话/CSRF） | ✅ 可用 |
| 问题（发布/列表/详情/编辑/删除/标签） | ✅ 可用 |
| 回答（提交/编辑/删除/采纳/教师认证） | ✅ 可用（教师认证需教师账号） |
| 投票（+1/-1/撤销/改投增量） | ✅ 可用 |
| 评论（一级评论 + 二级回复） | ✅ 可用 |
| 声誉、徽章、悬赏、排行榜 | ⏳ S3（Dev-C） |
| 搜索、相关推荐、通知 | ⏳ S3（Dev-D） |

**环境依赖**：启动后端、跑 `CampusOverflowFlowIT`（Testcontainers）、Lab 7 集成测试与 Lab 8 部署
全都依赖 Docker。建议组内每台机器都按第 7 节装好。

## 7. 环境搭建踩坑记录（Windows 家庭版）

按这个顺序排查，三个坑都遇到过：

**① Docker 报 `500 Internal Server Error ... /_ping`**

症状是 `wsl -l -v` 显示「没有已安装的分发」。根因在 Docker 日志里（`%LOCALAPPDATA%\Docker\log\host\com.docker.backend.exe.log`）：

```
engine linux/wsl failed to start: checking preconditions: Virtual Machine Platform not enabled
```

Windows 的「虚拟机平台」功能没启用 —— 通常是因为 `wsl --install` 在**非管理员**窗口里跑过，用户态组件装上了但可选功能静默失败。以管理员身份执行后**重启电脑**：

```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
```

家庭版只能用 WSL 2 后端（不支持 Hyper-V），Docker Desktop 会自动选对，无需手工配置。

**② `ports are not available: ... bind: 0.0.0.0:3306`**

本机装了原生 MySQL 并注册成 Windows 服务，开机自启占住 3306。停掉它：

```powershell
net stop MySQL80
Set-Service -Name MySQL80 -StartupType Manual   # 免得每次开机又抢端口
```

**③ 后端启动即崩：`java.io.UnsupportedEncodingException: utf8mb4`**

`application.yml` 的 `characterEncoding` 取的是 **Java 字符集名**，早期模板误写成 MySQL 的 `utf8mb4`，
Connector/J 建连时直接抛异常。已修正为 `UTF-8`（服务端 utf8mb4 由 compose 启动参数与建表 DDL 保证）。
如果你的分支还是旧值，同步 `main` 即可。

## 8. 常见故障

| 现象 | 原因 / 处理 |
| --- | --- |
| `403 AUTH-403-CSRF` | 没带 `X-XSRF-TOKEN`。**必须先跑「0.1 获取 CSRF Token」**，它会把 token 存进集合变量 `csrfToken`，后续写请求由集合级 pre-request 自动带上。实测登录不会轮换该 token |
| `401 AUTH-401` | 未登录或会话过期（30 分钟）。重跑登录请求 |
| `422 QA-1001` | 标题不在 10–150 字（用 `code` 判断，不要匹配提示文案） |
| `422 QA-1005` | 标签数量不在 1–5 个 |
| `422 QA-1015` | 给自己的内容投票 |
| 启动报 Flyway 校验失败 | 本地库是旧版本：`docker compose down -v` 后重来（会清空数据） |
| `Could not resolve dependencies ... co-shared-kernel` | 单独构建子模块时漏了 `-am` |
| Docker `500 ... /_ping` | 虚拟机平台未启用，见第 7 节 ① |
| `bind: 0.0.0.0:3306` 端口被占 | 本机 MySQL 服务抢了端口，见第 7 节 ② |
| 启动抛 `UnsupportedEncodingException: utf8mb4` | JDBC `characterEncoding` 写错，见第 7 节 ③ |
| `400 Failed to read request`（curl 提交中文） | Git Bash 用 `-d` 传中文会乱码；改成写入文件再 `--data-binary @file`，见第 5 节 |
| 日志刷 `TODO(S3)：BountyService.expireDue` | S3 定时任务未实现，属正常，不影响 Q&A |
