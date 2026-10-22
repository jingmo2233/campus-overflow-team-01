import { Link } from 'react-router-dom';
import { errorText } from '@/api';
import { useMarkAllRead, useNotifications } from '@/query/hooks';

export function NotificationsPage() {
  const { data, error, isPending } = useNotifications();
  const markAllRead = useMarkAllRead();
  const items = data?.items ?? [];

  return (
    <div className="form-page">
      <h1>通知</h1>
      <button disabled={markAllRead.isPending || items.length === 0} onClick={() => markAllRead.mutate()}>
        全部标记为已读
      </button>
      {error && <p className="error">{errorText(error)}</p>}
      <ul className="plain">
        {items.map((n) => (
          <li key={n.id} className={n.read ? 'muted' : ''}>
            <Link to={n.link}>{n.title}</Link>
            <span className="muted"> · {new Date(n.createdAt).toLocaleString('zh-CN')}</span>
          </li>
        ))}
        {isPending && <li className="muted">加载中…</li>}
        {!isPending && items.length === 0 && <li className="muted">暂时没有通知。</li>}
      </ul>
    </div>
  );
}
