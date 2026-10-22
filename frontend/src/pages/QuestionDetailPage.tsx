import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { errorText } from '@/api';
import { useAuth } from '@/auth';
import {
  useAcceptAnswer,
  useEndorseAnswer,
  useOpenBounty,
  usePostComment,
  useQuestion,
  useRelatedQuestions,
  useSubmitAnswer,
  useVote,
} from '@/query/hooks';

export function QuestionDetailPage() {
  const { id } = useParams();
  const questionId = Number(id);
  const { me } = useAuth();
  const [draft, setDraft] = useState('');

  const { data: q, error, isPending } = useQuestion(questionId);
  const { data: related = [] } = useRelatedQuestions(questionId);

  const vote = useVote(questionId);
  const submitAnswer = useSubmitAnswer(questionId);
  const acceptAnswer = useAcceptAnswer(questionId);
  const endorseAnswer = useEndorseAnswer(questionId);
  const openBounty = useOpenBounty(questionId);

  // 写操作的错误集中展示：取第一个失败的 mutation
  const actionError = [vote, submitAnswer, acceptAnswer, endorseAnswer, openBounty].find(
    (m) => m.error,
  )?.error;
  const message = error ?? actionError;

  if (isPending) return <p className="muted">加载中…</p>;
  if (!q) return <p className="error">{message ? errorText(message) : '问题不存在或已删除。'}</p>;

  const canManage = q.permissions.canManage;

  return (
    <div className="layout">
      <article>
        <h1>{q.title}</h1>
        <p className="muted">
          {q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')} · {q.viewCount} 次浏览
          {q.course && <> · 课程 {q.course.name}</>}
        </p>

        <div className="post">
          <div className="votebox">
            <button
              disabled={!me || vote.isPending}
              onClick={() =>
                vote.mutate({ targetType: 'QUESTION', targetId: q.id, value: q.myVote === 1 ? 0 : 1 })
              }
            >
              ▲
            </button>
            <b>{q.score}</b>
            <button
              disabled={!me || vote.isPending}
              onClick={() =>
                vote.mutate({ targetType: 'QUESTION', targetId: q.id, value: q.myVote === -1 ? 0 : -1 })
              }
            >
              ▼
            </button>
          </div>
          <div className="body">
            {/* 正文由后端渲染并经 commonmark + OWASP Sanitizer 双重净化 */}
            <div dangerouslySetInnerHTML={{ __html: q.bodyHtml }} />
            <div className="tagrow">
              {q.tags.map((t) => (
                <span key={t} className="tag">
                  {t}
                </span>
              ))}
            </div>
            <CommentList
              comments={q.comments}
              targetType="QUESTION"
              targetId={q.id}
              questionId={questionId}
              canComment={!!me}
            />
            {canManage && (
              <button
                className="link"
                disabled={openBounty.isPending}
                onClick={() => {
                  const points = Number(prompt('悬赏积分（10–500）', '50'));
                  if (points) openBounty.mutate({ points, days: 7 });
                }}
              >
                发起悬赏
              </button>
            )}
          </div>
        </div>

        <h2>{q.answerCount} 个回答</h2>
        {q.answers.map((a) => (
          <div
            className={`post ${q.acceptedAnswerId === a.id ? 'is-accepted' : ''}`}
            key={a.id}
            id={`answer-${a.id}`}
          >
            <div className="votebox">
              <button
                disabled={!me || vote.isPending}
                onClick={() =>
                  vote.mutate({ targetType: 'ANSWER', targetId: a.id, value: a.myVote === 1 ? 0 : 1 })
                }
              >
                ▲
              </button>
              <b>{a.score}</b>
              <button
                disabled={!me || vote.isPending}
                onClick={() =>
                  vote.mutate({ targetType: 'ANSWER', targetId: a.id, value: a.myVote === -1 ? 0 : -1 })
                }
              >
                ▼
              </button>
              {q.acceptedAnswerId === a.id && (
                <span className="check-mark" title="已采纳">
                  ✔
                </span>
              )}
            </div>
            <div className="body">
              <div dangerouslySetInnerHTML={{ __html: a.bodyHtml }} />
              <p className="muted">
                {a.author.displayName}
                {a.endorsedBy && (
                  <span className="endorsed">教师认证 · {a.endorsedBy.displayName}</span>
                )}· {new Date(a.createdAt).toLocaleString('zh-CN')}
              </p>
              <div className="actions">
                {q.permissions.canAccept && !q.acceptedAnswerId && (
                  <button disabled={acceptAnswer.isPending} onClick={() => acceptAnswer.mutate(a.id)}>
                    采纳此回答
                  </button>
                )}
                {canManage && !a.endorsedBy && (
                  <button disabled={endorseAnswer.isPending} onClick={() => endorseAnswer.mutate(a.id)}>
                    教师认证
                  </button>
                )}
              </div>
              <CommentList
                comments={a.comments}
                targetType="ANSWER"
                targetId={a.id}
                questionId={questionId}
                canComment={!!me}
              />
            </div>
          </div>
        ))}

        {me ? (
          <section className="answer-form">
            <h3>写回答</h3>
            <textarea
              rows={8}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="支持 Markdown，代码请使用 ``` 包裹"
            />
            <button
              className="primary"
              disabled={submitAnswer.isPending}
              onClick={() => submitAnswer.mutate(draft, { onSuccess: () => setDraft('') })}
            >
              {submitAnswer.isPending ? '提交中…' : '提交回答'}
            </button>
          </section>
        ) : (
          <p className="muted">
            <Link to="/login">登录</Link>后可以回答与投票。
          </p>
        )}
        {message && <p className="error">{errorText(message)}</p>}
      </article>

      <aside>
        <h3>相关问题</h3>
        <ul className="plain">
          {related.map((r) => (
            <li key={r.id}>
              <Link to={`/questions/${r.id}`}>{r.title}</Link>
            </li>
          ))}
          {related.length === 0 && <li className="muted">暂无</li>}
        </ul>
      </aside>
    </div>
  );
}

function CommentList({
  comments,
  targetType,
  targetId,
  questionId,
  canComment,
}: {
  comments: { id: number; author: { displayName: string }; body: string; createdAt: string }[];
  targetType: 'QUESTION' | 'ANSWER';
  targetId: number;
  questionId: number;
  canComment: boolean;
}) {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const postComment = usePostComment(questionId);

  return (
    <div className="comments">
      {comments.map((c) => (
        <div className="comment" key={c.id}>
          <span>{c.body}</span>
          <span className="muted"> — {c.author.displayName}</span>
        </div>
      ))}
      {canComment &&
        (open ? (
          <div className="comment-form">
            <input
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="评论，可用 @昵称 提醒他人"
            />
            <button
              disabled={postComment.isPending}
              onClick={() =>
                postComment.mutate(
                  { targetType, targetId, body: text },
                  {
                    onSuccess: () => {
                      setText('');
                      setOpen(false);
                    },
                  },
                )
              }
            >
              发表
            </button>
            {postComment.error && <span className="error">{errorText(postComment.error)}</span>}
          </div>
        ) : (
          <button className="link" onClick={() => setOpen(true)}>
            添加评论
          </button>
        ))}
    </div>
  );
}
