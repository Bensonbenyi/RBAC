-- Synthetic course data for the selected integration demonstration.
INSERT INTO term(name,starts_on,ends_on,is_current,creator,created_at,modifier,modi_at)
SELECT '2026 秋季课程演示','2026-09-01','2027-01-31',0,'seed',NOW(6),'seed',NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM term WHERE name='2026 秋季课程演示');
INSERT INTO course(term_id,code,name,description,work_mode,status,review_requested_at,creator,created_at,modifier,modi_at)
SELECT id,'RBAC-REVIEW-01','软件工程课程审核','RBAC 接入演示','group',2,NOW(6),'seed',NOW(6),'seed',NOW(6)
FROM term WHERE name='2026 秋季课程演示'
AND NOT EXISTS (SELECT 1 FROM course WHERE code='RBAC-REVIEW-01');
INSERT INTO course(term_id,code,name,description,work_mode,status,review_requested_at,creator,created_at,modifier,modi_at)
SELECT id,'RBAC-REVIEW-02','权限模型课程审核','RBAC 接入演示','group',2,NOW(6),'seed',NOW(6),'seed',NOW(6)
FROM term WHERE name='2026 秋季课程演示'
AND NOT EXISTS (SELECT 1 FROM course WHERE code='RBAC-REVIEW-02');

INSERT INTO course(term_id,code,name,description,work_mode,status,review_requested_at,creator,created_at,modifier,modi_at)
SELECT id,'RBAC-REVIEW-03','分层架构课程审核','RBAC 接入演示','group',2,NOW(6),'seed',NOW(6),'seed',NOW(6)
FROM term WHERE name='2026 秋季课程演示'
AND NOT EXISTS (SELECT 1 FROM course WHERE code='RBAC-REVIEW-03');
