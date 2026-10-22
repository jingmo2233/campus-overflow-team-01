import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api';

/**
 * 全局查询客户端。
 *
 * 重试策略刻意区分 4xx 与 5xx：后端的业务错误（未登录、校验失败、权限不足）
 * 都以 4xx + 业务码返回，重试不会改变结果，只会放大无效请求。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (failureCount, error) => {
        if (error instanceof ApiError && error.status < 500) return false;
        return failureCount < 2;
      },
    },
    mutations: {
      retry: false,
    },
  },
});
