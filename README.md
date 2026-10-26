# CampusOverflow · 学生初始框架（Java / Spring Boot 3）

这是《软件工程》贯穿项目的**起始代码**。整体骨架、构建脚本、部署与 CI 已经就位，
**四个限界上下文中的 Identity（身份与认证）是完整的参考实现**，其余三个上下文的核心业务留给你们按 Sprint 实现。

现在克隆下来直接跑 `mvn test`，你会看到**大量失败的测试**——这不是环境坏了，这是你们这学期的需求说明书。

## 1. 五分钟跑起来

```bash
# 1) 起中间件（需要 Docker）
docker compose -f deploy/docker-compose.yml up -d mysql redis

# 2) 编译并跑测试（第一次会红，这是预期的）
cd backend && mvn clean test

# 3) 启动后端（dev profile，不要加 demo，因为演示数据依赖尚未实现的业务代码）
mvn spring-boot:run -pl co-bootstrap -Dspring-boot.run.profiles=dev

# 4) 启动前端
cd ../frontend && npm install && npm run dev      # http://localhost:5173
```

接口文档：http://localhost:8080/swagger-ui.html

## 2. 已经给你们的 vs 需要你们写的

| 已提供（读懂它，不要重写） | 需要你们实现 |
| --- | --- |
| 27 个 Maven 模块的骨架与依赖关系 | Q&A、Reputation、Discovery 三个上下文的**领域层**（聚合、值对象、业务规则） |
| `co-shared-kernel`：领域异常、Actor、事件契约、分页 | 上述三个上下文的**应用层**（用例编排、事务边界、事件发布） |
| **Identity 上下文完整实现**（注册、登录、课程、角色）——当作范例 | 前端页面的功能增强（见第 6 节） |
| 所有 `*-api` 发布语言、所有基础设施适配器（JPA / Redis / JDBC）与 REST 控制器 | 你们自己补充的单元测试（领域层覆盖率要求见第 5 节） |
| 安全链、发件箱中继、全局异常处理、Flyway 迁移、Docker、CI | |
| **107 个 `TODO` 方法与 38 个随之失败的测试**（领域测试 + 应用层用例测试 + 端到端集成测试） | |

> 为什么基础设施是给好的？因为本课程要考察的是**领域建模与架构约束**，而不是 JPA 映射的熟练度。
> 你只要把领域方法写对，适配器就能正常工作。

## 3. 怎么开始：跟着测试走

1. 打开 `docs/学生任务清单.md`，里面列出了全部 107 个 `TODO` 方法及其所在文件。
2. 从 Q&A 的领域层开始：`co-qa/co-qa-domain/src/test/java/.../QuestionTest.java` 就是规格说明。
3. 运行单个测试类：`mvn test -pl co-qa/co-qa-domain -Dtest=QuestionTest`
4. 红 → 绿 → 重构，逐个把测试变绿；再写应用层，让 `AnswerAndVoteServiceTest` 变绿。
5. 三个上下文都绿之后，跑 `mvn verify`（需要 Docker），端到端集成测试会验证“提问 → 回答 → 采纳 → 声誉 → 搜索 → 通知”整条链路。

**允许你们增加方法、增加类、增加测试**，但**不要修改已有测试的断言**——那相当于改需求；
如果你确信某条测试与文档矛盾，在 PR 里说明并与教师确认。

## 4. Sprint 与任务对应（指导书 V2，8 周）

> 起始代码 README 原按 15 周规划；现按《实验指导书 V2》压缩为 8 周、5 个 Sprint。
> 完整 Backlog 见 [`docs/backlog/product-backlog-v2.md`](docs/backlog/product-backlog-v2.md)，每个 PBI 对应一个 Issue。

| Sprint | 周次 | 目标 | 对应任务 |
| --- | --- | --- | --- |
| S0 | 第 1 周 | 团队、仓库、环境、CI 与 Backlog 就绪；能注册/登录/加入课程 | T0-01～11；v0.1.0 |
| S1 | 第 2–3 周 | 提问 → 回答 → 投票 → 评论 → 采纳 的完整闭环 | PB-06～19；Q&A 36 个方法 |
| S2 | 第 4–5 周 | 问答行为异步、幂等地转化为声誉、徽章、悬赏与排行榜 | PB-20～30；Reputation 39 个方法 |
| S3 | 第 6–7 周 | 搜索、相关推荐、通知、@提及，端到端链路全绿 | PB-31～39；Discovery 32 个方法 |
| S4 | 第 8 周 | 稳定化、体验打磨、可观测，v1.0 发布与答辩 | PB-40～47（按容量） |

## 5. 验收门槛（对应课程考核）

| 项目 | 要求 | 如何自检 |
| --- | --- | --- |
| 单元测试 | 当前 Sprint 范围全绿（ADR-010 渐进式门禁） | `mvn test -pl "$SPRINT_MODULES" -am` |
| 领域层覆盖率 | S1 起 `co-qa-domain` ≥70%，S2 加 Reputation，S3 全量 ≥70% | `mvn verify` |
| 架构约束 | ArchUnit FF-1～FF-8 全部通过 | `mvn test -pl co-bootstrap -am -Dtest=ArchitectureTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false` |
| 集成测试 | S3 起必须绿；S0～S2 允许失败 | `mvn verify`（需要 Docker） |
| 前端 | 类型检查与构建通过 | `cd frontend && npm run typecheck && npm run build` |
| 流水线 | GitHub Actions 全绿；S0 范围见 ADR-010 | 推送后看 Actions 页 |

**不要为了让测试通过而删测试或放宽断言**——CI 的历史记录会保留每一次改动，这属于学术诚信范畴。

## 6. 前端增强任务（S4 建议）

前端已提供可运行的骨架（登录/注册、问题列表与搜索、详情页投票与采纳、提问、排行榜、通知）。建议自行完成：

- 分页与无限滚动、加载骨架屏与错误重试；
- Markdown 实时预览与代码高亮；
- 悬赏发起对话框（当前是 `prompt()` 占位）、悬赏倒计时展示；
- 通知红点的实时刷新（当前是 30 秒轮询，可改 SSE）；
- 移动端适配与可访问性（键盘操作、对比度）。

## 7. 你必须先读的材料

1. `docs/adr/`：10 条架构决策记录——**为什么**这样设计比代码本身更重要；
2. 《CampusOverflow 软件架构设计文档（arc42）》：第 5 章构建块视图、第 6 章运行时视图、第 10 章质量场景；
3. `docs/错误码.md`：错误码分段与 HTTP 状态映射，实现领域异常时必须遵守；
4. `docs/ddd/`：上下文映射、领域事件、聚合清单、统一语言（Sprint 0 事件风暴产出）；
5. `docs/notes/architecture.md`：架构理解笔记与评审会自测题。

## 8. 常见坑

| 现象 | 原因 |
| --- | --- |
| `UnsupportedOperationException: TODO(S2)...` | 正常，说明这个方法还没实现 |
| 声誉不变化 | 声誉是异步结算的：事件先落 `outbox_event`，由中继投递；检查 `last_error` 字段 |
| 中文搜索没结果 | 必须用 compose 里的 MySQL（已配置 `ngram_token_size=2`），自建库请对照参数 |
| 事件重复导致加分两次 | 消费端必须幂等：账本唯一键 `(event_id, user_id, reason)` 或 `processed_event` 表 |
| 提交后 CI 红 | 先本地跑对应范围的 `mvn test`，再推送；不要把红的分支合并到 `main` |
| 本机 3306 被占用 | 用 `DB_HOST_PORT=3307` 起 MySQL 容器，后端加 `DB_PORT=3307` |

## 9. 开发者须知（Sprint 0）

### 9.1 Profile 与端口

| profile | 用途 | 注意 |
| --- | --- | --- |
| `dev`（默认） | 本地开发，连 `localhost:3306`（或 `DB_PORT`）的 MySQL/Redis | **不要加 `demo`**：`DemoDataSeeder` 依赖尚未实现的悬赏等业务 |
| `prod` | 容器部署 | 必须注入 `CO_FIELD_KEY`；`SPRING_PROFILES=prod` |
| `demo` | S3 完成后加载演示数据 | S3 之前启动会抛 `UnsupportedOperationException` |

- 后端：`http://localhost:8080`；Swagger：`/swagger-ui.html`；健康检查：`/actuator/health`
- 前端：`http://localhost:5173`，Vite 把 `/api` 代理到 8080
- 冒烟脚本：`http/auth.http`（CSRF → 注册 → 登录 → 我 → 课程 → 加入课程）

### 9.2 教师 / 管理员测试账号（Sprint 1～2 开发期）

`DemoDataSeeder` 在 S2 结束前不可用。开发期先正常注册账号，再用 SQL 提升角色；**改完必须退出重新登录**（会话缓存了 Actor）：

```sql
-- docker exec -it co-mysql mysql -ucampus -pcampus_dev_pwd campus_overflow
UPDATE id_user SET role = 'TEACHER', verified = TRUE WHERE username = 'T2001';
UPDATE id_user SET role = 'ADMIN',   verified = TRUE WHERE username = 'admin01';
```

教师建课后，学生调用 `POST /api/v1/courses/{id}/join` 加入；教师可通过
`PUT /api/v1/courses/{id}/members/{userId}` 任命助教（role: `TA`）。

### 9.3 CI 与质量门禁

- CI 使用**渐进式门禁**（ADR-010）：S0 范围 = `co-shared-kernel` + Identity domain + Q&A domain/application；
  每个 Sprint 计划会后由质量与 DevOps 负责人更新 `SPRINT_MODULES`，S3 恢复全量。
- **红线**：只能缩小运行范围，不能用 `-Dmaven.test.failure.ignore`、`@Disabled`、删测试或改断言让 CI 变绿。
- 覆盖率：`co-qa-domain` 自 S1 起 ≥0.70；S3 根 POM 统一 ≥0.70。

### 9.4 文档索引

| 文档 | 内容 |
| --- | --- |
| `docs/team.md` | 成员、角色、代码所有权 |
| `docs/team-agreement.md` | 分支/提交/PR 规则、DoR/DoD、评审与学术诚信 |
| `docs/ddd/` | 上下文映射、领域事件、聚合与不变量、统一语言 |
| `docs/notes/architecture.md` | 架构理解笔记、ADR 讲解分工、评审自测题 |
| `docs/sprint0/environment.md` | 环境版本矩阵与实测记录 |
| `docs/sprint0/verification.md` | 注册→登录→加入课程端到端证据 |
| `docs/adr/` | 001～010 架构决策记录 |
| `docs/backlog/product-backlog-v2.md` | 48 个 PBI 与 Issue 对照 |
| `docs/scrum/sprint-0.md` | Sprint 0 计划、站会、评审与回顾 |

### 9.5 当前状态（2026-10-26）

- Sprint 0 收尾中；目标标签 `v0.1.0`。
- main 受保护：至少 1 名非作者 Approve + CI 绿。
- `develop` 落后于 `main`：本 Sprint 收尾时由 PO 快进并开启保护。

