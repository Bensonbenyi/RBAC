-- 在两套应用完成数据库迁移后，用具备两个库写权限的账号手动执行。
-- 演示工号与平台用户名一致，课程登录以该字段先查本地教师。
START TRANSACTION;

INSERT INTO rbac.`role` (name, department_id)
SELECT DISTINCT 'teacher', u.department_id
FROM rbac.app_user u
WHERE u.username IN ('reviewer', 'viewer')
  AND NOT EXISTS (
      SELECT 1 FROM rbac.`role` r
      WHERE r.department_id = u.department_id AND r.name = 'teacher'
  );

INSERT INTO rbac.user_role (user_id, role_id)
SELECT u.id, r.id
FROM rbac.app_user u
JOIN rbac.`role` r ON r.department_id = u.department_id AND r.name = 'teacher'
WHERE u.username IN ('reviewer', 'viewer')
  AND NOT EXISTS (
      SELECT 1 FROM rbac.user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

UPDATE course_project.teacher t
JOIN rbac.app_user u ON t.user_id COLLATE utf8mb4_bin = CAST(u.id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin
SET t.employee_no = u.username
WHERE u.username IN ('reviewer', 'viewer') AND t.creator = 'rbac-demo'
  AND CAST(t.employee_no AS BINARY) = CAST(CONCAT('RBAC-DEMO-', u.username) AS BINARY);

INSERT INTO course_project.teacher
    (user_id, employee_no, name, department, creator, created_at, modifier, modi_at)
SELECT CAST(u.id AS CHAR), u.username, u.name, d.name,
       'rbac-demo', NOW(6), 'rbac-demo', NOW(6)
FROM rbac.app_user u
JOIN rbac.department d ON d.id = u.department_id
WHERE u.username IN ('reviewer', 'viewer')
  AND NOT EXISTS (
      SELECT 1 FROM course_project.teacher t
      WHERE CAST(t.user_id AS BINARY) = CAST(CAST(u.id AS CHAR) AS BINARY)
         OR CAST(t.employee_no AS BINARY) = CAST(u.username AS BINARY)
  );

COMMIT;
