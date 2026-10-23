import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Card, Checkbox, Empty, Input, List, Select, Space, Statistic, Tag } from 'antd';
import { CheckCircleTwoTone, EditOutlined } from '@ant-design/icons';
import { useAuth } from '@/auth';
import { describeError } from '@/errors';
import { useDebounced } from '@/hooks/useDebounced';
import { usePopularTags, useQuestionList } from '@/query/hooks';

const SORTS = [
  { value: 'newest', label: '最新' },
  { value: 'active', label: '最近活跃' },
  { value: 'hot', label: '得分最高' },
  { value: 'relevance', label: '相关度' },
];

export function QuestionListPage() {
  const { me } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [activeTag, setActiveTag] = useState<string | undefined>();
  const [sort, setSort] = useState('newest');
  const [unanswered, setUnanswered] = useState(false);

  const debouncedKeyword = useDebounced(keyword);
  const { data, error, isPending } = useQuestionList({
    keyword: debouncedKeyword,
    tag: activeTag,
    sort,
    unanswered,
  });
  const { data: tags = [] } = usePopularTags();

  return (
    <div className="layout">
      <section className="layout-main">
        <Card size="small" className="toolbar-card">
          <Space wrap>
            <Input.Search
              allowClear
              placeholder="搜索问题（支持中文全文检索）"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              style={{ width: 280 }}
            />
            <Select value={sort} onChange={setSort} options={SORTS} style={{ width: 140 }} />
            <Checkbox checked={unanswered} onChange={(e) => setUnanswered(e.target.checked)}>
              仅看未回答
            </Checkbox>
            {me && (
              <Link to="/ask">
                <Button type="primary" icon={<EditOutlined />}>
                  我要提问
                </Button>
              </Link>
            )}
          </Space>
        </Card>

        {activeTag && (
          <Space className="filter-bar">
            <span>标签筛选：</span>
            <Tag closable color="blue" onClose={() => setActiveTag(undefined)}>
              {activeTag}
            </Tag>
          </Space>
        )}

        {error && <Alert type="error" showIcon message={describeError(error)} className="page-alert" />}

        <List
          loading={isPending}
          dataSource={data?.items ?? []}
          locale={{ emptyText: <Empty description="没有匹配的问题，换个关键词试试" /> }}
          renderItem={(q) => (
            <List.Item key={q.id} className="qitem">
              <div className="qstats">
                <Statistic title="票数" value={q.score} />
                <Statistic
                  title="回答"
                  value={q.answerCount}
                  prefix={q.accepted ? <CheckCircleTwoTone twoToneColor="#52c41a" /> : null}
                />
                <Statistic title="浏览" value={q.viewCount} />
              </div>
              <List.Item.Meta
                title={
                  <Link className="qtitle" to={`/questions/${q.id}`}>
                    {q.title}
                  </Link>
                }
                description={
                  <>
                    <p className="excerpt">{q.excerpt}</p>
                    <Space wrap size={[4, 4]}>
                      {q.tags.map((t) => (
                        <Tag key={t} color="blue" className="clickable" onClick={() => setActiveTag(t)}>
                          {t}
                        </Tag>
                      ))}
                      <span className="meta">
                        {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')}
                      </span>
                    </Space>
                  </>
                }
              />
            </List.Item>
          )}
        />
      </section>

      <aside className="layout-side">
        <Card title="热门标签" size="small">
          <Space wrap size={[4, 8]}>
            {tags.map((t) => (
              <Tag key={t.name} className="clickable" onClick={() => setActiveTag(t.name)}>
                {t.name} <span className="count">{t.questionCount}</span>
              </Tag>
            ))}
            {tags.length === 0 && <span className="meta">暂无标签</span>}
          </Space>
        </Card>
      </aside>
    </div>
  );
}
