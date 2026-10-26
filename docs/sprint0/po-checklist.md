# Sprint 0 收尾：PO / 管理员操作清单

> 这些操作需要 **仓库管理员（@jingmo2233）** 权限；@xy1897 是协作者（push，无 admin），
> 当前 token 也没有 `project` scope，因此无法代做。以下每一步都给出 UI 路径与可复制的命令。
>
> 完成顺序建议：**先合并 PR → 再快进 develop → 最后开分支保护/看板 → 打 v0.1.0**。

## 1. 合并 Sprint 0 的三个 PR（需要非作者 Approve）

| PR | 内容 | 状态 |
| --- | --- | --- |
| [#29](https://github.com/jingmo2233/campus-overflow-team-01/pull/29) | 渐进式 CI、模板、CODEOWNERS、JDBC 启动修复 | CI 绿，待 Approve |
| [#78](https://github.com/jingmo2233/campus-overflow-team-01/pull/78) | DDD、架构笔记、团队约定、环境/冒烟文档 | CI 绿，待 Approve |
| [#79](https://github.com/jingmo2233/campus-overflow-team-01/pull/79) | 前端课程页 + 截图 | CI 绿，待 Approve |

- main 当前保护规则要求 **至少 1 个非作者 Approve**；作者 @xy1897 不能自审。
- 合并顺序：#29 → #78 → #79（#78/#79 的 base 已改为 main；#29 合并后 diff 会自动收敛）。
- 建议用 **Merge commit** 合并到 main，保留 PR 记录。

## 2. 快进 develop 并开启保护

`develop` 目前是 `main` 的祖先（落后若干提交），可以直接快进：

```bash
git fetch origin
git switch develop
git merge --ff-only origin/main
git push origin develop
```

随后在 **Settings → Branches / Rulesets** 为 `develop` 添加规则：

| 规则 | develop | main |
| --- | --- | --- |
| Require a pull request before merging | ✅ | ✅ |
| Required approvals | 1（非作者） | 2（含 SM/PO） |
| Dismiss stale approvals | ✅ | ✅ |
| Require review from Code Owners | 建议 | ✅ |
| Require status checks to pass | ✅ `后端构建与验证`、`前端构建` | 同左 |
| Require branches to be up to date | ✅ | ✅ |
| Require conversation resolution | ✅ | ✅ |
| Do not allow bypassing | ✅ | ✅ |
| Restrict force pushes / deletions | ✅ | ✅ |

> `容器镜像构建` 只在 push 时运行（PR 上 skipped），因此不要把它设为 PR 必过检查；
> main 合并后它会自动在 push 上跑，必须绿。

## 3. 创建 Projects 看板（PO 个人账号）

1. 打开仓库 → **Projects** → **New project** → 选 **Board**，命名 `CampusOverflow Sprint Board`。
2. **Settings → Manage access**：把 @xy1897、@yttt697、@fxs0917 加为 **Write**。
3. 仓库 **Projects** 页 → **Link a project** 关联该看板。
4. 建视图：
   - **Board**：列 `Backlog / Sprint Backlog / In Progress / In Review / Done`（按 Status 字段分组）；
   - **Table**：按 Rank 排序 = 优先级顺序；按 Sprint 过滤。
5. 自定义字段：`Story Points`（Number）、`Sprint`（Single select：Sprint 0～4）、`Owner`（Text/People）。
   `Rank` 用 Projects 内置排序即可。
6. 批量加入 48 个 PBI：在项目里 **Add items** → 搜索 `type:story`，全选加入；
   或安装 GitHub CLI 后：

```bash
# 需要 project scope；没有就先执行 gh auth refresh -s project
gh auth refresh -s project
gh project list --owner jingmo2233
gh issue list --repo jingmo2233/campus-overflow-team-01 --label type:story --limit 100 \
  --json number,url --jq '.[].url' | while read -r url; do
    gh project item-add <PROJECT_NUMBER> --owner jingmo2233 --url "$url"
  done
```

7. 给每个 PBI 填 `Sprint` 与 `Story Points`（数值见 `docs/backlog/product-backlog-v2.md`）。
8. 看板截图放 `docs/sprint0/screenshots/projects-board.png`，并在 PR/评审时展示。

## 4. 打 v0.1.0 标签与 Release

三个 PR 合并、main CI 绿后：

```bash
git fetch origin
git switch main && git pull --ff-only
git tag -a v0.1.0 -m "Sprint 0: 团队、仓库、环境、CI 与 Backlog 就绪；注册/登录/加入课程"
git push origin v0.1.0
```

然后在 GitHub **Releases → Draft a new release**：
- Tag：`v0.1.0`；Title：`v0.1.0 — Sprint 0 启动`;
- 描述直接粘贴 `docs/releases/v0.1.0.md`；
- 附件：可上传 `docs/sprint0/screenshots/` 下的截图。

## 5. 评审前最后核对（9.1 节）

- [ ] main / develop 最新提交 CI 绿
- [ ] 分支保护开启且 Bypass list 为空
- [ ] 48 个 PBI 在 Projects 中按 Rank 排序，Sprint 1 的 PBI 满足 DoR
- [ ] 每位成员至少 1 个已合并 PR + 1 次有效评审
- [ ] `docs/ddd/`、`docs/notes/architecture.md`、ADR-010、`docs/team.md`、`docs/team-agreement.md` 已合入
- [ ] `docs/sprint0/environment.md` 有全体成员的环境记录
- [ ] v0.1.0 标签与 Release 存在
- [ ] 现场演示：注册 → 登录 → 课程列表 → 加入课程 → Swagger UI

## 6. 需要其他成员补的两件事

- @yttt697 / @fxs0917：在 `docs/sprint0/environment.md` 补自己的版本与启动结果；
- 每人按 T0-10 在 `docs/team.md` 确认姓名/角色，并完成一次破冰 PR（#28 是 @xy1897 的）。
