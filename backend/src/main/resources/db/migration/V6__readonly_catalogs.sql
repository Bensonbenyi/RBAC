DELETE rp FROM role_permission rp
JOIN permission p ON p.id = rp.permission_id
JOIN business_system s ON s.id = p.system_id
WHERE s.system_key = 'rbac'
  AND p.code IN ('ui.system.create', 'ui.system.delete', 'ui.permission.create',
                 'ui.permission.edit', 'ui.permission.delete');

DELETE p FROM permission p
JOIN business_system s ON s.id = p.system_id
WHERE s.system_key = 'rbac'
  AND p.code IN ('ui.system.create', 'ui.system.delete', 'ui.permission.create',
                 'ui.permission.edit', 'ui.permission.delete');
