import { Link } from 'react-router-dom';
import { Alert, App, Badge, Button, Card, List, Typography } from 'antd';
import { describeError } from '@/errors';
import { useMarkAllRead, useNotifications } from '@/query/hooks';

const { Title, Text } = Typography;

export function NotificationsPage() {
  const { data, error, isPending } = useNotifications();
  const { message } = App.useApp();
  const markAllRead = useMarkAllRead();
  const items = data?.items ?? [];
  const unread = items.filter((n) => !n.read).length;

  return (
    <Card className="page-card">
      <div className="page-head">
        <Title level={3}>通知</Title>
        <Button
          loading={markAllRead.isPending}
          disabled={unread === 0}
          onClick={() =>
            markAllRead.mutate(undefined, {
              onSuccess: ({ updated }) => void message.success(`已标记 ${updated} 条为已读`),
              onError: (e) => void message.error(describeError(e)),
            })
          }
        >
          全部标记为已读
        </Button>
      </div>

      {error && <Alert type="error" showIcon message={describeError(error)} className="page-alert" />}

      <List
        loading={isPending}
        dataSource={items}
        locale={{ emptyText: '暂时没有通知' }}
        renderItem={(n) => (
          <List.Item key={n.id} className={n.read ? 'read' : ''}>
            <List.Item.Meta
              avatar={<Badge dot={!n.read} />}
              title={<Link to={n.link}>{n.title}</Link>}
              description={<Text type="secondary">{new Date(n.createdAt).toLocaleString('zh-CN')}</Text>}
            />
          </List.Item>
        )}
      />
    </Card>
  );
}
