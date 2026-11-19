-- 教师创建权限同时用于统一身份教师选择。管理员可按角色分配。
INSERT INTO permission(system_id, code, name)
SELECT id, 'TEC', '添加教师与查询统一身份目录'
FROM business_system WHERE system_key = 'course-project-management'
ON DUPLICATE KEY UPDATE code = permission.code;
