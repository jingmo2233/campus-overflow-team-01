# 团队成员与分工（Sprint 0）

- **团队**：CampusOverflow Team 01
- **仓库**：https://github.com/jingmo2233/campus-overflow-team-01
- **课程**：《软件工程》贯穿项目 · 8 周 · 4 人组
- **更新**：2026-10-26（Sprint 0）

## 1. 成员

| 成员 | GitHub | 团队角色 | 主责上下文 / 方向 | Sprint 0 重点 |
| --- | --- | --- | --- | --- |
| 韦国超 | [@jingmo2233](https://github.com/jingmo2233) | 产品负责人（PO）、仓库所有者 | Q&A 回答/采纳 → Reputation 计分；Backlog 排序与验收 | T0-01 仓库与分支保护、T0-02 模板、T0-07 Backlog 导入 |
| xy1897 | [@xy1897](https://github.com/xy1897) | 开发者 A + 质量与 DevOps（E）；Sprint 0 SM | Q&A 问题与查询；CI/CD、ArchUnit、测试替身 | T0-04 CI 门禁与 ADR、T0-05 架构笔记、T0-08 冒烟脚本、T0-11 开发者须知 |
| 陈阳 | [@yttt697](https://github.com/yttt697) | 开发者 C + QA | 投票/评论 → 悬赏 → 搜索推荐；测试与评审 | T0-06 事件风暴、T0-10 破冰 PR、评审 PR #1/#29 |
| fxs0917 | [@fxs0917](https://github.com/fxs0917) | 开发者 D | 前端、排行榜、通知中心 | T0-03 环境验证、T0-09 课程页、前端评审 |

> 4 人组按指导书建议合并 C/D/E 的部分职责；上表是 Sprint 0 的分工，S1 起按 Sprint Backlog 重新认领。
> 真实姓名/联系方式如未确认，以 GitHub 账号为准；请本人补全后在本文件提 PR。

## 2. Scrum 角色轮换

| Sprint | PO | SM | 备注 |
| --- | --- | --- | --- |
| S0（第 1 周） | 韦国超 | xy1897 | 建仓库、模板、CI、Backlog |
| S1（第 2–3 周） | 韦国超 | xy1897（暂定） | 问答闭环；S1 计划会确认 |
| S2（第 4–5 周） | 韦国超 | 陈阳（暂定） | 声誉与悬赏 |
| S3（第 6–7 周） | 韦国超 | fxs0917（暂定） | 内容发现与端到端 |
| S4（第 8 周） | 韦国超 | xy1897（暂定） | 发布与答辩 |

## 3. 代码所有权（与 `.github/CODEOWNERS` 对应）

| 路径 | 第一评审人 | 说明 |
| --- | --- | --- |
| `backend/co-qa/` | @xy1897 @jingmo2233 | 领域/应用层改动需 Q&A 主责人 |
| `backend/co-identity/` | @xy1897 | 参考实现，改动需谨慎 |
| `backend/co-reputation/` | @jingmo2233 @yttt697 | 账本/悬赏规则 |
| `backend/co-discovery/` | @yttt697 @fxs0917 | 搜索/通知 |
| `backend/co-shared-kernel/` | @xy1897 @jingmo2233 | 影响所有上下文，需两人评审 |
| `frontend/` | @xy1897 @fxs0917 | 前端 |
| `/.github/`、`/deploy/` | @jingmo2233 @xy1897 | CI/部署 |
| `docs/adr/`、`docs/错误码.md` | @jingmo2233 | 团队契约 |

## 4. 沟通

- **主渠道**：课程群（异步）。工作时段 2 小时内响应；阻塞超过 30 分钟必须群里提出。
- **站会**：每周一/三/五，15 分钟，三问：昨天完成什么、今天计划什么、有什么阻碍。
- **评审**：PR 发出后 24 小时内首次反馈；Sprint 评审会前 1 天冻结功能。
- **记录**：Scrum 事件记录在 `docs/scrum/sprint-N.md`；架构决策写 ADR；跨上下文契约写 `docs/ddd/`。

## 5. 个人承诺

- 我遵守 `docs/team-agreement.md` 的分支、提交、评审与学术诚信约定。
- 我不修改已有测试断言、不删除或 `@Disabled` 测试来让 CI 变绿。
- 我能在评审会上解释自己每个 PR 的实现与取舍。
- 我在 PR 中如实注明是否使用了 AI 辅助。

| 成员 | 确认（GitHub handle / 日期） |
| --- | --- |
| 韦国超 | @jingmo2233 / 2026-10-26 |
| xy1897 | @xy1897 / 2026-10-26 |
| 陈阳 | @yttt697 / 待确认 |
| fxs0917 | @fxs0917 / 待确认 |
