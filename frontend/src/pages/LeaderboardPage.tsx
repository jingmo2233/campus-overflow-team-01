import { useState } from 'react';
import { Alert, Card, Table, Tabs, Tag, Typography } from 'antd';
import { CheckCircleTwoTone } from '@ant-design/icons';
import { describeError } from '@/errors';
import { useLeaderboard } from '@/query/hooks';
import type { LeaderboardEntry } from '@/types';

const { Title } = Typography;

const PERIODS = [
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'term', label: '本学期' },
  { key: 'all', label: '总榜' },
];

export function LeaderboardPage() {
  const [period, setPeriod] = useState('week');
  const { data: rows = [], error, isPending } = useLeaderboard(period);

  return (
    <Card className="page-card">
      <Title level={3}>声誉排行榜</Title>
      <Tabs activeKey={period} onChange={setPeriod} items={PERIODS} />

      {error && <Alert type="error" showIcon message={describeError(error)} className="page-alert" />}

      <Table<LeaderboardEntry>
        rowKey="userId"
        loading={isPending}
        dataSource={rows}
        pagination={false}
        locale={{ emptyText: '这个周期还没有数据' }}
        columns={[
          { title: '名次', dataIndex: 'rank', width: 80, align: 'center' },
          {
            title: '用户',
            dataIndex: 'displayName',
            render: (name: string, row) => (
              <>
                {name}
                {row.verified && (
                  <Tag className="verified" icon={<CheckCircleTwoTone twoToneColor="#52c41a" />}>
                    已认证
                  </Tag>
                )}
              </>
            ),
          },
          { title: '声誉', dataIndex: 'score', width: 120, align: 'right' },
        ]}
      />
    </Card>
  );
}
