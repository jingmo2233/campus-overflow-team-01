import { createContext, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api';
import { qk } from '@/query/keys';
import type { Me } from '@/types';

interface AuthState {
  me: Me | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  // 写请求需要 CSRF token，拿到之前先不探测登录态，避免首个请求必定 403
  const [csrfReady, setCsrfReady] = useState(false);
  const requested = useRef(false);

  useEffect(() => {
    if (requested.current) return; // StrictMode 下 effect 会跑两次
    requested.current = true;
    void api
      .csrf()
      .catch(() => undefined) // 拿不到也继续，由 /me 决定是否已登录
      .finally(() => setCsrfReady(true));
  }, []);

  const { data, isPending } = useQuery({
    queryKey: qk.me,
    // 401 表示未登录，是正常状态而非错误，统一折叠成 null
    queryFn: () => api.me().catch(() => null),
    enabled: csrfReady,
    staleTime: Infinity,
  });

  const value = useMemo<AuthState>(
    () => ({
      me: data ?? null,
      loading: !csrfReady || isPending,
      login: async (username, password) => {
        const me = await api.login(username, password);
        client.setQueryData(qk.me, me);
        // 换了身份，所有带权限视角的数据（myVote、permissions）都要重取
        await client.invalidateQueries();
      },
      logout: async () => {
        await api.logout();
        client.setQueryData(qk.me, null);
        client.clear(); // 避免下一个登录者看到上一个人的缓存
      },
      refresh: async () => {
        await client.invalidateQueries({ queryKey: qk.me });
      },
    }),
    [data, csrfReady, isPending, client],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// 与 AuthProvider 同文件导出 hook 会让 Fast Refresh 退化为整页刷新。
// 拆成单独文件需要导出 AuthContext，那样同样触发本规则，故在此豁免。
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth 必须在 AuthProvider 内使用');
  return ctx;
}
