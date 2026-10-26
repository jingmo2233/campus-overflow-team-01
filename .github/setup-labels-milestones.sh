#!/usr/bin/env bash
# 一次性创建指导书 V2 第 7.4 节约定的标签集与 Sprint Milestones。
#
#   gh auth login                       # 首次需要
#   bash .github/setup-labels-milestones.sh
#
# 重复执行安全：标签已存在则更新颜色/描述，Milestone 已存在则跳过。
set -euo pipefail

REPO="${REPO:-jingmo2233/campus-overflow-team-01}"

command -v gh >/dev/null || {
  echo "需要 GitHub CLI：winget install -e --id GitHub.cli" >&2
  exit 1
}

label() { # name color description
  gh label create "$1" --repo "$REPO" --color "$2" --description "$3" --force >/dev/null
  echo "  label  $1"
}

echo "== 标签：type: =="
label "type:story"      "0E8A16" "有业务价值的需求（PBI）"
label "type:task"       "1D76DB" "Story 下的技术任务"
label "type:bug"        "D73A4A" "行为与预期不符"
label "type:tech-debt"  "FBCA04" "技术债，需要在后续 Sprint 偿还"
label "type:spike"      "5319E7" "技术调研，产出结论而非功能"
label "type:docs"       "0075CA" "文档、ADR、交付说明"

echo "== 标签：ctx: =="
label "ctx:identity"    "C5DEF5" "Identity 身份与课程"
label "ctx:qa"          "C5DEF5" "Q&A 问答核心"
label "ctx:reputation"  "C5DEF5" "Reputation 声誉激励"
label "ctx:discovery"   "C5DEF5" "Discovery 内容发现"
label "ctx:frontend"    "C5DEF5" "前端"
label "ctx:infra"       "C5DEF5" "CI/CD、部署、工程基础设施"

echo "== 标签：priority:（MoSCoW）=="
label "priority:must"   "B60205" "本 Sprint 必须完成"
label "priority:should" "D93F0B" "应当完成，可让位于 must"
label "priority:could"  "FEF2C0" "有余力再做"
label "priority:wont"   "EEEEEE" "本次明确不做"

echo "== 标签：status: =="
label "status:blocked"          "000000" "被阻塞，站会重点讨论"
label "status:needs-discussion" "D4C5F9" "需求或方案待澄清"

echo "== Milestones（= Sprint）=="
milestone() { # title due description
  if gh api "repos/$REPO/milestones?state=all" --jq '.[].title' | grep -qx "$1"; then
    echo "  milestone  $1（已存在，跳过）"
  else
    gh api "repos/$REPO/milestones" -f title="$1" -f due_on="$2" -f description="$3" >/dev/null
    echo "  milestone  $1"
  fi
}

# ⚠️ 日期按「第 1 周 = 2026-10-26」暂定；如校历不同，改这里后重跑（已存在的 Milestone 不会更新 due date，
#    需在 GitHub UI 手工改，或先删后建）。
milestone "Sprint 0" "2026-11-01T15:59:59Z" "启动：团队、仓库、环境、CI 与 Backlog 就绪，能注册/登录并加入课程（v0.1.0）。2026-10-26 ~ 2026-11-01"
milestone "Sprint 1" "2026-11-15T15:59:59Z" "问答核心：提问→回答→投票→评论→采纳 的完整闭环（v0.2.0）。2026-11-02 ~ 2026-11-15"
milestone "Sprint 2" "2026-11-29T15:59:59Z" "声誉激励：问答行为异步、幂等地转化为声誉、徽章、悬赏与排行榜（v0.3.0）。2026-11-16 ~ 2026-11-29"
milestone "Sprint 3" "2026-12-13T15:59:59Z" "内容发现：搜索、相关推荐、通知，端到端链路全绿（v0.9.0 RC）。2026-11-30 ~ 2026-12-13"
milestone "Sprint 4" "2026-12-20T15:59:59Z" "发布：稳定化、体验打磨、可观测，v1.0 发布与最终答辩（v1.0.0）。2026-12-14 ~ 2026-12-20"

# 48 个 PBI 已按 docs/backlog/product-backlog-v2.md 导入（Issue #30～#77）。
# 如需重建，可先导出 Issue 列表，再用 gh issue create 逐条创建；不要重复创建同标题 Issue。

echo
echo "完成。GitHub 默认标签（bug / enhancement / question 等）与本方案重复，"
echo "建议在 Issues → Labels 页面手工删除，避免两套并存。"
