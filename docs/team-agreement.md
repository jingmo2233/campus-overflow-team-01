# 团队工作约定（Team Agreement）

> Sprint 0 / T0-07。本文件与 `docs/team/charter-v1.0.md` 配套：章程写价值观，本文写**可执行的规则**。
> 规则可以改，但必须经 PR 评审并在 Sprint 回顾会上说明原因。

## 1. 时间与沟通

| 项目 | 约定 |
| --- | --- |
| 工作时段 | 周一至周五 18:00–23:00；周末按个人安排，不强制 |
| 响应时限 | 群消息 2 小时内；PR 评审请求 24 小时内首次反馈 |
| 站会 | 周一/三/五 21:30，15 分钟；无法参加需提前在群里请假并补文字三问 |
| 阻塞 | 超过 30 分钟未解决，立即在群里发“现象 + 已尝试 + 需要谁” |
| 会议 | Sprint 计划会 ≤1h；评审会 ≤1h；回顾会 ≤45min；均需有人记录到 `docs/scrum/` |

## 2. 分支与提交

- `main`：只接受发布 PR（develop → main），每个 Sprint 末打标签；不直接提交。
- `develop`：日常集成分支，保持 CI 绿；功能分支从它切出、合并回它。
- `feature/<issue>-<short-name>`：一个 Story/Task 一个分支，生命周期 ≤3 天。
- `fix/<issue>-<short-name>`：非紧急缺陷；`hotfix/<version>-<name>`：已发布版本紧急修复。
- 提交信息：`<type>(<scope>): <中文简述 ≤50 字> (#<issue>)`，type ∈ feat/fix/test/refactor/docs/chore/ci/perf/style。
- 一次提交只做一件事、能通过编译；禁止提交 `target/`、`node_modules/`、`.env`、真实密钥。

## 3. Pull Request

- 一个 PR 只对应一个 Story/Task；变更 ≤400 行（不含锁文件/生成文件），超过就拆。
- 标题同提交规范；描述用 `.github/pull_request_template.md`：关联 Issue、改动说明、测试证据、自检清单、AI 使用说明。
- 至少 1 名非作者 Approve；共享内核/错误码/ADR 改动需 2 人（CODEOWNERS 自动指派）。
- 作者负责回复每条评论并 Resolve；有分歧时在 PR 讨论，必要时升级到站会或写 ADR。
- 合并方式：feature→develop 用 Squash；develop→main 用 Merge commit。合并后删除 head 分支。
- **CI 红灯的 PR 不请求评审**；红灯期间除修复 PR 外暂停合并。

## 4. Definition of Ready（PBI 进入 Sprint 的条件）

- [ ] 采用“作为…我希望…以便…”格式，价值明确
- [ ] 关联 Epic 与上下文标签（`ctx:*`）
- [ ] 有可验证的 Given/When/Then 验收标准，标出对应测试类或需新增测试
- [ ] 已用 Planning Poker 估算（≤8 点；超过则拆分）
- [ ] 依赖已识别（上游事件字段、其他 PBI、外部账号/环境）
- [ ] 团队对“怎样算完成”没有歧义

## 5. Definition of Done（PBI 完成的条件）

- [ ] 代码经 PR 合入 `develop`，至少 1 名非作者 Approve，PR 关联并关闭 Issue
- [ ] 相关领域测试与应用层测试全部通过；新增业务规则有单元测试
- [ ] **未修改任何已有测试断言，未删除或 `@Disabled` 测试**
- [ ] ArchUnit FF-1～FF-8 通过
- [ ] 所在领域模块行覆盖率 ≥70%（按 ADR-010 的 Sprint 范围执行）
- [ ] CI 在 PR 与合并后的 develop 上均为绿色
- [ ] 涉及接口/错误码：Swagger 可见且可调用，错误码符合 `docs/错误码.md`
- [ ] 涉及前端：`npm run typecheck && npm run build` 通过
- [ ] 本地 docker compose 环境按验收标准手工演示通过（PR 附截图/GIF）
- [ ] 文档/ADR 已更新；演示脚本已更新（如涉及）

## 6. 估算与容量

- 斐波那契：1、2、3、5、8；基准故事 PB-11 投票/改投/撤销 = 5 点。
- 每人每周课外投入 8–10 小时；4 人组 2 周 Sprint 容量约 64–80 理想小时，只承诺 80%。
- 从 Sprint 2 起用上个 Sprint 的实际速度做计划；不把“加班”当容量。

## 7. 评审与质量

- 评审意见用“问题 + 依据 + 建议”，避免只写 LGTM；至少指出一处可改进点或明确说明“已验证无问题”。
- 评审清单见指导书 7.5.2：DDD/架构、正确性、事务与事件、测试、可读性、安全。
- 测试是规格：先读测试再实现；测试与文档矛盾时在 PR 说明并找教师确认，不擅自改断言。
- 覆盖率只升不降；S1 起 `co-qa-domain` 门禁 0.70，S2 加 Reputation，S3 全量。

## 8. 学术诚信与 AI

- 允许 AI 辅助理解代码、生成测试草稿；**必须在 PR 描述中注明用途**。
- 本人必须能解释提交的每一行；评审会随机抽查。
- 禁止：修改已有断言/删测试/`@Disabled` 求绿、绕过 PR 直接推 main/develop、冒用他人提交、提交密钥或真实个人信息、复制其他小组代码。

## 9. 冲突与升级

1. 技术分歧：在 PR 评论用数据/测试说话；24 小时内不能达成一致 → 站会 10 分钟决策。
2. 影响架构/跨上下文：写 ADR，PO + 相关主责人评审。
3. 进度/分工冲突：SM 主持，必要时调整 Sprint Backlog 并记录在 `docs/scrum/`。
4. 学术诚信问题：直接报告教师，不在组内私下处理。

## 10. 确认

| 成员 | GitHub | 确认 |
| --- | --- | --- |
| 韦国超 | @jingmo2233 | ✅ 2026-10-26 |
| xy1897 | @xy1897 | ✅ 2026-10-26 |
| 陈阳 | @yttt697 | 待确认 |
| fxs0917 | @fxs0917 | 待确认 |
