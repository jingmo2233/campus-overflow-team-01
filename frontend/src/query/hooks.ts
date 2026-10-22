import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api';
import { qk, type QuestionListFilter } from '@/query/keys';
import type { Page, QuestionSummary, SearchResult } from '@/types';

/** 筛选条件是否构成一次搜索；全默认时走普通列表接口。 */
function isSearching(f: QuestionListFilter): boolean {
  return f.keyword.trim() !== '' || !!f.tag || f.unanswered || f.sort !== 'newest';
}

export function useQuestionList(filter: QuestionListFilter) {
  return useQuery({
    queryKey: qk.questions(filter),
    queryFn: (): Promise<Page<QuestionSummary | SearchResult>> =>
      isSearching(filter)
        ? api.search(filter.keyword.trim(), {
            tag: filter.tag,
            sort: filter.sort,
            unanswered: filter.unanswered,
          })
        : api.questions(1),
  });
}

export function useQuestion(id: number) {
  return useQuery({
    queryKey: qk.question(id),
    queryFn: () => api.question(id),
    enabled: Number.isFinite(id),
  });
}

export function useRelatedQuestions(id: number) {
  return useQuery({
    queryKey: qk.related(id),
    queryFn: () => api.related(id),
    enabled: Number.isFinite(id),
  });
}

export function usePopularTags() {
  return useQuery({
    queryKey: qk.popularTags,
    queryFn: () => api.popularTags(),
    staleTime: 5 * 60_000, // 热门标签变化慢，缓存久一点
  });
}

export function useLeaderboard(period: string) {
  return useQuery({
    queryKey: qk.leaderboard(period),
    queryFn: () => api.leaderboard(period),
  });
}

export function useNotifications() {
  return useQuery({
    queryKey: qk.notifications,
    queryFn: () => api.notifications(),
  });
}

/** 未读数在顶栏常驻，靠轮询保持新鲜；未登录时不发请求。 */
export function useUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: qk.unreadCount,
    queryFn: () => api.unreadCount(),
    enabled,
    refetchInterval: 30_000,
  });
}

/**
 * 详情页的写操作共用这个工厂：成功后让该问题的详情与列表失效。
 * 投票/采纳/认证会改变列表里的票数与「已采纳」标记，所以列表也要一起刷新。
 */
function useQuestionMutation<TArgs>(fn: (args: TArgs) => Promise<unknown>, questionId: number) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: qk.question(questionId) }),
        client.invalidateQueries({ queryKey: ['questions'] }),
      ]);
    },
  });
}

export function useVote(questionId: number) {
  return useQuestionMutation(
    (v: { targetType: 'QUESTION' | 'ANSWER'; targetId: number; value: number }) =>
      api.vote(v.targetType, v.targetId, v.value),
    questionId,
  );
}

export function useSubmitAnswer(questionId: number) {
  return useQuestionMutation((body: string) => api.answer(questionId, body), questionId);
}

export function useAcceptAnswer(questionId: number) {
  return useQuestionMutation((answerId: number) => api.accept(questionId, answerId), questionId);
}

export function useEndorseAnswer(questionId: number) {
  return useQuestionMutation((answerId: number) => api.endorse(answerId), questionId);
}

export function useOpenBounty(questionId: number) {
  return useQuestionMutation(
    (v: { points: number; days: number }) => api.bounty(questionId, v.points, v.days),
    questionId,
  );
}

export function usePostComment(questionId: number) {
  return useQuestionMutation(
    (v: { targetType: 'QUESTION' | 'ANSWER'; targetId: number; body: string; parentId?: number }) =>
      api.comment(v.targetType, v.targetId, v.body, v.parentId),
    questionId,
  );
}

/** 发布问题成功后让列表失效，返回新问题 id 供页面跳转。 */
export function useAskQuestion() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (body: { courseId?: number; title: string; body: string; tags: string[] }) => api.ask(body),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['questions'] });
    },
  });
}

export function useMarkAllRead() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: () => api.readAll(),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: qk.notifications }),
        client.invalidateQueries({ queryKey: qk.unreadCount }),
      ]);
    },
  });
}
