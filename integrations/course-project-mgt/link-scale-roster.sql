-- 由 build-demo-directory.py 接入导入事务，基于用户名定位实际的统一身份编号。
SET @course_system = (SELECT id FROM rbac.business_system WHERE system_key = 'course-project-management');

INSERT INTO rbac.permission (system_id, code, name)
SELECT @course_system, codes.code, codes.code
FROM JSON_TABLE(
  '["login","TC","TE","TD","TCM","CV","CR","CRS","CE","CD","CC","CTM","TV","TEC","TEE","TED","AV","UL","MCO","MTR","MTE","MLG","MCOR","MCOE","MCOD","MCOT","MTRC","MTRE","MTRD","MTRM","MTEC","MTEE","MTED","MTEL"]',
  '$[*]' COLUMNS (code VARCHAR(100) CHARACTER SET ascii PATH '$')
) codes
WHERE NOT EXISTS (
  SELECT 1 FROM rbac.permission p WHERE p.system_id = @course_system AND p.code = codes.code
);

INSERT INTO rbac.`role` (name, department_id)
SELECT DISTINCT roster.role_name, u.department_id
FROM demo_course_roster roster JOIN rbac.app_user u ON u.username = roster.username
WHERE NOT EXISTS (
  SELECT 1 FROM rbac.`role` r WHERE r.name = roster.role_name AND r.department_id = u.department_id
);

INSERT INTO rbac.user_role (user_id, role_id)
SELECT u.id, r.id
FROM demo_course_roster roster
JOIN rbac.app_user u ON u.username = roster.username
JOIN rbac.`role` r ON r.name = roster.role_name AND r.department_id = u.department_id
WHERE NOT EXISTS (SELECT 1 FROM rbac.user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- 教师和学生基础角色只授予课程登录许可，课程归属由课程库名册决定。
INSERT INTO rbac.role_permission (role_id, permission_id)
SELECT DISTINCT ur.role_id, p.id
FROM demo_course_roster roster
JOIN rbac.app_user u ON u.username = roster.username
JOIN rbac.user_role ur ON ur.user_id = u.id
JOIN rbac.`role` r ON r.id = ur.role_id AND r.name = roster.role_name AND r.department_id = u.department_id
JOIN rbac.permission p ON p.system_id = @course_system AND p.code = 'login'
WHERE NOT EXISTS (SELECT 1 FROM rbac.role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 现有审核演示账号同时承担教师目录联调，普通教师和学生的授权仍独立。
INSERT INTO rbac.role_permission (role_id, permission_id)
SELECT DISTINCT r.id, p.id
FROM rbac.app_user u
JOIN rbac.user_role ur ON ur.user_id = u.id
JOIN rbac.`role` r ON r.id = ur.role_id AND r.name = u.username AND r.department_id = u.department_id
JOIN rbac.permission p ON p.system_id = @course_system AND p.code IN ('TV', 'TEC', 'MTE', 'MTEC', 'MTEL')
WHERE u.username IN ('admin', 'reviewer')
  AND NOT EXISTS (SELECT 1 FROM rbac.role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO course_project.teacher (user_id, employee_no, name, department, creator, created_at, modifier, modi_at)
SELECT CAST(u.id AS CHAR), u.username, u.name, d.name, 'rbac-scale', NOW(6), 'rbac-scale', NOW(6)
FROM demo_course_roster roster
JOIN rbac.app_user u ON u.username = roster.username
JOIN rbac.department d ON d.id = u.department_id
WHERE roster.role_name = 'teacher'
  AND NOT EXISTS (
    SELECT 1 FROM course_project.teacher t
    WHERE t.employee_no = u.username COLLATE utf8mb4_0900_ai_ci OR t.user_id = CAST(u.id AS CHAR)
  );

INSERT INTO course_project.student (user_id, student_no, name, status, creator, created_at, modifier, modi_at)
SELECT CAST(u.id AS CHAR), u.username, u.name, 'active', 'rbac-scale', NOW(6), 'rbac-scale', NOW(6)
FROM demo_course_roster roster JOIN rbac.app_user u ON u.username = roster.username
WHERE roster.role_name = 'student'
  AND NOT EXISTS (
    SELECT 1 FROM course_project.student s
    WHERE s.student_no = u.username COLLATE utf8mb4_0900_ai_ci OR s.user_id = CAST(u.id AS CHAR)
  );

-- 唯一键已占用时保留原记录，校验失败使整次导入回滚。
CREATE TEMPORARY TABLE demo_course_import_check (
  teachers INT CHECK (teachers = 190),
  students INT CHECK (students = 9810)
);
INSERT INTO demo_course_import_check (teachers, students)
SELECT
  SUM(roster.role_name = 'teacher' AND t.id IS NOT NULL AND t.deleted_at IS NULL),
  SUM(roster.role_name = 'student' AND s.id IS NOT NULL AND s.status = 'active')
FROM demo_course_roster roster
JOIN rbac.app_user u ON u.username = roster.username
JOIN rbac.`role` r ON r.name = roster.role_name AND r.department_id = u.department_id
JOIN rbac.user_role ur ON ur.user_id = u.id AND ur.role_id = r.id
LEFT JOIN course_project.teacher t
  ON t.employee_no = u.username COLLATE utf8mb4_0900_ai_ci AND t.user_id = CAST(u.id AS CHAR)
LEFT JOIN course_project.student s
  ON s.student_no = u.username COLLATE utf8mb4_0900_ai_ci AND s.user_id = CAST(u.id AS CHAR);

-- 仅在独立演示班绑定两名演示成员，已有学期、课程和名册不覆盖。
SET @term = (SELECT MIN(id) FROM course_project.term WHERE name = '2026 秋季课程演示');
INSERT INTO course_project.course (term_id, code, name, description, work_mode, status, creator, created_at, modifier, modi_at)
SELECT @term, 'RBAC-IDENTITY-DEMO', '统一身份联调演示课', '教师与学生使用统一账号登录后查看同一门课。',
       'individual', 3, 'rbac-scale', NOW(6), 'rbac-scale', NOW(6)
WHERE @term IS NOT NULL AND NOT EXISTS (SELECT 1 FROM course_project.course WHERE code = 'RBAC-IDENTITY-DEMO');

SET @demo_course = (SELECT id FROM course_project.course WHERE code = 'RBAC-IDENTITY-DEMO' AND creator = 'rbac-scale');
INSERT INTO course_project.teaching_class (course_id, no, name, creator, created_at, modifier, modi_at)
SELECT @demo_course, 1, '身份联调演示班', 'rbac-scale', NOW(6), 'rbac-scale', NOW(6)
WHERE @demo_course IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM course_project.teaching_class WHERE course_id = @demo_course AND no = 1);

SET @demo_class = (SELECT id FROM course_project.teaching_class WHERE course_id = @demo_course AND no = 1);
INSERT INTO course_project.course_leader_assignment (course_id, teacher_id, creator, created_at, modifier, modi_at)
SELECT @demo_course, t.id, 'rbac-scale', NOW(6), 'rbac-scale', NOW(6)
FROM course_project.teacher t
WHERE @demo_course IS NOT NULL AND t.employee_no = 'ooad-load-2026-00001'
  AND NOT EXISTS (SELECT 1 FROM course_project.course_leader_assignment a WHERE a.course_id = @demo_course AND a.teacher_id = t.id);

INSERT INTO course_project.enrollment (class_id, student_id, course_id, source, creator, created_at, modifier, modi_at)
SELECT @demo_class, s.id, @demo_course, 'manual', 'rbac-scale', NOW(6), 'rbac-scale', NOW(6)
FROM course_project.student s
WHERE @demo_class IS NOT NULL AND s.student_no = 'ooad-load-2026-00191'
  AND NOT EXISTS (SELECT 1 FROM course_project.enrollment e WHERE e.course_id = @demo_course AND e.student_id = s.id);
