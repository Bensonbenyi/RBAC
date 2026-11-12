-- RBAC 第一版评审草案；MySQL 8.4 LTS / InnoDB。
-- 在选定的空数据库中执行；不创建数据库、不删除已有表、不预置管理员。
-- 暂按用户至多一个部门；每个角色必须属于一个部门。
SET NAMES utf8mb4;

CREATE TABLE department (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门编号',
    name VARCHAR(100) NOT NULL COMMENT '部门名称',
    parent_id BIGINT NULL COMMENT '父部门编号；根部门为 NULL',
    PRIMARY KEY (id),
    KEY idx_department_parent (parent_id),
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_id)
        REFERENCES department (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_department_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='部门';

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户编号',
    username VARCHAR(64) NOT NULL COMMENT '用户名；全局唯一且区分大小写',
    name VARCHAR(100) NOT NULL COMMENT '姓名',
    phone VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '手机号；格式由 API 校验',
    department_id BIGINT NULL COMMENT '所属部门；暂允许未分配',
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    KEY idx_app_user_department (department_id),
    CONSTRAINT fk_app_user_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_app_user_username CHECK (CHAR_LENGTH(TRIM(username)) > 0),
    CONSTRAINT ck_app_user_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_app_user_phone CHECK (CHAR_LENGTH(TRIM(phone)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='用户账号';

CREATE TABLE `role` (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色编号',
    name VARCHAR(100) NOT NULL COMMENT '角色名称；部门内唯一',
    department_id BIGINT NOT NULL COMMENT '角色所属部门；限定本部门可分配角色',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_department_name (department_id, name),
    CONSTRAINT fk_role_department FOREIGN KEY (department_id)
        REFERENCES department (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_role_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='角色';

CREATE TABLE business_system (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '系统编号',
    system_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '系统标识；全局唯一',
    name VARCHAR(100) NOT NULL COMMENT '系统名称',
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_system_key (system_key),
    CONSTRAINT ck_business_system_key CHECK (CHAR_LENGTH(TRIM(system_key)) > 0),
    CONSTRAINT ck_business_system_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='业务系统';

CREATE TABLE permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限编号',
    system_id BIGINT NOT NULL COMMENT '所属业务系统编号',
    code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '权限编码；系统内唯一',
    name VARCHAR(100) NOT NULL COMMENT '权限名称',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_system_code (system_id, code),
    CONSTRAINT fk_permission_system FOREIGN KEY (system_id)
        REFERENCES business_system (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_permission_code CHECK (CHAR_LENGTH(TRIM(code)) > 0),
    CONSTRAINT ck_permission_name CHECK (CHAR_LENGTH(TRIM(name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='操作权限';

CREATE TABLE user_role (
    user_id BIGINT NOT NULL COMMENT '用户编号',
    role_id BIGINT NOT NULL COMMENT '角色编号',
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
        REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
        REFERENCES `role` (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='用户与角色关联';

CREATE TABLE role_permission (
    role_id BIGINT NOT NULL COMMENT '角色编号',
    permission_id BIGINT NOT NULL COMMENT '权限编号',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_role_permission_permission (permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id)
        REFERENCES `role` (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES permission (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='角色与权限关联';

