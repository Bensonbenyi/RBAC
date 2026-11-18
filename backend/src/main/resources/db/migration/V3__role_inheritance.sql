-- RBAC1：高级角色继承基础角色；同部门约束与防环由事务内业务规则维护。
CREATE TABLE role_inheritance (
    senior_role_id BIGINT NOT NULL COMMENT '获得继承权限的高级角色',
    junior_role_id BIGINT NOT NULL COMMENT '提供权限的基础角色',
    PRIMARY KEY (senior_role_id, junior_role_id),
    KEY idx_inheritance_junior (junior_role_id),
    CONSTRAINT fk_inheritance_senior FOREIGN KEY (senior_role_id) REFERENCES `role` (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inheritance_junior FOREIGN KEY (junior_role_id) REFERENCES `role` (id) ON DELETE RESTRICT,
    CONSTRAINT ck_inheritance_distinct CHECK (senior_role_id <> junior_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='角色继承';
INSERT IGNORE INTO permission(system_id,code,name)
SELECT id,'ui.role.inheritance','配置角色继承' FROM business_system WHERE system_key='rbac';
INSERT IGNORE INTO role_permission(role_id,permission_id)
SELECT rp.role_id,ui.id FROM role_permission rp
JOIN permission api ON api.id=rp.permission_id
JOIN business_system s ON s.id=api.system_id
JOIN permission ui ON ui.system_id=s.id AND ui.code='ui.role.inheritance'
WHERE s.system_key='rbac' AND api.code='role.manage';
