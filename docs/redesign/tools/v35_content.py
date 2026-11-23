"""第二轮验收的代码证据、GRASP 职责与测试方法追溯。"""
from pathlib import Path
import json
R=Path(__file__).resolve().parents[2]

def deployment_results():
 version=(R/'redesign/VERSION').read_text().strip()
 path=R/f'implementation/deployment-v{version}.json'
 return json.loads(path.read_text()) if path.is_file() else {}

def grasp(table):
 return '''=== GRASP 职责分配与双实现

以 ROLE-11 新增继承为抽查入口。RoleController 校验路径 id 和请求体 juniorId 后调用 RoleUseCase。业务层检查 role.manage 与两端归属，从基础角色求可达集合，确认无环后写入关联。JpaManagementStore 委托 Spring Data JPA 插入关联，组合主键拒绝重复关系。

'''+table(['原则','代码位置与职责'],[
 ('Controller','RoleController 接收 HTTP，StructuredRoleService 或 DddRoleService 编排事务；不把业务防环放到 HTTP 层。'),
 ('Information Expert','RoleHierarchy 根据基础角色的可达集合判断环路；User 根据自身部门校验可分配的角色。'),
 ('Creator','DddUserService 根据当前用户资料创建 User，交由 User 校验目标角色的部门。'),
 ('Low Coupling','API 依赖 RoleUseCase，业务依赖 ManagementStore；领域规则使用 Java 集合，与 Spring Data JPA、HTTP 解耦。'),
 ('High Cohesion','Controller 处理输入输出，Service 处理事务与范围，RoleHierarchy 处理无环规则，适配器封装 JPA 数据访问。'),
 ('Polymorphism','用户与角色端口分别有 Structured 与 Ddd 实现。启动时按配置选择，目录服务由两套实现共用。'),
 ('Indirection','RoleUseCase 与 ManagementStore 分开调用方和实现方，便于契约测试与替换。'),
 ('Pure Fabrication','JpaManagementStore 管理直接关系；JpaEffectiveAccessStore 批量逐层读取基础角色；AccessPolicy 检查权限及部门边界。'),
 ('Protected Variations','共享 API、Data 和存储端口保护两种业务实现变化；同一契约套件验证外部行为。')],'3,7')+table(['比较项','结构化实现','DDD 实现'],[
 ('业务规则位置','StructuredRoleService 在事务脚本中调用共享防环规则并写入继承边。','DddRoleService 编排事务和范围，调用 Role.requireBaseRole 检查归属和环路。'),
 ('已有授权修改','Service 校验权限和范围，数据库拒绝重复分配。','User 校验角色部门，应用服务保存关联，数据库拒绝重复分配。'),
 ('继承图生命周期','事务从基础角色出发查询可达集合。','Role 接收相同的可达集合，检查其中是否包含当前角色。'),
 ('共同部分','API、认证、用例端口、数据库迁移、Spring Data JPA、日志及页面。','相同契约和基础设施，不复制两套数据库与接口。'),
 ('配置切换','BACKEND_STRATEGY=structured。','BACKEND_STRATEGY=ddd，重启生效。')],'2,5,5')+'''
=== 实际 Java Web 技术

Spring MVC 处理 HTTP，Jakarta Validation 校验 Controller 输入，Spring IoC 按配置装配，Spring 事务管理 JPA 写边界；Jackson 映射 JSON，Spring Security 校验 Bearer，请求和 AOP 日志通过 SLF4J、Logback 输出。Flyway 执行增量迁移，CloneFactory 0.0.2 在编译期生成对象拷贝。领域类使用普通 Java 对象，持久化由 Spring Data JPA 生成数据库访问。
'''

def verification(table):
 version=(R/'redesign/VERSION').read_text().strip()
 test_report=f'implementation/test-summary-v{version}.json'
 deployment_report=f'implementation/deployment-v{version}.json'
 data=json.loads((R/test_report).read_text())
 evidence=f'本版契约回归见 docs/{test_report}。'
 deployment=deployment_results()
 if deployment:
  evidence=f'本版 Swarm 部署及线上联调见 docs/{deployment_report}。'+evidence
  evidence+=f"结构化与 DDD 各通过 {len(deployment['acceptance']['structured']['checks'])} 项线上 HTTP 检查。"
 return '''=== 需求到测试方法与页面行为

共同契约按 IdentityContract、ManagementContract 与 RbacContract 分组，两个配置运行相同方法，共用 ContractFixture 的 MySQL 测试库和初始化数据。

'''+table(['需求','具体测试方法 / 复现','前端可观察结果'],[
 ('REQ-F-01 认证与鉴权','authenticatesAndRejectsBadCredentials；tokensContainIdentityAndAudienceWithoutPermissions；logoutAndExpiryInvalidateSession。','用户名手机号登录；错误凭据提示；退出后受保护页面拒绝访问。'),
 ('REQ-F-01 身份目录','listsIdentityDepartmentsByLevelAndKeyword、listsOnlyIdentityFieldsForDepartmentUsers、looksUpIdentityUserUnderTeacherCreationPermission、authenticatesEachRequestedAudienceAndRevokesWholeToken。','逐层查询部门及所属候选人，按目标系统判权。'),
 ('REQ-F-02 用户','assignsAndRevokesRoleWithConflictAndIdempotence；validatesInputAndRemovedEndpoints。','分页查看本部门账号，分配或撤销本部门角色。'),
 ('REQ-F-03 角色与权限','readsCatalogsWithoutWriteEndpoints；revocationIsVisibleToExistingTokenAndSystemsAreIsolated。','角色权限配置、引用冲突提示；旧令牌按当前许可执行。'),
 ('REQ-F-03 RBAC1','inheritsAcrossMultiplePathsAndRevokesForExistingTokens；rejectsHierarchyCyclesScopeAndReferences。','配置继承、有效权限并集、解除继承；自环和间接环 409，跨部门 403。'),
 ('REQ-F-04 部门','enforcesDirectDepartmentScope；readsCatalogsWithoutWriteEndpoints。','部门树只表达组织；管理员管理本部门直接成员。'),
 ('REQ-F-05 系统 / REQ-F-06 权限目录','readsCatalogsWithoutWriteEndpoints；isolatesUiAndApiPermissionsAndSupportsNarrowSelections。','系统与权限目录只读；授权下拉分页完整加载，不依赖无关目录管理权。'),
 ('REQ-F-07 课程接入','tooling/acceptance.py 对已部署的老师后端调用 /login、/me、学期维护、课程列表与详情、无权审核、撤权及退出。','课程和学期菜单分别受 UI 码控制；API 许可独立检查。'),
 ('REQ-N-01 安全','isolatesUiAndApiPermissionsAndSupportsNarrowSelections；upgradesExistingGrantsOnceAndKeepsUiRevocationIndependent；frontend/src/permissions.test.ts。','无菜单码时禁用菜单，无按钮码时禁用按钮；仅有 API 码也不能启用控件。'),
 ('REQ-N-02 性能','tooling/benchmark.py；测量环境、样本量与结果见性能需求。','用户列表限定本部门并分页返回。'),
 ('REQ-N-03 可用性','Swarm 健康检查和历史会话恢复记录。','依赖故障拒绝业务；恢复后刷新页面与会话。'),
 ('REQ-N-04 可修改性','Structured 与 Ddd 的六个契约测试类；tooling/check-architecture.py；Swarm 同镜像切换业务策略。','切换后同一套页面和 HTTP 请求继续工作。'),
 ('REQ-N-05 交付与运行','Maven verify、tooling/check-copy-artifact.py、build-api.py、build-design.sh；Swarm 镜像与服务记录。','两个前端、API 文档、覆盖率报告可访问；菜单请求对应后端日志。')],'3,7,5')+f'''
=== 本轮测试与交付入口

v{version} 平台测试 {data['platformTests']} 项、老师 CloneFactory 测试 {data['cloneFactoryTests']} 项和前端组件测试 {data['frontendTests']} 项全部通过。JaCoCo 0.8.14 的平台行覆盖率 {data['coverage']['LINE']['percent']}%，分支覆盖率 {data['coverage']['BRANCH']['percent']}%；生成的 CloneFactory 工具类单独由拷贝测试与运行包检查验证，覆盖率排除该生成类。

'''+table(['产物','访问与复现'],[
 ('权限后台','/platform/'),
 ('老师原版课程前端','/course/'),
 ('平台 API 文档','运行 npm ci --prefix docs/api 后执行 python3 tooling/build-api.py。'),
 ('覆盖率报告','执行 ./mvnw verify 生成 backend/target/site/jacoco/。'),
 ('部署与验收证据',evidence),
 ('课程提交','代码推送 GitHub，由已配置的自动同步更新 Gitee；课程网站提交与口试预约由组员完成。')],'3,7')
