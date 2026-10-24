# Lab 5 交付物：前端骨架与对接

> 对应 PDF Lab 5（第 9 周，Sprint 2）。本文件把指导书的提交物逐条映射到仓库中的实际产物。
> 后端侧的验收见 [`docs/lab4/README.md`](../lab4/README.md)。

| Lab 5 要求 | 评分权重 | 本仓库产物 | 状态 |
| --- | --- | --- | --- |
| 工程结构规范（pnpm / 目录分层 / 别名） | 20 | `frontend/`，pnpm 12 锁版本，`@/` 别名 | ✅ |
| 严格 TypeScript | 25 | `strict` + `noUnusedLocals` + `noUnusedParameters`，ESLint 类型感知规则 | ✅ `pnpm typecheck` / `pnpm lint` 全绿 |
| Hooks 使用规范 | 25 | TanStack Query 收口服务端状态，8 查询 + 8 写操作 | ✅ 见第 3 节 |
| 端到端可用（提问链路 30 秒录屏） | 30 | `pnpm dev` + 后端联调，录屏见第 5 节 | ✅ |

---

## 1. 起前端

```bash
cd frontend
pnpm install          # 首次；CI 用 --frozen-lockfile
pnpm dev              # http://localhost:5173
```

Vite 已把 `/api` 代理到 `http://localhost:8080`，同源下会话 Cookie 与 CSRF 双提交都能正常工作，
**不需要配置 CORS**。后端启动方式见 `docs/lab4/README.md` 第 1 节。

> 包管理器由 `package.json` 的 `packageManager: pnpm@12.14.0` 锁定。
> 没有 pnpm 时用 `corepack enable pnpm` 即可，不要退回 npm——仓库里只有 `pnpm-lock.yaml`，
> `npm install` 读不到它，装出来的版本和大家不一致。

## 2. 质量命令

| 命令 | 作用 |
| --- | --- |
| `pnpm typecheck` | `tsc --noEmit`，严格模式 |
| `pnpm lint` | ESLint 9 flat config，`--max-warnings 0` |
| `pnpm format` | Prettier 统一风格 |
| `pnpm build` | `tsc -b && vite build` |

**pre-commit 钩子**：husky 装在仓库根（Git 钩子必须在 git 根目录，故根目录有一个只管钩子的
最小 `package.json`）。提交时由 lint-staged 只检查暂存文件：

```
src/**/*.{ts,tsx}  →  eslint --fix --max-warnings 0  +  prettier --write
src/**/*.css       →  prettier --write
```

没装前端依赖的同学（例如只改后端 Java）会被自动跳过，不阻塞提交。

**验证钩子真的生效**：提交一个含 `any` 的文件，应当被拒绝且 `HEAD` 不动。

## 3. 目录结构与分层

```
frontend/src
├── api.ts              HTTP 客户端：CSRF 双提交、Problem Details → ApiError
├── errors.ts           业务码 → 用户文案（见第 4 节）
├── types.ts            后端契约的 TS 类型
├── auth.tsx            登录态（基于 React Query 缓存）
├── query/
│   ├── client.ts       QueryClient：重试策略区分 4xx / 5xx
│   ├── keys.ts         查询键集中定义
│   └── hooks.ts        8 个查询 + 8 个写操作
├── hooks/useDebounced.ts
└── pages/              6 个页面
```

**分层约定**：页面不直接调用 `api.ts`，一律经过 `query/hooks.ts`。这样缓存失效的时机
集中在一处——例如投票成功后同时失效「该问题详情」与「问题列表」，因为列表里的票数和
采纳标记也会变。

**重试策略**（`query/client.ts`）刻意区分状态码：后端的业务错误（未登录、校验失败、
权限不足）都是 4xx + 业务码，重试不会改变结果，只会放大无效请求，因此直接不重试；
5xx 才重试 2 次。

## 4. 错误体验：按业务码给提示

`src/errors.ts` 按 [`docs/错误码.md`](../错误码.md) 建立了 30 个业务码的文案映射。

**为什么不直接显示后端的 `detail`**：按《团队章程》4.3，前端一律用 `code` 分支，
不匹配后端文案——后端改一次措辞就会让前端判断全部失效。另外前端文案可以更具操作性：
后端说「标题长度不合法」，用户需要知道的是「需 10–150 字」。

**校验错误会定位到具体表单项**。`fieldOf()` 把错误码映射到字段，再由
`form.setFields()` + `scrollToField()` 标注并滚动过去：

| 业务码 | 定位到 | 提示 |
| --- | --- | --- |
| `QA-1001` | 标题 | 标题需 10–150 字，请把问题写得更具体一些 |
| `QA-1002` | 正文 | 问题至少 20 字，回答至少 10 字，评论 1–600 字 |
| `QA-1004` / `QA-1005` | 标签 | 格式不正确 / 需要 1–5 个 |
| `ID-2001` | 学号 | 该学号或邮箱已被注册，换一个或直接登录 |

未登记的码回退到后端 `detail`，并在控制台打一条 warning 便于补齐映射表——
不回退成笼统的「操作失败」，那样会把仅有的信息也丢掉。

**可现场演示的几条**：

- 给自己的问题投票 → `QA-1015` → 「不能给自己的内容投票」
- 重复采纳 → `QA-2002` → 「该问题已经采纳过回答了」
- 非作者编辑 → `QA-3002` → 「只有作者本人或课程教师/助教可以编辑」

## 5. 提问链路录屏

**录制流程**（约 30 秒）：

| 时间 | 动作 | 画面要点 |
| --- | --- | --- |
| 0–3s | 首页 | 列表、三项统计、右侧热门标签 |
| 3–6s | 点「我要提问」 | 跳转 `/ask` |
| 6–18s | 填标题 / 正文 / 标签 | 字数计数器实时变化 |
| 18–22s | 点「发布问题」 | 按钮 loading → 「发布成功」提示 |
| 22–27s | 自动跳详情页 | 标题、正文、标签、投票区 |
| 27–30s | 返回首页 | **新问题出现在列表第一条** |

最后一步证明的是数据真的落库、且列表缓存正确失效，而不只是前端自己演了一遍。

> 标签输入框是 `mode="tags"`，**打完字要按回车**（或逗号/空格）才会变成标签。
> Windows 录屏：`Win + Shift + S` → 选摄像机图标；或 Xbox Game Bar `Win + G`。

## 6. UI 与构建

- **antd 6 + `zh_CN`**，`ConfigProvider` 统一主题 token，`AntApp` 提供 message/modal 上下文
- `styles.css` 只保留 antd 覆盖不到的部分：布局栅格、投票区排版、Markdown 正文排印
- **构建分块**：antd 单独成 chunk，业务代码从 1.14 MB 降到 **26.7 KB**，
  改业务不会让用户重下依赖。antd 自身 907 KB（gzip 293 KB）是已知体量。

**antd 6 的坑**：`Divider` 的 `orientation` 现在表示横/竖，标题位置要用 `titlePlacement`。
沿用 5.x 的 `orientation="left"` 会类型报错。

## 7. CI

`.github/workflows/ci.yml` 的 frontend job 已迁到 pnpm：

```yaml
- uses: pnpm/action-setup@v4          # 按根 package.json 的 packageManager 锁版本
- uses: actions/setup-node@v4
  with: { node-version: "20", cache: pnpm, cache-dependency-path: frontend/pnpm-lock.yaml }
- run: pnpm install --frozen-lockfile  # lockfile 与 package.json 不一致直接失败
- run: pnpm lint                       # 此前 CI 完全不跑 ESLint
- run: pnpm typecheck
- run: pnpm build
```

`deploy/frontend.Dockerfile` 同步改用 `corepack enable` + pnpm。
**注意**：镜像构建要一并拷贝 `pnpm-workspace.yaml`，esbuild 的 postinstall 白名单在里面，
缺了会报 `ERR_PNPM_IGNORED_BUILDS`。

## 8. 常见故障

| 现象 | 原因 / 处理 |
| --- | --- |
| `ERR_PNPM_IGNORED_BUILDS` | esbuild 的 postinstall 被拦。`pnpm-workspace.yaml` 里的 `allowBuilds` 负责放行（pnpm 12 的键名，旧版 `onlyBuiltDependencies` 已失效） |
| 前端请求全 404 | 后端没起，或端口不是 8080。代理目标在 `vite.config.ts` |
| 写操作 403 `AUTH-403-CSRF` | 刷新页面重新取 CSRF token |
| 标签填了却提示「至少 1 个标签」 | 没按回车确认，输入框里的文字还不是标签 |
| `corepack enable` 报 EPERM | 写 `C:\Program Files\nodejs` 需要管理员。改用 `corepack enable --install-directory "$HOME/.local/bin" pnpm` |
