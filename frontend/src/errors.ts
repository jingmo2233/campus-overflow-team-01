import { ApiError } from '@/api';

/**
 * 业务码 → 面向用户的提示文案。
 *
 * 取自 `docs/错误码.md`。按《团队章程》4.3 的约定，前端一律用 `code` 分支，
 * 绝不匹配后端的 `detail` 文案——后端改一次措辞就会让前端的判断全部失效。
 *
 * 这里的文案刻意比后端的更具操作性：后端说「标题长度不合法」，
 * 用户需要知道的是「10–150 字」以及现在差多少。
 */
const MESSAGES: Record<string, string> = {
  // —— Identity ——
  'ID-1004': '密码需 8–64 位，且同时包含字母与数字',
  'ID-2001': '该学号/工号或邮箱已被注册，换一个或直接登录',

  // —— Q&A 业务规则（422）——
  'QA-1001': '标题需 10–150 字，请把问题写得更具体一些',
  'QA-1002': '正文长度不合法：问题至少 20 字，回答至少 10 字，评论 1–600 字',
  'QA-1004': '标签格式不正确，只允许中英文、数字与 # + . - _',
  'QA-1005': '标签需要 1–5 个（重复的会自动去重）',
  'QA-1006': '投票值只能是 +1 或 -1',
  'QA-1007': '评论回复只支持一层，不能再回复二级评论',
  'QA-1008': '该内容已被删除，无法继续操作',
  'QA-1009': '这个回答不属于当前问题',
  'QA-1010': '已有回答的问题只能由课程教师或助教删除',
  'QA-1011': '已被采纳的回答只能由课程教师或助教删除',
  'QA-1012': '回复必须与被回复的评论挂在同一内容下',
  'QA-1015': '不能给自己的内容投票',

  // —— Q&A 冲突（409）——
  'QA-2002': '该问题已经采纳过回答了',
  'QA-2003': '该回答已被认证，无需重复认证',

  // —— Q&A 权限（403）——
  'QA-3001': '只有提问者本人或课程教师/助教可以采纳回答',
  'QA-3002': '只有作者本人或课程教师/助教可以编辑',
  'QA-3003': '只有作者本人或课程教师/助教可以删除',
  'QA-3004': '只有课程教师或助教可以认证回答',

  // —— Reputation ——
  'REP-1002': '可用悬赏积分不足',
  'REP-1003': '悬赏积分需在 10–500 之间',
  'REP-1004': '悬赏期限需在 1–14 天之间',
  'REP-3002': '只能查看自己的声誉流水',

  // —— 认证与 CSRF ——
  'AUTH-401': '登录状态已失效，请重新登录',
  'AUTH-403-CSRF': '安全令牌已过期，请刷新页面后重试',
  'AUTH-429': '登录失败次数过多，请 1 分钟后再试',

  // —— 平台级 ——
  'SYS-409-CONCURRENT': '这条内容刚被其他人修改过，请刷新后重试',
  'SYS-409-DUPLICATE': '操作重复提交了，请勿连续点击',
  'SYS-400-VALIDATION': '提交的内容有字段不合法，请检查后重试',
};

/**
 * 把异常转成用户可读的提示。
 *
 * 未登记的业务码回退到后端的 `detail`，而不是一句笼统的「操作失败」——
 * 后端文案至少还有信息量，同时在控制台留一条记录，便于补齐映射表。
 */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const known = MESSAGES[error.code];
    if (known) return known;
    console.warn(`[errors] 未登记的业务码 ${error.code}，回退到后端文案`);
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return '网络异常，请稍后重试';
}

/** 取业务码，用于需要按码分支的场景（如把校验错误定位到具体表单项）。 */
export function errorCode(error: unknown): string | undefined {
  return error instanceof ApiError ? error.code : undefined;
}

/** 该错误是否应归咎于某个表单字段（而非全局提示）。 */
export function fieldOf(error: unknown): 'title' | 'body' | 'tags' | undefined {
  switch (errorCode(error)) {
    case 'QA-1001':
      return 'title';
    case 'QA-1002':
      return 'body';
    case 'QA-1004':
    case 'QA-1005':
      return 'tags';
    default:
      return undefined;
  }
}
