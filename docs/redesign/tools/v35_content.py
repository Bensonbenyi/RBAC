"""第二轮验收的代码证据、GRASP 职责与测试方法追溯。"""
from pathlib import Path
import json
R=Path(__file__).resolve().parents[2]

def grasp(table):
 return '''=== GRASP 职责分配与双实现

以 ROLE-11 新增继承为抽查入口。RoleController 校验正整数路径，取得操作者，交给 RoleUseCase；业务实现先检查 role.manage，在事务内锁定部门并核对两端归属，再判断继承图并写入关联。ManagementStore 隔离业务与 SQL，JdbcManagementStore 完成锁定读与写入。

'''+table(['原则','代码位置与职责'],[
 ('Controller','RoleController 接收 HTTP，StructuredRoleService 或 DddRoleService 编排事务；不把业务防环放到 HTTP 层。'),
 ('Information Expert','DDD RoleHierarchy 持有继承边快照，执行 requireAcyclic；Role 维护自身 permissionIds，User 维护 roleIds。'),
 ('Creator','DddRoleService 取得部门边集合并创建 RoleHierarchy，生命周期限本次继承用例。'),
 ('Low Coupling','API 依赖 RoleUseCase，业务依赖 ManagementStore；领域规则使用 Java 集合，与 Spring JDBC、HTTP 解耦。'),
 ('High Cohesion','Controller 处理输入输出，Service 处理事务与范围，RoleHierarchy 处理无环规则，适配器处理 SQL。'),
 ('Polymorphism','五个用例端口分别有 Structured 与 Ddd 实现，启动条件只装配配置选中的实现。'),
 ('Indirection','RoleUseCase 与 ManagementStore 分开调用方和实现方，便于契约测试与替换。'),
 ('Pure Fabrication','JdbcManagementStore、AccessPolicy 是技术与策略对象，集中持久化和权限边界，避免分散到实体。'),
 ('Protected Variations','共享 API、Data 和存储端口保护两种业务实现变化；同一契约套件验证外部行为。')],'3,7')+table(['比较项','结构化实现','DDD 实现'],[
 ('业务规则位置','StructuredRoleService 在事务脚本中进行队列遍历、防环与存储编排。','DddRoleService 编排事务和范围，RoleHierarchy 执行领域防环。'),
 ('已有授权修改','Service 校验集合及调用存储。','User、Role 管理授权标识集合，应用服务保存变化。'),
 ('继承图生命周期','方法局部 edges、队列和 visited。','不可变 RoleHierarchy 快照；Data.Inheritance 表达边。'),
 ('共同部分','API、认证、用例端口、数据库迁移、JDBC、日志及页面。','相同契约和基础设施，不复制两套数据库与接口。'),
 ('配置切换','BACKEND_STRATEGY=structured。','BACKEND_STRATEGY=ddd，重启生效。')],'2,5,5')+'''
=== 实际 Java Web 技术

Spring MVC 处理 HTTP，Jakarta Validation 校验 Controller 输入，Spring IoC 按配置装配，Spring 事务管理 JDBC 写边界；Jackson 映射 JSON，Spring Security 校验 Bearer，请求和 AOP 日志通过 SLF4J、Logback 输出。Flyway 执行增量迁移，CloneFactory 0.0.2 在编译期生成对象拷贝。领域类使用普通 Java 对象，持久化使用 Spring JDBC。
'''

def verification(table):
 data=json.loads((R/'implementation/test-summary-v3.5.0.json').read_text())
 return '''=== 需求到测试方法与页面行为

共同测试位于 backend/src/test/java/cn/edu/xmu/rbac/ContractSuite.java，StructuredContractTest 和 DddContractTest 各继承并执行 16 项。表内方法均为源码中的实际名称；同一方法可覆盖多项需求。

'''+table(['需求','具体测试方法 / 复现','前端可观察结果'],[
 ('REQ-F-01 认证与鉴权','authenticatesAndRejectsBadCredentials；tokensContainIdentityAndAudienceWithoutPermissions；logoutExpiryAndDeletionInvalidateSession。','用户名手机号登录；错误凭据提示；退出后受保护页面拒绝访问。'),
 ('REQ-F-02 用户','createsUpdatesAndDeletesUserWithGeneratedIdentity；assignsAndRevokesRoleWithConflictAndIdempotence；validatesInputAndProtectedFields。','单项新建、修改、删除及分配撤销；资料编辑不能修改部门和编号。'),
 ('REQ-F-03 角色与权限','directoryCrudHonoursReferencesAndCaseSensitiveCodes；revocationIsVisibleToExistingTokenAndSystemsAreIsolated。','角色权限配置、引用冲突提示；旧令牌按当前许可执行。'),
 ('REQ-F-03 RBAC1','inheritsAcrossMultiplePathsAndRevokesForExistingTokens；rejectsHierarchyCyclesScopeAndReferences；serializesConcurrentOppositeInheritanceWrites。','配置继承、有效权限并集、解除继承；自环和间接环 409，跨部门 403；并发反向连边仅一条成功。'),
 ('REQ-F-04 部门','enforcesDirectDepartmentScope；directoryCrudHonoursReferencesAndCaseSensitiveCodes。','部门树只表达组织；管理员管理本部门直接成员。'),
 ('REQ-F-05 系统 / REQ-F-06 权限目录','directoryCrudHonoursReferencesAndCaseSensitiveCodes；isolatesUiAndApiPermissionsAndSupportsNarrowSelections。','系统与权限目录维护；授权下拉分页完整加载，不依赖无关目录管理权。'),
 ('REQ-F-07 课程接入','tooling/acceptance.py 对已部署的老师后端调用 /me、学期 CRUD、课程列表与详情、无权审核、撤权及退出。','课程和学期菜单分别受 UI 码控制；API 许可独立检查。'),
 ('REQ-N-01 安全','isolatesUiAndApiPermissionsAndSupportsNarrowSelections；upgradesExistingGrantsOnceAndKeepsUiRevocationIndependent；frontend/src/permissions.test.ts。','无菜单码时禁用菜单，无按钮码时禁用按钮；仅有 API 码也不能启用控件。'),
 ('REQ-N-02 性能','tooling/benchmark.py；assets/v33/performance-baseline.json 为 v3.3.0 历史测量。','分页有界；当前版本性能需按相同数据与负载重新测量后比较。'),
 ('REQ-N-03 可用性','tooling/resilience.py 与历史恢复记录；本轮记录实际部署后健康与会话结果。','依赖故障拒绝业务；恢复后刷新页面与会话。'),
 ('REQ-N-04 可修改性','StructuredContractTest / DddContractTest；tooling/check-architecture.py；Swarm 同镜像切换业务策略。','切换后同一套页面和 HTTP 请求继续工作。'),
 ('REQ-N-05 交付与运行','Maven verify、tooling/check-copy-artifact.py、build-api.py、build-design.sh；Swarm 镜像与服务记录。','两个前端、API 文档、覆盖率报告可访问；菜单请求对应后端日志。')],'3,7,5')+f'''
=== 本轮测试与交付入口

v3.5.0 平台测试 {data['platformTests']} 项、老师 CloneFactory 测试 {data['cloneFactoryTests']} 项和前端组件测试 {data['frontendTests']} 项全部通过。JaCoCo 0.8.14 的平台行覆盖率 {data['coverage']['LINE']['percent']}%，分支覆盖率 {data['coverage']['BRANCH']['percent']}%；生成的 CloneFactory 工具类单独由拷贝测试与运行包检查验证，覆盖率排除该生成类。

'''+table(['产物','访问与复现'],[
 ('权限后台','http://113.44.144.119/platform/'),
 ('老师课程系统接入','http://113.44.144.119/course/'),
 ('平台 API 文档','http://113.44.144.119/api-docs/；npm ci --prefix docs/api 后执行 python3 tooling/build-api.py。'),
 ('覆盖率报告','http://113.44.144.119/coverage/；执行 ./mvnw verify 生成 backend/target/site/jacoco/。'),
 ('部署与验收证据','docs/implementation/整改报告-v3.5.0.md 与验收 JSON；口试步骤见同目录第二轮演示脚本.md。'),
 ('课程提交','代码推送 GitHub，由已配置的自动同步更新 Gitee；课程网站提交与口试预约由组员完成。')],'3,7')
