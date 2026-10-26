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

export function CoursePage() {
  const { me, refresh } = useAuth();
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [joiningId, setJoiningId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setCourses(await api.courses());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const join = async (course: Course) => {
    setJoiningId(course.id);
    setError('');
    setNotice('');
    try {
      await api.joinCourse(course.id);
      // 刷新 me.courses，提问页的课程下拉框会同步出现该课程
      await refresh();
      await load();
      setNotice(`已加入《${course.name}》，现在可以在提问页选择该课程。`);
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setNotice(`你已经加入《${course.name}》。`);
        await load();
      } else {
        setError((e as Error).message);
      }
    } finally {
      setJoiningId(null);
    }
  };

  const mine = courses.filter((c) => c.myRole);
  const rest = courses.filter((c) => !c.myRole);

  const card = (course: Course) => (
    <li key={course.id} className="course-card">
      <div>
        <span className="course-code">{course.code}</span>
        <h3>{course.name}</h3>
        <p className="course-meta">{course.term} · 授课教师：{course.teacherName}</p>
      </div>
      <div className="course-action">
        {course.myRole ? (
          <span className="role-pill">{ROLE_LABEL[course.myRole]}</span>
        ) : me ? (
          <button className="primary" disabled={joiningId === course.id} onClick={() => void join(course)}>
            {joiningId === course.id ? '加入中…' : '加入课程'}
          </button>
        ) : (
          <Link to="/login">登录后加入</Link>
        )}
      </div>
    </li>
  );

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

      {notice && <p className="success">{notice}</p>}
      {error && <p className="error">{error}</p>}

      {loading ? (
        <p className="muted">加载中…</p>
      ) : courses.length === 0 ? (
        <div className="empty">
          <p>还没有课程。</p>
          <p className="muted">请课程教师创建课程后刷新本页；开发期可用 README 第 9.2 节的 SQL 提升教师角色。</p>
        </div>
      ) : (
        <>
          {me && (
            <>
              <h2>我的课程 <span className="muted">({mine.length})</span></h2>
              {mine.length > 0 ? (
                <ul className="course-list">{mine.map(card)}</ul>
              ) : (
                <p className="muted">还没有加入课程，从下面的“全部课程”选择一门加入。</p>
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
