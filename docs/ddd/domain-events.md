# 领域事件目录（Domain Events）

> 事件是跨上下文唯一的异步集成方式（ADR-002 事务性发件箱）。字段以各 `*-api` 模块中的 record 为准，
> 本文档描述语义、订阅关系与幂等键。新增事件必须同步更新本表并在 PR 中说明订阅者。

## 1. 事件清单

| 事件 | 发布者（触发用例） | 订阅者及处理 | 关键字段（语义） |
| --- | --- | --- | --- |
| `UserRegisteredEvent` | Identity：注册 / 创建教职工 | Reputation：开立声誉账户并按角色发放初始悬赏积分；Discovery：可选的欢迎通知 | `eventId`、`occurredAt`、`userId`、`role` |
| `QuestionPostedEvent` | Q&A：发布问题 | Discovery：插入搜索投影 | `eventId`、`questionId`、`title`、`body`、`tags`、`authorId`、`courseId`、`createdAt` |
| `QuestionEditedEvent` | Q&A：编辑问题 | Discovery：更新投影文本与标签 | `eventId`、`questionId`、`title`、`body`、`tags`、`editedAt` |
| `AnswerSubmittedEvent` | Q&A：提交回答 | Reputation：授予“初来乍到”；Discovery：投影回答数 +1、通知提问者 | `eventId`、`answerId`、`questionId`、`authorId`、`questionAuthorId`、`body`、`createdAt` |
| `AnswerAcceptedEvent` | Q&A：采纳 | Reputation：+15、徽章、悬赏结算；Discovery：标记已采纳、通知回答者 | `eventId`、`questionId`、`answerId`、`questionAuthorId`、`answerAuthorId`、`acceptedAt` |
| `AnswerEndorsedEvent` | Q&A：教师认证 | Reputation：授予“名师认可”；Discovery：通知回答者 | `eventId`、`answerId`、`questionId`、`answerAuthorId`、`endorsedBy`、`endorsedAt` |
| `VoteCastEvent` | Q&A：投票/改投/撤销 | Reputation：按差值计分（受每日上限约束）；Discovery：问题得分同步 | `eventId`、`targetType`、`targetId`、`targetAuthorId`、`voterId`、`oldValue`、`newValue`、`castAt` |
| `CommentPostedEvent` | Q&A：评论 | Discovery：解析 @提及并通知 | `eventId`、`commentId`、`targetType`、`targetId`、`questionId`、`authorId`、`body`、`parentId`、`createdAt` |
| `ContentDeletedEvent` | Q&A：软删除问题/回答 | Discovery：标记投影删除 / 回答数 −1 | `eventId`、`targetType`、`targetId`、`questionId`、`deletedBy`、`deletedAt` |
| `ReputationChangedEvent` | Reputation：记账 | Reputation 排行榜：更新周/月/学期 ZSET | `eventId`、`userId`、`delta`、`reason`、`newReputation`、`occurredAt` |
| `BadgeAwardedEvent` | Reputation：授予徽章 | Discovery：通知获得者 | `eventId`、`userId`、`badgeCode`、`awardedAt` |
| `BountyOpenedEvent` | Reputation：悬赏发起 | Discovery：维护“悬赏中”标记 | `eventId`、`bountyId`、`questionId`、`initiatorId`、`points`、`expiresAt` |
| `BountyAwardedEvent` | Reputation：悬赏结算 | Discovery：通知获奖者 | `eventId`、`bountyId`、`questionId`、`winnerId`、`points`、`awardedAt` |
| `BountyClosedEvent` | Reputation：悬赏关闭/退款 | Discovery：清除“悬赏中”标记 | `eventId`、`bountyId`、`questionId`、`reason`、`closedAt` |

## 2. 事件契约的共同约定

- 所有事件是**不可变 record**，带全局唯一 `eventId`（UUID）与 `occurredAt`（UTC `Instant`）。
- 事件只包含下游必需的数据，**不暴露个人信息**（学号、邮箱）；需要更多信息时下游经 `*-api` 查询。
- 事件放在发布方 `*-api` 模块的 `event` 包，下游只依赖该模块，不依赖发布方 domain。
- 事件字段一旦发布即视为契约：新增字段必须可空/有默认值；删除或改语义要开 ADR。

## 3. 投递语义与幂等

发件箱是**至少一次**投递：

| 消费者 | 幂等键 | 实现 |
| --- | --- | --- |
| Reputation 账本 | `(eventId, userId, reason)` 唯一 | `rep_ledger` 唯一索引；重复插入被忽略 |
| Reputation 事件处理 | `processed_event(eventId, consumer)` | 处理前 markProcessed，已处理则跳过 |
| Discovery 搜索投影 | `processed_event(eventId, "search-index")` | 同一事件重复消费不重复计数 |
| Discovery 通知 | `(source_event_id, recipient_id)` 唯一 | 同一事件对同一接收者只生成一条 |
| 排行榜 ZSET | `processed_event(eventId, "leaderboard")` | 用 `ZINCRBY` 前先判重 |

**顺序**：`OutboxRelay` 按 `outbox_event.id` 顺序投递，单条失败则停止本批后续事件并指数退避重试；
超过 10 次进入死信（指标 `co_outbox_dead_letters`）。因此下游不能假设“不同聚合之间的全局顺序”。

## 4. 行走骨架（Sprint 1 起）

只要有一个监听器抛 `UnsupportedOperationException: TODO(S3)`，整批事件都会失败并堵住发件箱。
因此 S1 开始时：

- 把尚未进入当前 Sprint 的 `@EventListener` 方法改为**空实现**（只打 DEBUG 日志）；
- 每个空实现标注 `TODO(S2/S3)` 并链接技术债 Issue；
- 对应 Sprint 实现时替换空实现，**不改事件契约**；
- 集成测试验证“事件被标记 published，且下游投影/通知按预期出现”。

## 5. 事件风暴核对问题

事件风暴时对每个事件追问：

1. 触发它的**命令**是什么？谁有权发？
2. 它改变哪个**聚合**的哪个不变量？
3. 谁需要知道它？同步查询能不能替代异步事件？
4. 重复投递会怎样？幂等键是什么？
5. 失败后重试会不会产生副作用？死信如何告警？
