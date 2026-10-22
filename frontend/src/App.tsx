import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider, useAuth } from '@/auth';
import { queryClient } from '@/query/client';
import { useUnreadCount } from '@/query/hooks';
import { QuestionListPage } from '@/pages/QuestionListPage';
import { QuestionDetailPage } from '@/pages/QuestionDetailPage';
import { AskPage } from '@/pages/AskPage';
import { LoginPage } from '@/pages/LoginPage';
import { LeaderboardPage } from '@/pages/LeaderboardPage';
import { NotificationsPage } from '@/pages/NotificationsPage';

function Header() {
  const { me, logout } = useAuth();
  const navigate = useNavigate();
  const { data: unread } = useUnreadCount(!!me);
  const unreadCount = unread?.count ?? 0;

  return (
    <header className="topbar">
      <Link className="brand" to="/">
        CampusOverflow
      </Link>
      <nav>
        <Link to="/">问题</Link>
        <Link to="/leaderboard">排行榜</Link>
        {me && (
          <Link to="/notifications">
            通知{unreadCount > 0 && <span className="badge">{unreadCount}</span>}
          </Link>
        )}
      </nav>
      <div className="spacer" />
      {me ? (
        <div className="user">
          <span>{me.displayName}</span>
          <span className="role">{me.role}</span>
          <button
            onClick={() => {
              void logout().then(() => navigate('/'));
            }}
          >
            退出
          </button>
        </div>
      ) : (
        <Link className="primary" to="/login">
          登录 / 注册
        </Link>
      )}
    </header>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const { me, loading } = useAuth();
  if (loading) return <p className="muted">加载中…</p>;
  return me ? children : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Header />
          <main>
            <Routes>
              <Route path="/" element={<QuestionListPage />} />
              <Route path="/questions/:id" element={<QuestionDetailPage />} />
              <Route
                path="/ask"
                element={
                  <RequireAuth>
                    <AskPage />
                  </RequireAuth>
                }
              />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/leaderboard" element={<LeaderboardPage />} />
              <Route
                path="/notifications"
                element={
                  <RequireAuth>
                    <NotificationsPage />
                  </RequireAuth>
                }
              />
            </Routes>
          </main>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
