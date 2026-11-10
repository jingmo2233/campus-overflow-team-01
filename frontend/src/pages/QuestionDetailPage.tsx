import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../api';
import { useAuth } from '../auth';
import type { Answer, Comment, Me, QuestionDetail } from '../types';

export function QuestionDetailPage() {
  const { id } = useParams();
  const questionId = Number(id);
  const { me } = useAuth();
  const navigate = useNavigate();
  const [q, setQ] = useState<QuestionDetail | null>(null);
  const [related, setRelated] = useState<{ id: number; title: string }[]>([]);
  const [draft, setDraft] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const load = useCallback(() => {
    api.question(questionId).then(setQ).catch((e) => setError(errorMessage(e, '问题加载失败')));
  }, [questionId]);

  useEffect(() => {
    load();
    api.related(questionId).then(setRelated).catch(() => undefined);
  }, [load, questionId]);

  // 写操作统一从这里走：先清掉上一次的提示，成功给 success，失败给 error。
  // 以前只 catch 到 setError，成功时上一条错误仍挂在页面上，用户会误以为操作失败了。
  const act = (fn: () => Promise<unknown>, done?: string) => {
    setError('');
    setNotice('');
    return fn()
      .then(() => {
        if (done) setNotice(done);
        load();
      })
      .catch((e) => setError(errorMessage(e)));
  };

  /**
   * 评论成功后把新评论**就地插进当前状态**，不重取整个详情。
   *
   * PB-19 验收标准 ③ 要求「评论成功后无需整页刷新即可看到新内容」。
   * 重取详情虽然不用刷浏览器，但会把整页正文重新渲染一遍：滚动位置可能跳动，
   * 正在展开的编辑框 / 输入框状态也会被打断，所以改成只往对应的评论数组尾部追加。
   *
   * 顺序与后端一致：详情查询是 `ORDER BY created_at, id` 升序，新评论追加在尾部即正确位置。
   */
  const appendComment = useCallback((targetType: 'QUESTION' | 'ANSWER', targetId: number, c: Comment) => {
    setQ((prev) => {
      if (!prev) return prev;
      if (targetType === 'QUESTION') {
        return { ...prev, comments: [...prev.comments, c] };
      }
      return {
        ...prev,
        answers: prev.answers.map((a) => (a.id === targetId ? { ...a, comments: [...a.comments, c] } : a)),
      };
    });
  }, []);

  if (!q) return <p className="muted">{error || '加载中…'}</p>;

  const canManage = q.permissions.canManage;

  const removeQuestion = () => {
    if (!window.confirm('删除后问题将从列表和搜索中消失，且无法恢复。确定删除？')) return;
    // 删除成功后原页面已经取不到数据，直接回列表，不留在 404 状态
    act(() => api.deleteQuestion(q.id)).then(() => navigate('/', { replace: true })).catch(() => undefined);
  };

  return (
    <div className="layout">
      <article>
        <h1>{q.title}</h1>
        <p className="muted">
          {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')} · {q.viewCount} 次浏览
          {q.course && <> · 课程 {q.course.name}</>}
        </p>

        {/* 全局提示放在正文上方，任何位置的写操作结果都在同一处反馈 */}
        <div role="status" aria-live="polite">{notice && <p className="success">{notice}</p>}</div>
        {error && <p className="error" role="alert">{error}</p>}

        <div className="post">
          <div className="votebox">
            <button disabled={!me} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === 1 ? 0 : 1))}>▲</button>
            <b>{q.score}</b>
            <button disabled={!me} onClick={() => act(() => api.vote('QUESTION', q.id, q.myVote === -1 ? 0 : -1))}>▼</button>
          </div>
          <div className="body">
            {/* 正文由后端渲染并经 commonmark + OWASP Sanitizer 双重净化 */}
            <div dangerouslySetInnerHTML={{ __html: q.bodyHtml }} />
            <div className="tagrow">{q.tags.map((t) => <span key={t} className="tag">{t}</span>)}</div>
            <CommentList comments={q.comments} targetType="QUESTION" targetId={q.id} me={me}
                         onCreated={(c) => appendComment('QUESTION', q.id, c)} />
            {(q.permissions.canEdit || q.permissions.canDelete) && (
              <div className="actions">
                {q.permissions.canEdit && (
                  <EditQuestionForm question={q} onDone={() => act(async () => undefined, '问题已更新')} />
                )}
                {q.permissions.canDelete && (
                  <button className="danger" onClick={removeQuestion}>删除问题</button>
                )}
              </div>
            )}
            {canManage && (
              <button className="link" onClick={() => {
                const points = Number(prompt('悬赏积分（10–500）', '50'));
                if (points) act(() => api.bounty(q.id, points, 7), '悬赏已发起');
              }}>发起悬赏
              </button>
            )}
          </div>
        </div>

        <h2>{q.answerCount} 个回答</h2>
        {q.answers.map((a) => (
          <div className={`post ${q.acceptedAnswerId === a.id ? 'is-accepted' : ''}`} key={a.id} id={`answer-${a.id}`}>
            <div className="votebox">
              <button disabled={!me} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === 1 ? 0 : 1))}>▲</button>
              <b>{a.score}</b>
              <button disabled={!me} onClick={() => act(() => api.vote('ANSWER', a.id, a.myVote === -1 ? 0 : -1))}>▼</button>
              {q.acceptedAnswerId === a.id && <span className="check-mark" title="已采纳">✔</span>}
            </div>
            <div className="body">
              <div dangerouslySetInnerHTML={{ __html: a.bodyHtml }} />
              <p className="muted">
                {a.author.displayName}
                {a.endorsedBy && <span className="endorsed">教师认证 · {a.endorsedBy.displayName}</span>}
                · {new Date(a.createdAt).toLocaleString('zh-CN')}
              </p>
              <div className="actions">
                {q.permissions.canAccept && !q.acceptedAnswerId &&
                  <button onClick={() => act(() => api.accept(q.id, a.id), '已采纳该回答')}>采纳此回答</button>}
                {canManage && !a.endorsedBy && <button onClick={() => act(() => api.endorse(a.id), '已认证该回答')}>教师认证</button>}
                {(a.canEdit || canManage) && (
                  <EditAnswerForm answer={a} onDone={() => act(async () => undefined, '回答已更新')} />
                )}
                {(a.canEdit || canManage) && (
                  <button className="danger" onClick={() => {
                    if (!window.confirm('删除这条回答？该操作会同时减少问题的回答数。')) return;
                    act(() => api.deleteAnswer(a.id), '回答已删除');
                  }}>删除回答</button>
                )}
              </div>
              <CommentList comments={a.comments} targetType="ANSWER" targetId={a.id} me={me}
                           onCreated={(c) => appendComment('ANSWER', a.id, c)} />
            </div>
          </div>
        ))}

        {me ? (
          <section className="answer-form">
            <h3>写回答</h3>
            <textarea rows={8} value={draft} onChange={(e) => setDraft(e.target.value)}
                      placeholder="支持 Markdown，代码请使用 ``` 包裹" />
            <button className="primary" onClick={() => act(async () => {
              await api.answer(q.id, draft);
              setDraft('');
            }, '回答已提交')}>提交回答
            </button>
          </section>
        ) : <p className="muted"><Link to="/login">登录</Link>后可以回答与投票。</p>}
      </article>

      <aside>
        <h3>相关问题</h3>
        <ul className="plain">
          {related.map((r) => <li key={r.id}><Link to={`/questions/${r.id}`}>{r.title}</Link></li>)}
          {related.length === 0 && <li className="muted">暂无</li>}
        </ul>
      </aside>
    </div>
  );
}

/**
 * 评论列表。发表成功后把新评论交给上层就地插入，不触发整页重取。
 *
 * `me` 同时承担两个职责：非空才显示输入框；非空才有作者信息可以拼出完整 Comment。
 * 拼装之所以可行，是因为后端 `POST /v1/comments` 只回 `{ id }`，没有回完整视图，
 * 而评论在页面上是纯文本展示（不像回答要经后端渲染 Markdown），
 * 用当前登录态 + 本地时刻足以还原一条与服务端一致的记录。
 */
function CommentList({ comments, targetType, targetId, me, onCreated }: {
  comments: Comment[];
  targetType: 'QUESTION' | 'ANSWER';
  targetId: number;
  me: Me | null;
  onCreated: (c: Comment) => void;
}) {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');

  const submit = async () => {
    // 空评论后端会返回 400，这里先拦一道，避免用户点了一下没反应还以为是卡住了
    const body = text.trim();
    if (!body) {
      setError('评论内容不能为空');
      return;
    }
    setSending(true);
    setError('');
    try {
      const { id } = await api.comment(targetType, targetId, body);
      setText('');
      setOpen(false);
      if (me) {
        onCreated({
          id,
          author: { id: me.id, displayName: me.displayName, role: me.role, verified: me.verified },
          body,
          createdAt: new Date().toISOString(),
        });
      }
    } catch (e) {
      // 评论失败要把原因留在输入框下面，否则用户不知道是网络问题还是被限流
      setError(errorMessage(e, '评论发表失败'));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="comments">
      {comments.map((c) => (
        <div className="comment" key={c.id}>
          <span>{c.body}</span>
          <span className="muted"> — {c.author.displayName}</span>
        </div>
      ))}
      {me && (open ? (
        <div className="comment-form">
          <input value={text} onChange={(e) => setText(e.target.value)} placeholder="评论，可用 @昵称 提醒他人"
                 onKeyDown={(e) => { if (e.key === 'Enter') void submit(); }} />
          <button disabled={sending} onClick={() => void submit()}>{sending ? '发表中…' : '发表'}</button>
          <button className="link" onClick={() => { setOpen(false); setError(''); }}>取消</button>
        </div>
      ) : <button className="link" onClick={() => setOpen(true)}>添加评论</button>)}
      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}

/** 编辑问题：课程归属不允许在前端改，这里只提交标题 / 正文 / 标签。 */
function EditQuestionForm({ question, onDone }: { question: QuestionDetail; onDone: () => void }) {
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(question.title);
  const [body, setBody] = useState(question.body);
  const [tags, setTags] = useState(question.tags.join(' '));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const save = async () => {
    // 后端对标题有 @NotBlank + 150 字上限，这里先拦，省一次往返
    if (!title.trim() || !body.trim()) {
      setError('标题和正文都不能为空');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await api.editQuestion(question.id, {
        courseId: question.course?.id,
        title: title.trim(),
        body,
        tags: tags.split(/[,，\s]+/).filter(Boolean),
      });
      setEditing(false);
      onDone();
    } catch (e) {
      setError(errorMessage(e, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  if (!editing) return <button onClick={() => setEditing(true)}>编辑问题</button>;

  return (
    <div className="edit-form">
      <label>标题
        <input value={title} maxLength={150} onChange={(e) => setTitle(e.target.value)} />
      </label>
      <label>正文（Markdown）
        <textarea rows={10} value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      <label>标签（1–5 个）
        <input value={tags} onChange={(e) => setTags(e.target.value)} />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button className="primary" disabled={saving} onClick={() => void save()}>{saving ? '保存中…' : '保存'}</button>
        <button onClick={() => { setEditing(false); setError(''); }}>取消</button>
      </div>
    </div>
  );
}

/** 编辑回答：正文与问题共用 MarkdownBody 的长度约束，前端只做空值拦截。 */
function EditAnswerForm({ answer, onDone }: { answer: Answer; onDone: () => void }) {
  const [editing, setEditing] = useState(false);
  const [body, setBody] = useState(answer.body);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const save = async () => {
    if (!body.trim()) {
      setError('回答内容不能为空');
      return;
    }
    setSaving(true);
    setError('');
    try {
      // 用正文覆盖原内容：AnswerView 只回渲染后的 bodyHtml，编辑必须以原始 Markdown 为起点
      await api.editAnswer(answer.id, body);
      setEditing(false);
      onDone();
    } catch (e) {
      setError(errorMessage(e, '保存失败'));
    } finally {
      setSaving(false);
    }
  };

  if (!editing) return <button onClick={() => setEditing(true)}>编辑回答</button>;

  return (
    <div className="edit-form">
      <label>回答内容（Markdown）
        <textarea rows={10} value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="actions">
        <button className="primary" disabled={saving} onClick={() => void save()}>{saving ? '保存中…' : '保存'}</button>
        <button onClick={() => { setEditing(false); setError(''); }}>取消</button>
      </div>
    </div>
  );
}
