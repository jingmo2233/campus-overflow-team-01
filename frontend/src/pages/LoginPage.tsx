import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { App, Button, Card, Form, Input, Tabs, Typography } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { api } from '@/api';
import { useAuth } from '@/auth';
import { describeError, errorCode } from '@/errors';

const { Title } = Typography;

type Mode = 'login' | 'register';

interface LoginForm {
  username: string;
  password: string;
  displayName?: string;
  email?: string;
  college?: string;
  className?: string;
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const [form] = Form.useForm<LoginForm>();
  const [mode, setMode] = useState<Mode>('login');
  const [submitting, setSubmitting] = useState(false);

  const submit = async (values: LoginForm) => {
    setSubmitting(true);
    try {
      if (mode === 'register') {
        await api.register(values as unknown as Record<string, string>);
      }
      await login(values.username, values.password);
      void message.success(mode === 'register' ? '注册成功，已自动登录' : '登录成功');
      navigate('/');
    } catch (error) {
      const code = errorCode(error);
      const text = describeError(error);
      // 账号已存在是注册场景最常见的失败，直接标在学号那一项上
      if (code === 'ID-2001') {
        form.setFields([{ name: 'username', errors: [text] }]);
      } else if (code === 'ID-1004') {
        form.setFields([{ name: 'password', errors: [text] }]);
      } else {
        void message.error(text);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card className="page-card narrow">
      <Title level={3}>{mode === 'login' ? '登录' : '注册'}</Title>
      <Tabs
        activeKey={mode}
        onChange={(k) => {
          setMode(k as Mode);
          form.resetFields();
        }}
        items={[
          { key: 'login', label: '登录' },
          { key: 'register', label: '注册' },
        ]}
      />

      <Form
        form={form}
        layout="vertical"
        onFinish={(values: LoginForm) => void submit(values)}
        disabled={submitting}
      >
        <Form.Item
          name="username"
          label="学号 / 工号"
          rules={[{ required: true, message: '请填写学号或工号' }]}
        >
          <Input prefix={<UserOutlined />} placeholder="2023901" autoComplete="username" />
        </Form.Item>

        <Form.Item
          name="password"
          label="密码"
          extra={mode === 'register' ? '8–64 位，需同时包含字母与数字' : undefined}
          rules={[{ required: true, message: '请填写密码' }]}
        >
          <Input.Password
            prefix={<LockOutlined />}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          />
        </Form.Item>

        {mode === 'register' && (
          <>
            <Form.Item name="displayName" label="昵称" rules={[{ required: true, message: '请填写昵称' }]}>
              <Input placeholder="同学们看到的名字" />
            </Form.Item>
            <Form.Item
              name="email"
              label="校园邮箱"
              rules={[
                { required: true, message: '请填写校园邮箱' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
            >
              <Input placeholder="2023901@campus.edu.cn" />
            </Form.Item>
            <Form.Item name="college" label="学院">
              <Input placeholder="计算机学院" />
            </Form.Item>
            <Form.Item name="className" label="班级">
              <Input placeholder="软工2101" />
            </Form.Item>
          </>
        )}

        <Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            {mode === 'login' ? '登录' : '注册并登录'}
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
}
