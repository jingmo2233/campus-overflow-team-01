# Lab 4 交付物：后端工程骨架与领域代码

> 对应 PDF Lab 4（第 7 周，Sprint 2）。本文件把指导书的 4 项提交物逐条映射到仓库中的实际产物。

| Lab 4 要求 | 本仓库产物 | 状态 |
| --- | --- | --- |
| 可运行的多模块 Maven 工程（`mvn clean install` 全绿） | 27 模块骨架 + Q&A 领域/应用层实现 | ⚠️ 编译与安装通过；**全仓测试要等 S3 的 Reputation/Discovery TODO 清空才会全绿** |
| 通过 `application.yml` 启动并连上数据库 | `co-bootstrap/src/main/resources/application.yml`（dev 默认 profile） | ✅ 需 Docker 提供 MySQL 8 + Redis 7 |
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

等 S3 结束后，把第一条命令的 `-DskipTests` 去掉即可得到 Lab 4 要求的「全绿」。

## 3. 跑 Postman 集合

1. Postman → Import → 选择 `docs/postman/CampusOverflow.postman_collection.json`
2. 打开集合变量，确认 `baseUrl = http://localhost:8080`（端口不同就改这里）
3. 按文件夹顺序执行：`00 认证` → `01 问题` → `02 回答与评论` → `03 投票` → `04 负向用例` → `05 清理`

**关键设计**

- 写请求的 CSRF：集合级 pre-request 脚本读取 `XSRF-TOKEN` Cookie 并写入 `X-XSRF-TOKEN` 请求头；
  若返回 `403 AUTH-403-CSRF`，先单独跑一次「0.1 获取 CSRF Token」。
- 投票不能投给自己，所以 `03` 会注册并登录第二个学生（学生 B），跑完再切回学生 A。
- `questionId` / `answerId` / `commentId` 由前置请求自动写入变量，不要手工填写。

**Lab 4 要求的那条用例**：`01 · 问题 / 1.1 发布问题（POST /api/v1/questions）`
断言：HTTP 201 + 响应体含数值型 `id`，并把 id 写入 `{{questionId}}`，供后续详情/回答/投票复用。

## 4. 数据库落库验证（截图用）

```sql
-- 最近发布的问题
SELECT id, title, tags, answer_count, created_at
FROM qa_question ORDER BY id DESC LIMIT 1;

-- 对应的回答
SELECT id, question_id, author_id, LEFT(body, 30) AS body, created_at
FROM qa_answer ORDER BY id DESC LIMIT 1;
```

建议 Lab 4 提交的截图：`actuator/health` 返回 UP、Postman 1.1 的 201 响应、上面两条 SQL 的结果。

## 5. 当前覆盖范围与限制

| 能力 | 状态 |
| --- | --- |
| 认证（注册/登录/会话/CSRF） | ✅ 可用 |
| 问题（发布/列表/详情/编辑/删除/标签） | ✅ 可用 |
| 回答（提交/编辑/删除/采纳/教师认证） | ✅ 可用（教师认证需教师账号） |
| 投票（+1/-1/撤销/改投增量） | ✅ 可用 |
| 评论（一级评论 + 二级回复） | ✅ 可用 |
| 声誉、徽章、悬赏、排行榜 | ⏳ S3（Dev-C） |
| 搜索、相关推荐、通知 | ⏳ S3（Dev-D） |

**环境限制**：本机没有 Docker 时无法启动后端，也无法跑 `CampusOverflowFlowIT`（Testcontainers）。
Lab 7 的集成测试与 Lab 8 的部署都依赖 Docker，建议本周内至少两台机器装好 Docker Desktop。

## 6. 常见故障

| 现象 | 原因 / 处理 |
| --- | --- |
| `403 AUTH-403-CSRF` | 没带 `X-XSRF-TOKEN`，或登录后 token 轮换。先跑「0.1 获取 CSRF Token」再重试 |
| `401 AUTH-401` | 未登录或会话过期（30 分钟）。重跑登录请求 |
| `422 QA-1001` | 标题不在 10–150 字（用 `code` 判断，不要匹配提示文案） |
| `422 QA-1005` | 标签数量不在 1–5 个 |
| `422 QA-1015` | 给自己的内容投票 |
| 启动报 Flyway 校验失败 | 本地库是旧版本：`docker compose down -v` 后重来（会清空数据） |
| `Could not resolve dependencies ... co-shared-kernel` | 单独构建子模块时漏了 `-am` |
