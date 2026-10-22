/** 搜索与列表共用的筛选条件。 */
export interface QuestionListFilter {
  keyword: string;
  tag?: string;
  sort: string;
  unanswered: boolean;
}

/**
 * 查询键集中定义，避免各页面手写字符串导致失效（invalidate）时对不上。
 *
 * 层级约定：`['question', id]` 是详情，`['question', id, 'related']` 是其子资源，
 * 因此 invalidate `['question', id]` 会连同相关问题一起刷新。
 */
export const qk = {
  me: ['me'] as const,
  questions: (filter: QuestionListFilter) => ['questions', filter] as const,
  question: (id: number) => ['question', id] as const,
  related: (id: number) => ['question', id, 'related'] as const,
  popularTags: ['tags', 'popular'] as const,
  notifications: ['notifications'] as const,
  unreadCount: ['notifications', 'unread'] as const,
  leaderboard: (period: string) => ['leaderboard', period] as const,
};
