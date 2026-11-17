#!/usr/bin/env bash
set -euo pipefail
rbac_password="$(cat /run/secrets/rbac_db_password)"
course_password="$(cat /run/secrets/course_db_password)"
MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" mysql --protocol=socket -uroot <<SQL
CREATE DATABASE IF NOT EXISTS rbac CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE DATABASE IF NOT EXISTS course_project CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER IF NOT EXISTS 'rbac'@'%' IDENTIFIED BY '${rbac_password}';
CREATE USER IF NOT EXISTS 'course'@'%' IDENTIFIED BY '${course_password}';
GRANT ALL PRIVILEGES ON rbac.* TO 'rbac'@'%';
GRANT ALL PRIVILEGES ON course_project.* TO 'course'@'%';
SQL
