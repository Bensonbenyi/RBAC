ALTER TABLE login_session DROP COLUMN system_key;

DELETE rp FROM role_permission rp
JOIN permission p ON p.id = rp.permission_id
JOIN business_system s ON s.id = p.system_id
WHERE s.system_key = 'rbac' AND p.code IN (
    'user.create', 'user.edit', 'user.delete',
    'ui.user.create', 'ui.user.edit', 'ui.user.delete',
    'ui.department.create', 'ui.department.edit', 'ui.department.delete', 'ui.system.edit'
);

DELETE p FROM permission p
JOIN business_system s ON s.id = p.system_id
WHERE s.system_key = 'rbac' AND p.code IN (
    'user.create', 'user.edit', 'user.delete',
    'ui.user.create', 'ui.user.edit', 'ui.user.delete',
    'ui.department.create', 'ui.department.edit', 'ui.department.delete', 'ui.system.edit'
);
