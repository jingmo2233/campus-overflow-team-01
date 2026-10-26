# Sprint 0（2026-10-26 ~ 2026-11-01）

> 记录人：@xy1897（Sprint 0 SM）｜状态：**收尾中，待 Sprint 评审**

## Sprint Goal

> 团队、仓库、环境、CI 与 Product Backlog 全部就绪；每位成员都能在本地启动系统、
> 完成注册/登录/加入课程，并完成一次完整的“分支 → PR → 评审 → 合并”。

## 计划：纳入的 PBI 与故事点

- **承诺**：PB-01～PB-05，共 13 故事点；Sprint Backlog 任务 T0-01～T0-11。
- **容量**：4 人 × 每周 8–10 小时 ≈ 32–40 理想小时；按 80% 承诺 ≈ 26–32 小时。
- **Sprint Goal 的可运行验收**：注册 → 登录 → 课程列表 → 加入课程；CI 绿；看板就绪。

| 任务 | 内容 | 负责 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| T0-01 | 仓库、develop 分支、协作者、main 分支保护、Projects 看板 | PO / B | 🟡 部分 | 仓库与 main 保护已就绪；develop 待快进；看板待 PO 创建 |
| T0-02 | Issue/PR 模板、CODEOWNERS、标签、Milestone | A | ✅ | PR #29；标签 18 个、Milestone 5 个 |
| T0-03 | 克隆/环境/JDK/Maven/Docker/Node/启动/测试 | 全员 | 🟡 部分 | @xy1897 已实测（`docs/sprint0/environment.md`）；其他成员待补 |
| T0-04 | 渐进式 CI 门禁 + ADR | E | ✅ | PR #29；`docs/adr/010-渐进式质量门禁.md` |
| T0-05 | 8 条 ADR + Identity 精读 → 架构理解笔记 | 全员 | ✅ | `docs/notes/architecture.md` |
| T0-06 | 事件风暴 → 上下文映射、事件目录、术语表 | 全员 | ✅ | `docs/ddd/` |
| T0-07 | 48 个 PBI 导入、Planning Poker、DoR/DoD | PO / B | 🟡 部分 | Issue #30～#77；`docs/team-agreement.md`；看板字段待 PO |
| T0-08 | `http/auth.http` 冒烟脚本 | C | ✅ | `http/auth.http` |
| T0-09 | 前端课程页：列表、我的课程、加入课程 | D | 🟡 进行中 | `frontend/src/pages/CoursePage.tsx` |
| T0-10 | `docs/team.md` + 破冰 PR（每人 1 次评审） | 全员 | 🟡 部分 | `docs/team.md`；PR #28（@xy1897）；其他成员待补 |
| T0-11 | 教师/管理员测试账号说明写入 README | E | ✅ | README 第 9.2 节 |

图例：✅ 完成；🟡 部分完成/待他人；⬜ 未开始。

## 每日站会摘要

| 日期 | 昨天完成 | 今天计划 | 阻碍 |
| --- | --- | --- | --- |
| 10-26（周一） | 环境验证；CI 门禁与 ADR-010；48 个 PBI 导入；DDD 文档；`http/auth.http` | 课程页；端到端注册/登录/加入课程；整理 PR | main 保护要求 1 个非作者 Approve；本机 3306 被 MySQL80 占用 |
| 10-27（周二） |  |  |  |
| 10-28（周三） |  |  |  |
| 10-29（周四） |  |  |  |
| 10-30（周五） |  |  |  |
| 10-31（周六） |  |  |  |
| 11-01（周日） |  | Sprint 评审与回顾 |  |

## 评审：演示脚本（15 分钟）

1. **仓库与协作证据**（3 min）：分支列表、PR #29 的 CI 绿、CODEOWNERS、Issue 模板、48 个 PBI 与 Milestone。
2. **环境**（2 min）：`docker ps` 显示 mysql/redis 健康；`mvn test` 在 S0 范围全绿，全量在 Reputation 预期红。
3. **可运行软件**（7 min）：
   - 打开 `http://localhost:5173`，注册新学生；
   - 登录 → 课程页 → 加入《软件工程》；
   - 提问页课程下拉出现该课程（可选）；
   - Swagger UI 展示 `POST /api/v1/courses/{id}/join`。
4. **文档**（2 min）：`docs/ddd/`、`docs/notes/architecture.md`、ADR-010、`docs/team-agreement.md`。
5. **回顾**（1 min）：做得好的 / 待改进 / 行动项。

**待教师确认/接受的 PBI**：PB-01～PB-05。
**已知未完成**：Projects 看板（需 PO 管理员权限）、`develop` 快进与保护、其他成员的破冰 PR/评审、分支保护状态检查勾选。

## 数据

| 指标 | 数值 |
| --- | --- |
| 完成故事点 | 13 / 13（PB-01～05 的交付物已落地；PB-01/05 的看板与前端待收尾） |
| PR | #29（CI + 模板，13 files）；#28（hello-xy1897，待评审） |
| CI | 改造前 main 连续 13 次红；改造后 PR #29 后端/前端全绿 |
| CI 红灯恢复时长 | 约 3 天（10-23～10-26） |
| Issue | 48 个 PBI（#30～#77）+ 旧 Issue 12 个 |
| 环境阻塞 | 本机 3306 被 MySQL80 占用 → 使用 3307；已写入 compose 与 README |

## 回顾

### 做得好的

- CI 从“全量必红”改为渐进式门禁，PR 重新有了可信信号；ADR-010 记录了红线与 S3 收口动作。
- 48 个 PBI 一次性导入，标题/标签/Milestone/AC 齐全，后续 Sprint 可以直接排期。
- 事件风暴产出与代码对齐，尤其是发件箱“堵住”风险和行走骨架的结论。

### 待改进

- Sprint 0 启动偏晚，T0-01 的看板与 develop 保护拖到最后；下个 Sprint 第一天就完成仓库设置。
- 团队成员的环境验证与破冰 PR 没有同步完成，贡献证据不均衡。
- 分支基于 main 而非 develop，说明分支策略还没有真正执行；S1 起严格从 develop 切 feature。

### 改进行动（负责人 / Issue）

| 行动 | 负责人 | 截止 | 跟踪 |
| --- | --- | --- | --- |
| 创建 Projects 看板并把 48 个 PBI 加入，配置 Story Points/Sprint 字段 | @jingmo2233 | 10-28 | #30 |
| 快进 develop 到 main，配置 develop 保护与状态检查 | @jingmo2233 | 10-28 | #30 |
| 为 PR #29 找非作者 Approve 并合并，打 v0.1.0 | @xy1897 | 10-28 | #29 |
| 补环境验证截图与破冰 PR（陈阳、fxs0917） | @yttt697 @fxs0917 | 10-29 | #31 / 待建 |
| S1 计划会确认行走骨架范围与技术债 Issue | @xy1897 | S1 第 1 天 | PB-18 |

## 附：Sprint 0 交付物清单

- [x] 48 个 PBI Issue + 5 个 Milestone + 18 个标签
- [x] `docs/ddd/`（上下文映射、事件目录、聚合、术语表、事件风暴记录）
- [x] `docs/notes/architecture.md`
- [x] `docs/adr/010-渐进式质量门禁.md`
- [x] `docs/team.md`、`docs/team-agreement.md`
- [x] `http/auth.http`
- [x] `docs/sprint0/environment.md`
- [x] README 开发者须知
- [ ] 前端课程页（进行中）
- [ ] Projects 看板（PO）
- [ ] v0.1.0 标签（待 PR 合并）
