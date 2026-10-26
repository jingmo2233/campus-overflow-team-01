# 统一语言术语表 v2.0（Ubiquitous Language）

> 依据指导书 V2 第 3.3 节与起始代码整理。**代码类名、方法名、错误信息、Issue 标题、站会发言都用同一套词。**
> 与代码不一致时以代码测试为准，但必须开 Issue 记录差异。维护人：各上下文主责人；评审人：PO。

## 1. 战略设计

| 中文术语 | 代码名 | 定义 / 规则 | 所属上下文 |
| --- | --- | --- | --- |
| 限界上下文 | Bounded Context | 一个模型边界；边界内术语含义唯一，跨边界只经 `*-api` 或事件 | 全局 |
| 子域 | Subdomain | 问题空间划分：核心域（Q&A、Reputation）、支撑子域（Identity、Discovery） | 全局 |
| 上下文映射 | Context Map | 上下文之间的关系模式（OHS/PL、Customer/Supplier、Conformist、Shared Kernel） | 全局 |
| 统一语言 | Ubiquitous Language | 业务、文档、代码共用的一套术语，见本表 | 全局 |
| 共享内核 | Shared Kernel | `co-shared-kernel`：异常、Guard、Actor、事件契约、分页；零框架依赖 | 全局 |
| 发布语言 | Published Language | 上下文对外暴露的稳定契约：查询接口 + 只读 DTO + 事件 record | 各上下文 `*-api` |
| 防腐层 | Anti-Corruption Layer | 下游把上游模型转换成本地模型的适配层；本项目以 `*ApiImpl` / 事件处理器承担 | 下游上下文 |

## 2. 战术设计

| 中文术语 | 代码名 | 定义 / 规则 | 所属上下文 |
| --- | --- | --- | --- |
| 聚合 | Aggregate | 一致性边界；一个事务只修改一个聚合 | 全局 |
| 聚合根 | Aggregate Root | 外部只能通过它访问聚合内部；工厂方法完成全部校验 | 全局 |
| 实体 | Entity | 有身份标识、生命周期内可变的对象（如 `CourseMember`） | 全局 |
| 值对象 | Value Object | 不可变、以值判等（record），构造时完成规范化与校验 | 全局 |
| 领域服务 | Domain Service | 不属于单个聚合的无状态规则（如 `AcademicTerm`、`PasswordPolicy`） | 各上下文 |
| 仓储 | Repository | 领域层接口、基础设施层实现；聚合的持久化入口 | 各上下文 |
| 工厂方法 | Factory Method | `Question.post(...)`、`Bounty.open(...)`：创建即合法，id 为 null | 各上下文 |
| 领域事件 | Domain Event | 聚合状态变化的事实，过去式命名；跨上下文时成为集成事件 | 全局 |
| 集成事件 | IntegrationEvent | 跨上下文传播的不可变 record，带全局唯一 `eventId` | 全局 |
| 事务性发件箱 | Transactional Outbox | 保存聚合与写 `outbox_event` 在同一事务；中继异步投递 | 全局 |
| 幂等 | Idempotency | 同一事件重复投递不产生重复副作用；用唯一键或 `processed_event` | 全局 |
| 最终一致 | Eventual Consistency | 跨聚合/上下文不追求强一致，靠事件在数百毫秒内收敛 | 全局 |
| 只追加 | Append-only | 账本只插入、不更新/删除；余额由流水求和推导 | Reputation |
| 操作者 | Actor | 当前操作主体（userId、displayName、role），由接口层显式传入 | 全局 |
| 行走骨架 | Walking Skeleton | 先让端到端链路可运行，未实现监听器暂为空实现，后续填充 | 全局 |

## 3. 业务术语

| 中文术语 | 代码名 | 定义 / 规则 | 所属上下文 |
| --- | --- | --- | --- |
| 问题 | Question | 学生发布的求助；标题 10–150 字、正文 Markdown、1–5 个标签，可关联课程 | Q&A |
| 回答 | Answer | 对问题的解答；独立聚合，经 `questionId` 引用问题 | Q&A |
| 采纳 | accept | 提问者或课程管理者选定最佳答案；每题最多一次且不可更改 | Q&A |
| 教师认证 | endorse | 课程教师/助教为优质回答打的标记（`endorsedBy`），每个回答只能一次 | Q&A |
| 投票 | Vote | 对问题/回答 +1 或 −1；每人每内容一票；改投、撤销（0）都可以 | Q&A |
| 评论 / 回复 | Comment | 挂在问题或回答下，最多一层回复，回复必须与父评论同一目标 | Q&A |
| 标签 | TagName / Tags | 统一小写、去掉前导 `#`，支持中文与 `# + . - _`；每题去重后 1–5 个 | Q&A |
| 软删除 | softDelete / deletedAt | 标记删除而非物理删除；已删除内容不可再回答/编辑/采纳 | Q&A |
| 课程管理者 | actorManagesCourse | 管理员，或该课程教师/助教（由 `IdentityApi.canManageCourse` 判定） | Identity / Q&A |
| 课程 | Course | 教师创建的课程；代码 + 学期唯一；成员有角色 | Identity |
| 课程成员 | CourseMember | 用户与课程的关系；角色 ∈ {STUDENT, TA, TEACHER} | Identity |
| 角色 | Role | 平台角色：STUDENT / TA / TEACHER / ADMIN | Identity |
| 声誉 | reputation | 社区信誉分，只能由账本流水改变；采纳 +15、被赞 +10、被踩 −2 | Reputation |
| 账本流水 | LedgerEntry | 只追加的声誉变动记录；`(eventId, userId, reason)` 唯一 | Reputation |
| 每日上限 | DAILY_VOTE_CAP | 每人每个 UTC 自然日因被投票获得的正向声誉最多 200 | Reputation |
| 悬赏 | Bounty | 课程管理者用悬赏积分为问题加码；OPEN → AWARDED / EXPIRED | Reputation |
| 悬赏积分 | availablePoints / frozenPoints | 与声誉不同的“货币”；发起悬赏时冻结，结算时转出或退回 | Reputation |
| 徽章 | BadgeType | 初来乍到、初露锋芒、解惑达人、名师认可、小有名气、德高望重 | Reputation |
| 排行榜周期 | LeaderboardPeriod | WEEK / MONTH / TERM / ALL；学期：2–7 月春季，8 月–次年 1 月秋季 | Reputation |
| 学期 | AcademicTerm | 校园时区 `Asia/Shanghai`；提供学期/ISO 周/月键 | Reputation |
| 对账 | ReconciliationService | 检查每个账户的声誉是否等于其流水之和（FF-8） | Reputation |
| 搜索投影 | SearchDocument | Discovery 自己的问题模型，只关心“如何被找到” | Discovery |
| 相关问题 | RelatedQuestionPolicy | 按标签集合 Jaccard 相似度推荐；平局时得分高、已采纳者优先 | Discovery |
| 提及 | MentionParser | 评论中的 @昵称，去重，每条最多提醒 5 人 | Discovery |
| 通知 | Notification | 站内消息；`(sourceEventId, recipientId)` 唯一，只有接收者能标记已读 | Discovery |

## 4. 过程术语

| 中文术语 | 代码名 | 定义 / 规则 |
| --- | --- | --- |
| 产品待办 | Product Backlog | 按优先级排序的 PBI 集合，GitHub Projects 中 `type:story` 的 Issue |
| Sprint 待办 | Sprint Backlog | 当前 Milestone 中的 Story + Task Issue |
| 增量 | Increment | main 上的版本标签 + Release；必须满足 DoD |
| 定义就绪 | Definition of Ready (DoR) | 故事格式、可验证 AC、已估算、依赖已识别 |
| 完成定义 | Definition of Done (DoD) | PR 合入、测试通过、ArchUnit、覆盖率、CI 绿、可演示 |
| 燃尽图 | Burndown | 剩余故事点随时间下降的图；SM 每次站会后更新 |
| 速度 | Velocity | 上个 Sprint 实际完成的平均故事点，用于下个 Sprint 计划 |

## 5. 命名对照（易错）

| 不要写 | 应写 | 原因 |
| --- | --- | --- |
| `postQuestion` / `createQuestion` | `Question.post` | 工厂方法名表达业务动作 |
| `delQuestion` / `remove` | `softDelete` | 本项目一律软删除 |
| `like` / `dislike` | `Vote` + `value=+1/-1` | 统一语言 |
| `score`（作为可写字段） | `adjustScore` 原子更新 | ADR-008 计数器不回写 |
| `teacher`（平台角色） | `TEACHER` / 课程内 `CourseRole.TEACHER` | 平台角色与课程角色是两套 |
| `notification` 表放 Q&A | `dis_notification` | 表前缀 = 上下文 |
