-- 在 Flyway 建表完成后，手动导入空的课程演示库。
START TRANSACTION;

INSERT INTO department(name, parent_id) VALUES ('教学部', NULL);
SET @dept = LAST_INSERT_ID();
INSERT INTO department(name, parent_id) VALUES ('教务部', @dept);
SET @other_dept = LAST_INSERT_ID();

SET @phone = COALESCE(@demo_phone, '13800000000');
INSERT INTO app_user(username, name, phone, department_id) VALUES
    ('admin', '演示管理员', @phone, @dept),
    ('reviewer', '审核教师', @phone, @dept),
    ('viewer', '普通教师', @phone, @dept),
    ('other', '其他部门用户', @phone, @other_dept);
INSERT INTO `role`(name, department_id) VALUES
    ('admin', @dept), ('reviewer', @dept), ('viewer', @dept), ('teacher', @dept);
INSERT INTO user_role(user_id, role_id)
SELECT u.id, r.id
FROM app_user u JOIN `role` r ON r.department_id = u.department_id
WHERE u.department_id = @dept
  AND (r.name = u.username OR (r.name = 'teacher' AND u.username IN ('reviewer', 'viewer')));

INSERT INTO business_system(system_key, name) VALUES ('rbac', '统一权限管理后台');
SET @system = LAST_INSERT_ID();
INSERT INTO permission(system_id, code, name) VALUES
    (@system, 'login', 'login'),
    (@system, 'user.view', 'user.view'),
    (@system, 'user.role.assign', 'user.role.assign'),
    (@system, 'department.manage', 'department.manage'),
    (@system, 'system.manage', 'system.manage'),
    (@system, 'permission.manage', 'permission.manage'),
    (@system, 'role.manage', 'role.manage'),
    (@system, 'ui.users', 'ui.users'),
    (@system, 'ui.user.roles', 'ui.user.roles'),
    (@system, 'ui.roles', 'ui.roles'),
    (@system, 'ui.role.create', 'ui.role.create'),
    (@system, 'ui.role.edit', 'ui.role.edit'),
    (@system, 'ui.role.delete', 'ui.role.delete'),
    (@system, 'ui.role.permissions', 'ui.role.permissions'),
    (@system, 'ui.role.inheritance', 'ui.role.inheritance'),
    (@system, 'ui.departments', 'ui.departments'),
    (@system, 'ui.systems', 'ui.systems'),
    (@system, 'ui.permissions', 'ui.permissions');
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM `role` r CROSS JOIN permission p
WHERE r.department_id = @dept AND r.name = 'admin' AND p.system_id = @system;

INSERT INTO business_system(system_key, name) VALUES ('course-project-management', '课程管理系统');
SET @system = LAST_INSERT_ID();
INSERT INTO permission(system_id, code, name) VALUES
    (@system, 'login', 'login'),
    (@system, 'TC', 'TC'),
    (@system, 'TE', 'TE'),
    (@system, 'TD', 'TD'),
    (@system, 'CV', 'CV'),
    (@system, 'CR', 'CR'),
    (@system, 'UL', 'UL'),
    (@system, 'TEC', 'TEC'),
    (@system, 'MCO', 'MCO'),
    (@system, 'MTR', 'MTR'),
    (@system, 'MCOR', 'MCOR'),
    (@system, 'MTRC', 'MTRC'),
    (@system, 'MTRE', 'MTRE'),
    (@system, 'MTRD', 'MTRD');
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM `role` r CROSS JOIN permission p
WHERE r.department_id = @dept AND p.system_id = @system AND (
    r.name = 'admin'
    OR (r.name = 'reviewer' AND p.code <> 'TEC')
    OR (r.name = 'viewer' AND p.code IN ('login', 'CV', 'MCO'))
);

COMMIT;
