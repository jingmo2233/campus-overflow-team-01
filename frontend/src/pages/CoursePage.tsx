import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import type { Course, CourseRole } from '../types';

const ROLE_LABEL: Record<CourseRole, string> = {
  STUDENT: '学生',
  TA: '助教',
  TEACHER: '教师',
};

/** 加入课程失败时的提示。409 不走这里，由调用方当作「已加入」的中性状态处理。 */
function joinFailureMessage(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status === 401) return '登录状态已失效，请重新登录后再加入课程。';
    if (e.status === 404) return '这门课程可能已被删除，请刷新页面。';
    return e.message;
  }
  return '加入失败，请稍后重试。';
}

export function CoursePage() {
  const { me, refresh } = useAuth();
  const [courses, setCourses] = useState<Course[]>([]);
  const [mine, setMine] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [joiningId, setJoiningId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setError('');
    try {
      // 未登录只需要全部课程；登录后再取一次「我加入的」，
      // 与后端 /courses/mine 语义对齐，不再靠前端过滤全表。
      const [all, own] = await Promise.all([
        api.courses(),
        me ? api.myCourses() : Promise.resolve<Course[]>([]),
      ]);
      setCourses(all);
      setMine(own);
    } catch (e) {
      setError(e instanceof Error ? e.message : '课程列表加载失败');
      setCourses([]);
      setMine([]);
    } finally {
      setLoading(false);
    }
  }, [me]);

  useEffect(() => {
    void load();
  }, [load]);

  // 另一个标签页加入课程后切回来，本页的「我的课程」需要跟着更新。
  // 只在页面重新可见时拉一次，避免定时轮询造成无谓请求。
  useEffect(() => {
    if (!me) return;
    const onVisible = () => {
      if (document.visibilityState === 'visible') void load();
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [me, load]);

  const join = async (course: Course) => {
    setJoiningId(course.id);
    setError('');
    setNotice('');
    try {
      await api.joinCourse(course.id);
      // 刷新 me.courses，提问页的课程下拉框会同步出现该课程
      await refresh();
      await load();
      setNotice(`已加入《${course.name}》，现在可以在提问页选择这门课。`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        // 409 说明服务端已有成员关系，不是错误：本地状态是旧的，重拉后课程会落到「我的课程」
        setNotice(`你已经加入《${course.name}》了。`);
        await refresh();
        await load();
      } else {
        setNotice('');
        setError(joinFailureMessage(e));
      }
    } finally {
      setJoiningId(null);
    }
  };

  // 「全部课程」里排除已加入的，避免同一门课在两个分区重复出现
  const joinedIds = new Set(mine.map((c) => c.id));
  const rest = courses.filter((c) => !joinedIds.has(c.id));

  const card = (course: Course) => {
    const role = course.myRole ?? (joinedIds.has(course.id) ? 'STUDENT' : null);
    return (
      <li key={course.id} className="course-card">
        <div>
          <span className="course-code">{course.code}</span>
          <h3>{course.name}</h3>
          <p className="course-meta">{course.term} · 授课教师：{course.teacherName}</p>
        </div>
        <div className="course-action">
          {role ? (
            <span className="role-pill">{ROLE_LABEL[role] ?? role}</span>
          ) : me ? (
            <button className="primary" disabled={joiningId === course.id}
                    aria-label={`加入课程 ${course.name}`}
                    onClick={() => void join(course)}>
              {joiningId === course.id ? '加入中…' : '加入课程'}
            </button>
          ) : (
            <Link to="/login">登录后加入</Link>
          )}
        </div>
      </li>
    );
  };

  return (
    <section className="course-page">
      <div className="course-head">
        <div>
          <h1>课程</h1>
          <p className="muted">
            加入课程后，提问时可以选择课程；课程教师/助教可以代为采纳、认证与删除内容。
          </p>
        </div>
        {!me && <Link className="primary" to="/login">登录后加入课程</Link>}
      </div>

      {/* 结果提示统一放在一个 aria-live 区域，读屏软件能播报加入结果 */}
      <div role="status" aria-live="polite">
        {notice && <p className="success">{notice}</p>}
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      {loading ? (
        <p className="muted" aria-busy="true">加载中…</p>
      ) : courses.length === 0 && !error ? (
        <div className="empty">
          <p>还没有可加入的课程。</p>
          <p className="muted">课程由任课教师创建，创建后会自动出现在这里。</p>
        </div>
      ) : (
        <>
          {me && (
            <>
              <h2>我的课程 <span className="muted">({mine.length})</span></h2>
              {mine.length > 0 ? (
                <ul className="course-list">{mine.map(card)}</ul>
              ) : (
                <p className="muted">还没有加入课程，从下面的「全部课程」里选一门加入。</p>
              )}
            </>
          )}

          <h2>{me ? '全部课程' : '课程列表'} <span className="muted">({rest.length})</span></h2>
          {rest.length > 0 ? (
            <ul className="course-list">{rest.map(card)}</ul>
          ) : (
            <p className="muted">没有其他课程。</p>
          )}
        </>
      )}
    </section>
  );
}
