# DDD 设计文档（Sprint 0 初版）

本目录是 Sprint 0 事件风暴工作坊的产出，也是后续三个 Sprint 的共同语言。S3 结束时在此定稿。

| 文件 | 内容 | 对应交付 |
| --- | --- | --- |
| [context-map.md](context-map.md) | 子域划分、限界上下文、上下文映射与集成方式 | T0-06 / PB-03 |
| [domain-events.md](domain-events.md) | 领域事件目录：发布者、订阅者、关键字段、幂等键 | T0-06 / PB-03 |
| [aggregates.md](aggregates.md) | 聚合清单、聚合根、不变量、状态机 | T0-06 / PB-03 |
| [glossary.md](glossary.md) | 统一语言词汇表（≥30 词） | T0-06 / PB-03 |
| [event-storming.md](event-storming.md) | 工作坊流程、便利贴分类与现场结论 | T0-06 |

## 怎么用

1. **写代码前**：先在 `glossary.md` 找到术语的英文名，类名/方法名/Issue 标题都用它。
2. **改跨上下文调用前**：先看 `context-map.md` 的关系模式，只能经 `*-api` 或集成事件。
3. **新增/修改领域事件**：同步更新 `domain-events.md`，并在 PR 里说明订阅者与幂等键。
4. **实现聚合**：对照 `aggregates.md` 的不变量写测试；测试名直接用不变量描述。

## 维护约定

- 本目录的改动属于“团队契约”，PR 需要 PO 或对应上下文主责人评审（见 `.github/CODEOWNERS`）。
- 与代码冲突时以代码测试为准，但必须在本目录记录差异并开 Issue 跟踪，不允许“文档一套、代码一套”。
- 事件字段以各上下文 `*-api/src/main/java/.../event/*.java` 的 record 为准；本文档描述语义，不复制全部字段。
