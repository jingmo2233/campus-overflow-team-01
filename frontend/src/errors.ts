/**
 * 业务错误码 → 中文提示（PB-19 验收标准 ②：前端按 errors.ts 显示中文提示，不显示裸 500）。
 *
 * 设计原则：
 * 1. **`code` 才是契约**，不靠匹配后端 `detail` 文案——文案会随产品调整，错误码不会。
 *    后端 `docs/错误码.md` 明确要求「不要靠匹配错误消息文本判断」。
 * 2. 已知码在这里给出**前端定稿的文案**，措辞面向用户操作（"怎么办"），
 *    而不是照抄后端的开发视角描述（"哪里抛的什么异常"）。
 * 3. 未登记的码回落到后端 `detail`，再回落到这里的分段兜底，最后才是通用文案。
 *    这样后端新增错误码时前端不会崩，只是暂时用后端文案。
 *
 * 维护约定：后端在 `docs/错误码.md` 新增码时，这里同步加一条；两者必须一一对应。
 * 当前覆盖：后端源码实际会抛出的 68 个码 + 前端自定义的 `SYS-NETWORK`。
 * 注意：`docs/错误码.md` 目前只登记了 30 个，另有 38 个已在源码里使用但文档未登记
 * （QA-4001~4004、REP-1001/1005/1006/2001/2002/3001、DIS-1002/3001/4001、
 * ID- 系列 20 个、AUTH-403、SYS-400/400-VALIDATION/500），
 * 本表按源码里的抛出条件补全；文档补登需由 PO 安排。
 */

/** Q&A 上下文（co-qa）—— 对应 docs/错误码.md 的 1xxx 业务规则段 */
const QA_RULES: Record<string, string> = {
  'QA-1001': '标题长度需在 10–150 字之间',
  'QA-1002': '内容长度不符合要求：问题正文至少 20 字，回答至少 10 字，评论 1–600 字',
  'QA-1004': '标签格式不正确，不能为空，且只能包含中英文、数字与 # + . - _',
  'QA-1005': '每个问题需要 1–5 个标签',
  'QA-1006': '投票值只能是赞成或反对',
  'QA-1007': '评论回复只支持一层，不能再回复二级评论',
  'QA-1008': '这条内容已被删除，无法继续操作',
  'QA-1009': '该回答不属于当前问题',
  'QA-1010': '这个问题已有回答，只有课程教师或助教可以删除',
  'QA-1011': '这条回答已被采纳，只有课程教师或助教可以删除',
  'QA-1012': '回复内容与被回复的评论不一致',
  'QA-1015': '不能给自己的内容投票',
};

/** Q&A 上下文 —— 2xxx 状态冲突段 */
const QA_CONFLICT: Record<string, string> = {
  'QA-2002': '这个问题已经采纳过最佳答案了',
  'QA-2003': '这条回答已经被认证过了',
};

/** Q&A 上下文 —— 3xxx 权限段 */
const QA_FORBIDDEN: Record<string, string> = {
  'QA-3001': '只有提问者或课程教师/助教可以采纳答案',
  'QA-3002': '只有作者或课程教师/助教可以编辑',
  'QA-3003': '只有作者或课程教师/助教可以删除',
  'QA-3004': '只有课程教师/助教可以认证回答',
};

/** Q&A 上下文 —— 4xxx 未找到段（后端 NotFoundException 抛出，此前文档未登记，实现里实际在用） */
const QA_NOT_FOUND: Record<string, string> = {
  'QA-4001': '问题不存在或已被删除',
  'QA-4002': '回答不存在或已被删除',
  'QA-4003': '关联的课程不存在',
  'QA-4004': '被回复的评论不存在',
};

/** Reputation 上下文 —— 1xxx 业务规则段 */
const REP_RULES: Record<string, string> = {
  'REP-1001': '这个问题已删除或已采纳，无法再发起悬赏',
  // 后端这个码有两条不同语义的 detail（可用积分不足 / 冻结积分不足），前端只能取通用表述
  'REP-1002': '积分不足，无法完成该操作',
  'REP-1003': '悬赏积分需在 10–500 之间',
  'REP-1004': '悬赏期限需在 1–14 天之间',
  'REP-1005': '不能赢取自己发起的悬赏',
  'REP-1006': '积分数量必须为正数',
};

/** Reputation 上下文 —— 2xxx 冲突段 */
const REP_CONFLICT: Record<string, string> = {
  'REP-2001': '该悬赏已经结束，不能再次结算或关闭',
  'REP-2002': '这个问题已有进行中的悬赏',
};

/** Reputation 上下文 —— 3xxx 权限段 */
const REP_FORBIDDEN: Record<string, string> = {
  'REP-3001': '只有课程教师或助教可以发起悬赏',
  'REP-3002': '只能查看自己的声誉流水',
};

/** Discovery 上下文 */
const DISCOVERY: Record<string, string> = {
  'DIS-1001': '搜索关键词请至少输入 2 个字符',
  'DIS-1002': '搜索关键词不能超过 50 个字符',
  'DIS-3001': '只能把自己的通知标记为已读',
  'DIS-4001': '该通知不存在或已被删除',
};

/** Identity 上下文 —— 1xxx 业务规则段（注册、资料、课程的字段校验） */
const ID_RULES: Record<string, string> = {
  'ID-1001': '学号/工号应为 4–20 位字母或数字',
  'ID-1002': '昵称需为 2–20 个字符，且不能包含 @ 或空格',
  'ID-1003': '邮箱格式不正确',
  'ID-1004': '密码需为 8–64 位，且同时包含字母和数字',
  'ID-1005': '学生账号请通过注册流程创建',
  'ID-1006': '不能修改自己的角色',
  'ID-1007': '不能禁用自己的账号',
  'ID-1010': '课程代码应为 2–20 位字母、数字或连字符',
  'ID-1011': '课程名称需为 2–50 个字符',
  'ID-1012': '学期需为 2–20 个字符',
  'ID-1013': '不能修改课程教师的角色',
};

/** Identity 上下文 —— 2xxx 冲突段 */
const ID_CONFLICT: Record<string, string> = {
  'ID-2001': '该学号/工号已被注册，如已注册请直接登录',
  'ID-2002': '该昵称已被使用，换一个试试',
  'ID-2003': '该邮箱已被使用，换一个试试',
  'ID-2004': '该学期已存在相同代码的课程',
  'ID-2005': '你已加入该课程，无需重复加入',
  'ID-2006': '该用户已完成认证，无需重复提交',
};

/** Identity 上下文 —— 3xxx 权限段 */
const ID_FORBIDDEN: Record<string, string> = {
  'ID-3001': '仅管理员可执行该操作',
  'ID-3002': '仅教师或管理员可以创建课程',
  'ID-3003': '只有课程教师或管理员可以调整成员角色',
};

/** Identity 上下文 —— 4xxx 未找到段 */
const ID_NOT_FOUND: Record<string, string> = {
  'ID-4001': '用户不存在',
  'ID-4002': '课程不存在',
  'ID-4003': '该用户不是这门课程的成员',
};

/** 认证与平台级 */
const PLATFORM: Record<string, string> = {
  'AUTH-401': '登录状态已失效，请重新登录',
  'AUTH-403': '你没有权限执行该操作',
  'AUTH-403-CSRF': '页面令牌已过期，请刷新页面后重试',
  'AUTH-429': '登录失败次数过多，请 1 分钟后再试',
  'SYS-NETWORK': '网络连接失败，请检查网络后重试',
  'SYS-400': '请求参数不合法，请检查填写内容',
  'SYS-400-VALIDATION': '填写内容有误，请检查标红的字段',
  'SYS-409-CONCURRENT': '内容已被他人修改，请刷新后重试',
  'SYS-409-DUPLICATE': '操作冲突（可能是重复提交），请刷新后重试',
  'SYS-500': '服务器开小差了，请稍后重试',
};

export const ERROR_TEXT: Record<string, string> = {
  ...QA_RULES,
  ...QA_CONFLICT,
  ...QA_FORBIDDEN,
  ...QA_NOT_FOUND,
  ...REP_RULES,
  ...REP_CONFLICT,
  ...REP_FORBIDDEN,
  ...DISCOVERY,
  ...ID_RULES,
  ...ID_CONFLICT,
  ...ID_FORBIDDEN,
  ...ID_NOT_FOUND,
  ...PLATFORM,
};

/**
 * 按错误码前缀给出的兜底文案。
 * 用于「后端新增了码但前端还没登记」的情况：至少让用户知道是哪一块出了问题，
 * 而不是弹一句莫名其妙的英文或者裸 500。
 */
const PREFIX_FALLBACK: { prefix: string; text: string }[] = [
  { prefix: 'QA-', text: '问答操作未能完成，请检查填写内容后重试' },
  { prefix: 'REP-', text: '声誉/悬赏操作未能完成，请稍后重试' },
  { prefix: 'DIS-', text: '检索操作未能完成，请换个关键词试试' },
  { prefix: 'ID-', text: '账号操作未能完成，请检查填写内容后重试' },
  { prefix: 'AUTH-', text: '登录状态异常，请重新登录' },
  { prefix: 'SYS-', text: '服务暂时不可用，请稍后重试' },
];

/** 查表：已知码 → 本地文案；未知码 → 前缀兜底；都没有 → undefined（调用方再决定回落） */
export function textForCode(code: string | undefined): string | undefined {
  if (!code) return undefined;
  const known = ERROR_TEXT[code];
  if (known) return known;
  const hit = PREFIX_FALLBACK.find((p) => code.startsWith(p.prefix));
  return hit?.text;
}
