import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, Badge, Button, ConfigProvider, Layout, Menu, Space, Spin, Tag, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider, useAuth } from '@/auth';
import { queryClient } from '@/query/client';
import { useUnreadCount } from '@/query/hooks';
import { QuestionListPage } from '@/pages/QuestionListPage';
import { QuestionDetailPage } from '@/pages/QuestionDetailPage';
import { AskPage } from '@/pages/AskPage';
import { LoginPage } from '@/pages/LoginPage';
import { LeaderboardPage } from '@/pages/LeaderboardPage';
import { NotificationsPage } from '@/pages/NotificationsPage';

const { Header, Content } = Layout;

const ROLE_LABEL: Record<string, string> = {
  STUDENT: '学生',
  TEACHER: '教师',
  ADMIN: '管理员',
};

function AppHeader() {
  const { me, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { data: unread } = useUnreadCount(!!me);
  const unreadCount = unread?.count ?? 0;

  const items = [
    { key: '/', label: <Link to="/">问题</Link> },
    { key: '/leaderboard', label: <Link to="/leaderboard">排行榜</Link> },
    ...(me
      ? [
          {
            key: '/notifications',
            label: (
              <Link to="/notifications">
                <Badge count={unreadCount} size="small" offset={[8, -2]}>
                  <span className="nav-text">通知</span>
                </Badge>
              </Link>
            ),
          },
        ]
      : []),
  ];

  return (
    <Header className="topbar">
      <Link className="brand" to="/">
        CampusOverflow
      </Link>
      <Menu mode="horizontal" theme="dark" selectedKeys={[pathname]} items={items} className="topnav" />
      {me ? (
        <Space>
          <span className="username">{me.displayName}</span>
          <Tag color={me.role === 'STUDENT' ? 'default' : 'gold'}>{ROLE_LABEL[me.role] ?? me.role}</Tag>
          <Button
            size="small"
            onClick={() => {
              void logout().then(() => navigate('/'));
            }}
          >
            退出
          </Button>
        </Space>
      ) : (
        <Link to="/login">
          <Button type="primary">登录 / 注册</Button>
        </Link>
      )}
    </Header>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const { me, loading } = useAuth();
  if (loading) return <Spin tip="加载中…" fullscreen />;
  return me ? children : <Navigate to="/login" replace />;
}

export function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{ algorithm: theme.defaultAlgorithm, token: { colorPrimary: '#1677ff', borderRadius: 6 } }}
    >
      {/* AntApp 提供 message/modal 的上下文，静态方法才能继承 ConfigProvider 的主题与语言 */}
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AuthProvider>
              <Layout className="app-layout">
                <AppHeader />
                <Content className="app-content">
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
                </Content>
              </Layout>
            </AuthProvider>
          </BrowserRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  );
}
