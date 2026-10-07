CREATE TABLE company (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_department_name(company_id, name),
  FOREIGN KEY (company_id) REFERENCES company(id)
);
CREATE TABLE employee (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  phone VARCHAR(30) NOT NULL UNIQUE,
  company_id BIGINT NOT NULL,
  department_id BIGINT,
  position_name VARCHAR(120) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  super_admin BOOLEAN NOT NULL DEFAULT FALSE,
  department_admin BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  INDEX ix_employee_department(department_id, active),
  FOREIGN KEY (company_id) REFERENCES company(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);
CREATE TABLE business_system (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  system_key VARCHAR(60) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL UNIQUE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  client_secret VARCHAR(200) NOT NULL,
  directory_secret VARCHAR(200) NOT NULL,
  redirect_uri VARCHAR(300),
  logout_redirect_uri VARCHAR(300),
  directory_revision BIGINT NOT NULL DEFAULT 0,
  management_revision BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE system_department (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  system_id BIGINT NOT NULL,
  department_id BIGINT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_system_department(system_id, department_id),
  FOREIGN KEY (system_id) REFERENCES business_system(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);
CREATE TABLE permission_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  system_id BIGINT NOT NULL,
  permission_key VARCHAR(160) NOT NULL,
  module_name VARCHAR(160) NOT NULL,
  feature_name VARCHAR(160) NOT NULL,
  action_name VARCHAR(80) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_permission(system_id, permission_key),
  FOREIGN KEY (system_id) REFERENCES business_system(id)
);
CREATE TABLE business_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  system_id BIGINT NOT NULL,
  department_id BIGINT,
  name VARCHAR(120) NOT NULL,
  template BOOLEAN NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_department_role(system_id, department_id, name),
  FOREIGN KEY (system_id) REFERENCES business_system(id),
  FOREIGN KEY (department_id) REFERENCES department(id)
);
CREATE TABLE role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  FOREIGN KEY (role_id) REFERENCES business_role(id),
  FOREIGN KEY (permission_id) REFERENCES permission_item(id)
);
CREATE TABLE role_assignment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id BIGINT NOT NULL,
  system_id BIGINT NOT NULL,
  role_id BIGINT,
  revoked BOOLEAN NOT NULL DEFAULT TRUE,
  changed_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_employee_system(employee_id, system_id),
  INDEX ix_assignment_role(role_id, revoked),
  FOREIGN KEY (employee_id) REFERENCES employee(id),
  FOREIGN KEY (system_id) REFERENCES business_system(id),
  FOREIGN KEY (role_id) REFERENCES business_role(id)
);
CREATE TABLE audit_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  occurred_at TIMESTAMP(6) NOT NULL,
  actor_id BIGINT,
  department_id BIGINT,
  previous_department_id BIGINT,
  system_id BIGINT,
  event_type VARCHAR(60) NOT NULL,
  object_ref VARCHAR(160) NOT NULL,
  result VARCHAR(30) NOT NULL,
  detail TEXT NOT NULL,
  INDEX ix_audit_scope(department_id, occurred_at),
  INDEX ix_audit_time(occurred_at)
);
CREATE TABLE batch_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_id BIGINT NOT NULL,
  system_id BIGINT NOT NULL,
  role_id BIGINT,
  revoke BOOLEAN NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE batch_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  employee_id BIGINT NOT NULL,
  expected_version BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  message VARCHAR(400) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_batch_item(job_id, employee_id),
  FOREIGN KEY (job_id) REFERENCES batch_job(id)
);
CREATE TABLE seed_marker (
  marker_key VARCHAR(80) PRIMARY KEY,
  applied_at TIMESTAMP(6) NOT NULL
);
