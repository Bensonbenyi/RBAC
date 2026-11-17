-- Run with cross-database read permission after both applications have initialized.
-- Use the platform's actual IDs; existing teacher associations remain unchanged.
INSERT INTO course_project.teacher
    (user_id,employee_no,name,department,creator,created_at,modifier,modi_at)
SELECT CAST(u.id AS CHAR), CONCAT('RBAC-DEMO-',u.username), u.name, d.name,
       'rbac-demo',NOW(6),'rbac-demo',NOW(6)
FROM rbac.app_user u
JOIN rbac.department d ON d.id=u.department_id
WHERE u.username IN ('admin','reviewer','viewer')
  AND NOT EXISTS (
      SELECT 1 FROM course_project.teacher t
      WHERE CAST(t.user_id AS BINARY)=CAST(CAST(u.id AS CHAR) AS BINARY)
         OR CAST(t.employee_no AS BINARY)=CAST(CONCAT('RBAC-DEMO-',u.username) AS BINARY)
  );
