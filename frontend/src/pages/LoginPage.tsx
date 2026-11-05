import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../api';
import { useAuth } from '../auth';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [form, setForm] = useState({
    username: '', password: '', displayName: '', email: '', college: '', className: '',
  });
  const [error, setError] = useState('');
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm({ ...form, [k]: e.target.value });

  const submit = async () => {
    // 先清掉上一次的提示，否则重新提交时旧错误还挂在页面上
    setError('');
    if (!form.username.trim() || !form.password) {
      setError('请填写学号/工号与密码');
      return;
    }
    try {
      if (mode === 'register') {
        await api.register(form);
      }
      await login(form.username, form.password);
      navigate('/');
    } catch (e) {
      // 401 是密码错误、429 是失败次数过多，这两条必须给中文，不能把英文 message 丢给用户
      setError(errorMessage(e, mode === 'register' ? '注册失败，请稍后重试' : '登录失败，请稍后重试'));
    }
  };

  return (
    <div className="form-page narrow">
      <div className="tabs">
        <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>登录</button>
        <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>注册</button>
      </div>
      <label>学号 / 工号<input value={form.username} onChange={set('username')} /></label>
      <label>密码<input type="password" value={form.password} onChange={set('password')} /></label>
      {mode === 'register' && (
        <>
          <label>昵称<input value={form.displayName} onChange={set('displayName')} /></label>
          <label>校园邮箱<input value={form.email} onChange={set('email')} /></label>
          <label>学院<input value={form.college} onChange={set('college')} /></label>
          <label>班级<input value={form.className} onChange={set('className')} /></label>
        </>
      )}
      {error && <p className="error">{error}</p>}
      <button className="primary" onClick={submit}>{mode === 'login' ? '登录' : '注册并登录'}</button>
    </div>
  );
}
