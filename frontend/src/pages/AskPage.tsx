import { App, Button, Card, Form, Input, Select, Space, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth';
import { describeError, fieldOf } from '@/errors';
import { useAskQuestion } from '@/query/hooks';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

interface AskForm {
  title: string;
  body: string;
  tags: string[];
  courseId?: number;
}

export function AskPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm<AskForm>();
  const ask = useAskQuestion();

  const submit = (values: AskForm) => {
    ask.mutate(
      { courseId: values.courseId, title: values.title, body: values.body, tags: values.tags },
      {
        onSuccess: ({ id }) => {
          void message.success('发布成功');
          navigate(`/questions/${id}`);
        },
        onError: (error) => {
          const text = describeError(error);
          const field = fieldOf(error);
          // 能定位到字段的校验错误直接标在表单项上，用户不必自己猜是哪一项出了问题
          if (field) {
            form.setFields([{ name: field, errors: [text] }]);
            form.scrollToField(field);
          } else {
            void message.error(text);
          }
        },
      },
    );
  };

  return (
    <Card className="page-card">
      <Title level={3}>提出一个问题</Title>
      <Paragraph type="secondary">
        好问题会说明「你想达成什么、试过哪些方法、卡在哪一步」。贴上关键代码或报错原文，比描述现象更有效。
      </Paragraph>

      <Form form={form} layout="vertical" onFinish={submit} requiredMark="optional" disabled={ask.isPending}>
        <Form.Item
          name="title"
          label="标题"
          extra="10–150 字，尽量具体——把问题本身写进标题，而不是「求助」「在线等」"
          rules={[
            { required: true, message: '请填写标题' },
            { min: 10, max: 150, message: '标题需 10–150 字' },
          ]}
        >
          <Input placeholder="例如：红黑树插入后如何判断该左旋还是右旋？" showCount maxLength={150} />
        </Form.Item>

        <Form.Item
          name="body"
          label="正文"
          extra="支持 Markdown，代码请用 ``` 包裹"
          rules={[
            { required: true, message: '请填写正文' },
            { min: 20, message: '正文至少 20 字，请说明你尝试过什么、卡在哪里' },
          ]}
        >
          <TextArea
            rows={12}
            showCount
            maxLength={30000}
            placeholder="描述你的问题背景、已尝试的方案和具体卡点…"
          />
        </Form.Item>

        <Form.Item
          name="tags"
          label="标签"
          extra="1–5 个。输入后按回车确认，只允许中英文、数字与 # + . - _"
          rules={[
            { required: true, message: '至少填写 1 个标签' },
            {
              validator: (_, value: string[] | undefined) =>
                (value?.length ?? 0) <= 5 ? Promise.resolve() : Promise.reject(new Error('最多 5 个标签')),
            },
          ]}
        >
          <Select mode="tags" placeholder="数据结构　红黑树" tokenSeparators={[',', '，', ' ']} />
        </Form.Item>

        <Form.Item name="courseId" label="所属课程" extra="可选。关联课程后，该课程的教师与助教可以协助管理">
          <Select
            allowClear
            placeholder="不关联课程"
            options={me?.courses.map((c) => ({ value: c.id, label: `${c.code} ${c.name}` }))}
          />
        </Form.Item>

        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit" loading={ask.isPending}>
              发布问题
            </Button>
            <Button onClick={() => navigate(-1)}>取消</Button>
          </Space>
        </Form.Item>
      </Form>
    </Card>
  );
}
