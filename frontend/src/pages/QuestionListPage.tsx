import { useState } from 'react';
import { Link } from 'react-router-dom';
import { errorText } from '@/api';
import { useAuth } from '@/auth';
import { useDebounced } from '@/hooks/useDebounced';
import { usePopularTags, useQuestionList } from '@/query/hooks';

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
      <section>
        <div className="toolbar">
          <input
            placeholder="搜索问题（支持中文全文检索）"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <select value={sort} onChange={(e) => setSort(e.target.value)}>
            <option value="newest">最新</option>
            <option value="active">最近活跃</option>
            <option value="hot">得分最高</option>
            <option value="relevance">相关度</option>
          </select>
          <label className="check">
            <input type="checkbox" checked={unanswered} onChange={(e) => setUnanswered(e.target.checked)} />
            仅看未回答
          </label>
          {me && (
            <Link className="primary" to="/ask">
              我要提问
            </Link>
          )}
        </div>

        {activeTag && (
          <p className="muted">
            标签筛选：<b>{activeTag}</b> <button onClick={() => setActiveTag(undefined)}>清除</button>
          </p>
        )}
        {error && <p className="error">{errorText(error)}</p>}

        <ul className="qlist">
          {data?.items.map((q) => (
            <li key={q.id}>
              <div className="stats">
                <span>
                  <b>{q.score}</b> 票
                </span>
                <span className={q.accepted ? 'accepted' : ''}>
                  <b>{q.answerCount}</b> 回答
                </span>
                <span>{q.viewCount} 浏览</span>
              </div>
              <div className="qmain">
                <Link className="qtitle" to={`/questions/${q.id}`}>
                  {q.title}
                </Link>
                <p className="excerpt">{q.excerpt}</p>
                <div className="tagrow">
                  {q.tags.map((t) => (
                    <button key={t} className="tag" onClick={() => setActiveTag(t)}>
                      {t}
                    </button>
                  ))}
                  <span className="muted">
                    {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')}
                  </span>
                </div>
              </div>
            </li>
          ))}
        </ul>
        {isPending && <p className="muted">加载中…</p>}
        {data && data.items.length === 0 && <p className="muted">没有匹配的问题，换个关键词试试。</p>}
      </section>

      <aside>
        <h3>热门标签</h3>
        <div className="tagrow">
          {tags.map((t) => (
            <button key={t.name} className="tag" onClick={() => setActiveTag(t.name)}>
              {t.name}
              <span className="count">{t.questionCount}</span>
            </button>
          ))}
        </div>
      </aside>
    </div>
  );
}
