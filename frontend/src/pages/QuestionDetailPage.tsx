import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Alert,
  App,
  Avatar,
  Button,
  Card,
  Divider,
  Empty,
  Input,
  InputNumber,
  List,
  Result,
  Skeleton,
  Space,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  CaretDownOutlined,
  CaretUpOutlined,
  CheckCircleTwoTone,
  SafetyCertificateTwoTone,
} from '@ant-design/icons';
import { useAuth } from '@/auth';
import { describeError } from '@/errors';
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

const { Title, Text } = Typography;
const { TextArea } = Input;

export function QuestionDetailPage() {
  const { id } = useParams();
  const questionId = Number(id);
  const { me } = useAuth();
  const { message, modal } = App.useApp();
  const [draft, setDraft] = useState('');

  const { data: q, error, isPending } = useQuestion(questionId);
  const { data: related = [] } = useRelatedQuestions(questionId);

  // 所有写操作共用一套失败提示：按业务码翻译成用户能看懂的话
  const onError = (e: unknown) => void message.error(describeError(e));

  const vote = useVote(questionId);
  const submitAnswer = useSubmitAnswer(questionId);
  const acceptAnswer = useAcceptAnswer(questionId);
  const endorseAnswer = useEndorseAnswer(questionId);
  const openBounty = useOpenBounty(questionId);

  if (isPending) return <Skeleton active paragraph={{ rows: 8 }} className="page-card" />;
  if (!q) {
    return (
      <Result
        status="404"
        title="问题不存在或已删除"
        subTitle={error ? describeError(error) : undefined}
        extra={
          <Link to="/">
            <Button type="primary">返回问题列表</Button>
          </Link>
        }
      />
    );
  }

  const canManage = q.permissions.canManage;

  const castVote = (targetType: 'QUESTION' | 'ANSWER', targetId: number, next: number) =>
    vote.mutate({ targetType, targetId, value: next }, { onError });

  const askBounty = () => {
    let points = 50;
    modal.confirm({
      title: '发起悬赏',
      content: (
        <Space direction="vertical">
          <Text type="secondary">悬赏积分 10–500，期限 7 天。积分会先从账户冻结。</Text>
          <InputNumber min={10} max={500} defaultValue={50} onChange={(v) => (points = v ?? 50)} />
        </Space>
      ),
      okText: '确认悬赏',
      cancelText: '取消',
      onOk: () => openBounty.mutate({ points, days: 7 }, { onError }),
    });
  };

  return (
    <div className="layout">
      <article className="layout-main">
        <Card>
          <Title level={3}>{q.title}</Title>
          <Space className="meta" wrap>
            <span>{q.author.displayName}</span>
            <span>{new Date(q.createdAt).toLocaleString('zh-CN')}</span>
            <span>{q.viewCount} 次浏览</span>
            {q.course && <Tag>{q.course.name}</Tag>}
          </Space>

          <div className="post">
            <VoteBox
              score={q.score}
              myVote={q.myVote}
              disabled={!me || vote.isPending}
              reason={!me ? '登录后可投票' : undefined}
              onVote={(next) => castVote('QUESTION', q.id, next)}
            />
            <div className="post-body">
              {/* 正文由后端渲染并经 commonmark + OWASP Sanitizer 双重净化 */}
              <div className="markdown" dangerouslySetInnerHTML={{ __html: q.bodyHtml }} />
              <Space wrap size={[4, 4]} className="tagrow">
                {q.tags.map((t) => (
                  <Tag key={t} color="blue">
                    {t}
                  </Tag>
                ))}
              </Space>
              <CommentList
                comments={q.comments}
                targetType="QUESTION"
                targetId={q.id}
                questionId={questionId}
                canComment={!!me}
              />
              {canManage && (
                <Button type="link" size="small" loading={openBounty.isPending} onClick={askBounty}>
                  发起悬赏
                </Button>
              )}
            </div>
          </div>
        </Card>

        {/* antd 6：orientation 表示横/竖，标题位置改用 titlePlacement */}
        <Divider titlePlacement="start">{q.answerCount} 个回答</Divider>

        {q.answers.length === 0 && <Empty description="还没有人回答，来做第一个吧" />}

        {q.answers.map((a) => (
          <Card key={a.id} id={`answer-${a.id}`} className={q.acceptedAnswerId === a.id ? 'is-accepted' : ''}>
            <div className="post">
              <VoteBox
                score={a.score}
                myVote={a.myVote}
                disabled={!me || vote.isPending}
                reason={!me ? '登录后可投票' : undefined}
                accepted={q.acceptedAnswerId === a.id}
                onVote={(next) => castVote('ANSWER', a.id, next)}
              />
              <div className="post-body">
                <div className="markdown" dangerouslySetInnerHTML={{ __html: a.bodyHtml }} />
                <Space className="meta" wrap>
                  <Avatar size="small">{a.author.displayName.slice(0, 1)}</Avatar>
                  <span>{a.author.displayName}</span>
                  {a.endorsedBy && (
                    <Tag icon={<SafetyCertificateTwoTone twoToneColor="#faad14" />} color="gold">
                      教师认证 · {a.endorsedBy.displayName}
                    </Tag>
                  )}
                  <span>{new Date(a.createdAt).toLocaleString('zh-CN')}</span>
                </Space>
                <Space>
                  {q.permissions.canAccept && !q.acceptedAnswerId && (
                    <Button
                      size="small"
                      loading={acceptAnswer.isPending}
                      onClick={() =>
                        acceptAnswer.mutate(a.id, {
                          onSuccess: () => void message.success('已采纳'),
                          onError,
                        })
                      }
                    >
                      采纳此回答
                    </Button>
                  )}
                  {canManage && !a.endorsedBy && (
                    <Button
                      size="small"
                      loading={endorseAnswer.isPending}
                      onClick={() =>
                        endorseAnswer.mutate(a.id, {
                          onSuccess: () => void message.success('已认证'),
                          onError,
                        })
                      }
                    >
                      教师认证
                    </Button>
                  )}
                </Space>
                <CommentList
                  comments={a.comments}
                  targetType="ANSWER"
                  targetId={a.id}
                  questionId={questionId}
                  canComment={!!me}
                />
              </div>
            </div>
          </Card>
        ))}

        {me ? (
          <Card title="写回答">
            <TextArea
              rows={8}
              value={draft}
              showCount
              maxLength={30000}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="支持 Markdown，代码请用 ``` 包裹。至少 10 字。"
            />
            <Button
              type="primary"
              className="submit-answer"
              loading={submitAnswer.isPending}
              disabled={draft.trim().length === 0}
              onClick={() =>
                submitAnswer.mutate(draft, {
                  onSuccess: () => {
                    setDraft('');
                    void message.success('回答已提交');
                  },
                  onError,
                })
              }
            >
              提交回答
            </Button>
          </Card>
        ) : (
          <Alert
            type="info"
            showIcon
            message={
              <>
                <Link to="/login">登录</Link> 后可以回答与投票。
              </>
            }
          />
        )}
      </article>

      <aside className="layout-side">
        <Card title="相关问题" size="small">
          <List
            size="small"
            dataSource={related}
            locale={{ emptyText: '暂无' }}
            renderItem={(r) => (
              <List.Item>
                <Link to={`/questions/${r.id}`}>{r.title}</Link>
              </List.Item>
            )}
          />
        </Card>
      </aside>
    </div>
  );
}

function VoteBox({
  score,
  myVote,
  disabled,
  reason,
  accepted,
  onVote,
}: {
  score: number;
  myVote: number;
  disabled: boolean;
  reason?: string;
  accepted?: boolean;
  onVote: (next: number) => void;
}) {
  // 再次点击已投的方向表示撤销，所以目标值是 0
  return (
    <div className="votebox">
      <Tooltip title={reason ?? '赞同'}>
        <Button
          type={myVote === 1 ? 'primary' : 'text'}
          icon={<CaretUpOutlined />}
          disabled={disabled}
          onClick={() => onVote(myVote === 1 ? 0 : 1)}
        />
      </Tooltip>
      <b>{score}</b>
      <Tooltip title={reason ?? '反对'}>
        <Button
          type={myVote === -1 ? 'primary' : 'text'}
          danger={myVote === -1}
          icon={<CaretDownOutlined />}
          disabled={disabled}
          onClick={() => onVote(myVote === -1 ? 0 : -1)}
        />
      </Tooltip>
      {accepted && (
        <Tooltip title="已采纳">
          <CheckCircleTwoTone twoToneColor="#52c41a" className="accept-mark" />
        </Tooltip>
      )}
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
  const { message } = App.useApp();
  const postComment = usePostComment(questionId);

  return (
    <div className="comments">
      {comments.map((c) => (
        <div className="comment" key={c.id}>
          <span>{c.body}</span>
          <Text type="secondary"> — {c.author.displayName}</Text>
        </div>
      ))}
      {canComment &&
        (open ? (
          <Space.Compact className="comment-form">
            <Input
              value={text}
              maxLength={600}
              onChange={(e) => setText(e.target.value)}
              placeholder="评论，可用 @昵称 提醒他人"
              onPressEnter={() => void 0}
            />
            <Button
              type="primary"
              loading={postComment.isPending}
              disabled={text.trim().length === 0}
              onClick={() =>
                postComment.mutate(
                  { targetType, targetId, body: text },
                  {
                    onSuccess: () => {
                      setText('');
                      setOpen(false);
                    },
                    onError: (e) => void message.error(describeError(e)),
                  },
                )
              }
            >
              发表
            </Button>
          </Space.Compact>
        ) : (
          <Button type="link" size="small" onClick={() => setOpen(true)}>
            添加评论
          </Button>
        ))}
    </div>
  );
}
