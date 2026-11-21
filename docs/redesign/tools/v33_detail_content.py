"""Detailed tables retained from v3.2.2, revised against the running contracts."""
FIELD_TABLES = [
 ('role_inheritance 角色继承', [('senior_role_id','BIGINT','否','联合主键及外键 role.id','获得权限的高级角色'),('junior_role_id','BIGINT','否','联合主键及外键 role.id','提供权限的基础角色；禁止自环和间接环')]),
 ('department 部门', [('id','BIGINT','否','主键，自增','部门编号'),('name','VARCHAR(100)','否','非空文本','部门名称'),('parent_id','BIGINT','是','外键 department.id','父部门；根节点为空')]),
 ('app_user 用户', [('id','BIGINT','否','主键，自增','用户编号'),('username','VARCHAR(64)','否','全局唯一，区分大小写','创建后固定的登录名'),('name','VARCHAR(100)','否','非空文本','用户姓名'),('phone','VARCHAR(20)','否','ASCII，API 校验格式','手机号，本版登录凭据'),('department_id','BIGINT','否','外键 department.id','唯一所属部门，创建后固定')]),
 ('role 角色', [('id','BIGINT','否','主键，自增','角色编号'),('name','VARCHAR(100)','否','部门内唯一','角色名称'),('department_id','BIGINT','否','外键 department.id','角色归属及可分配范围')]),
 ('business_system 业务系统', [('id','BIGINT','否','主键，自增','系统编号'),('system_key','VARCHAR(64)','否','ASCII，全局唯一','系统标识，创建后固定'),('name','VARCHAR(100)','否','非空文本','系统名称')]),
 ('permission 操作权限', [('id','BIGINT','否','主键，自增','权限编号'),('system_id','BIGINT','否','外键 business_system.id','权限所属系统，创建后固定'),('code','VARCHAR(100)','否','ASCII，系统内唯一','权限码，创建后固定'),('name','VARCHAR(100)','否','非空文本','操作名称')]),
 ('user_role 用户角色关联', [('user_id','BIGINT','否','联合主键，外键 app_user.id','被授权用户'),('role_id','BIGINT','否','联合主键，外键 role.id','用户持有的角色')]),
 ('role_permission 角色权限关联', [('role_id','BIGINT','否','联合主键，外键 role.id','持有权限的角色'),('permission_id','BIGINT','否','联合主键，外键 permission.id','授予角色的权限')]),
 ('login_session 技术会话', [('id','CHAR(36)','否','主键，ASCII UUID','对应 JWT jti'),('user_id','BIGINT','否','外键 app_user.id','当前登录用户'),('system_key','VARCHAR(64)','否','ASCII，对应令牌 aud 首项','本次默认目标系统'),('expires_at','TIMESTAMP(6)','否','UTC 到期时刻','到期后拒绝认证'),('revoked_at','TIMESTAMP(6)','是','撤销时刻','为空表示尚未撤销')])]

# key, title, requirement, operation keys, package, actor, description, trigger,
# precondition, steps, extensions, postcondition, activity, module class plates
CASES = [
 ('AUTH-001','登录','REQ-F-01',['AUTH-01'],'身份认证','用户及接入系统客户端','提交用户名、手机号和目标系统列表，取得对应系统的登录凭据。','用户打开平台后台或课程系统登录页。','两个业务系统已登记，用户存在并通过角色取得目标系统 login 权限。',[
 ('校验 username、password 和 systems。','password 承载手机号，systems 包含至少一个不重复的非空标识。'),
 ('按用户名读取账号并匹配手机号。','失败统一返回 401。'),
 ('读取目标系统及当前 RBAC 登录许可。','无系统登录资格返回 403。'),
 ('同事务保存登录会话并签发 JWT。','返回 accessToken、tokenType、expiresIn。')],
 [('字段缺失、格式错误或目标系统重复','400，不调用登录服务。'),('账号不存在或手机号错误','401，返回统一凭据错误。'),('目标系统不存在或无 login 权限','403，不建立成功会话。')],
 '成功后客户端持有目标系统的 Bearer 凭据；JWT 只保存身份及会话信息。','activity-login',None),
 ('AUTH-002','查看本人资料 修改与退出','REQ-F-01',['AUTH-02','AUTH-04','AUTH-03'],'当前用户','已登录用户','读取本人资料和实时权限，修改姓名或手机号，或撤销当前会话。','用户查看个人资料、保存允许修改的字段或点击退出。','令牌签名、会话、账号和本系统 login 权限有效。',[
 ('从认证上下文取得本人编号。','请求不能指定其他用户或部门。'),('读取本人资料或校验资料白名单。','仅 name、phone；未提交字段保持原值。'),('提交资料修改，或撤销当前会话。','退出写入 revoked_at，不影响其他会话。')],
 [('令牌失效、会话已撤销或账号已删除','401。'),('资料字段格式错误、空对象或未知字段','400，不修改资料。'),('当前系统登录许可已撤销','403。')],
 '资料更新影响后续查询和新登录；退出后旧令牌返回 401。',None,None),
 ('USER-001','用户维护','REQ-F-02',['USER-01','USER-02','USER-03','USER-04','USER-05'],'用户管理','持有对应用户操作权限的管理用户','查询、创建、修改及删除本部门直接成员。','管理用户进入用户页面并选择一项操作。','已登录 rbac，具有本次操作权限和非空部门。',[
 ('检查 user.view、user.create、user.edit 或 user.delete。','按具体方法检查，列表按操作者部门过滤。'),('读取目标或校验新建资料。','新建部门由当前身份推导；用户名全局唯一。'),('在事务内保存姓名、手机号或新账号。','用户名、编号、部门均为固定字段。'),('删除时清理会话与用户角色后删除账号。','全部删除步骤在同一事务中提交。')],
 [('目标用户跨部门','403。'),('用户名重复或并发冲突','409，当前事务回滚。'),('目标不存在','404。'),('输入含 id、username、departmentId 等修改禁用字段','400。')],
 '资料持久化；已删除用户的后续认证失败。',None,'User'),
 ('DEPT-001','部门维护','REQ-F-04',['DEPT-01','DEPT-02','DEPT-03','DEPT-04'],'部门目录','持有 department.manage 的管理用户','查询多级组织目录，创建、重命名或删除无引用部门。','管理员打开部门目录或提交目录维护。','已登录 rbac 并通过部门管理权限检查。',[
 ('分页读取 id、name、parentId。','前端根据 parentId 组装层级，父子关系保持明确。'),('在指定父节点下新建部门或新建根节点。','parentId 可空；非空时验证父部门存在。'),('重命名部门。','更新只接收 name，父节点固定。'),('检查子部门、成员及角色引用后删除。','存在引用时拒绝删除。')],
 [('父部门或目标部门不存在','404。'),('名称或路径编号无效','400。'),('部门仍被引用','409，保留原数据。')],
 '组织层级及外键完整；部门通过角色限制分配范围。',None,'Department'),
 ('SYS-001','业务系统维护','REQ-F-05',['SYS-01','SYS-02','SYS-03','SYS-04'],'系统目录','持有 system.manage 的管理用户','登记和维护平台自身及课程系统的标识与名称。','管理员进入系统目录并选择维护操作。','已登录 rbac 并通过系统管理权限检查。',[
 ('读取系统列表或提交 systemKey 与 name。','系统标识全局唯一。'),('新建系统，或仅修改展示名称。','systemKey 创建后固定。'),('检查权限引用后删除系统。','先处理所属权限，数据库限制被引用系统删除。')],
 [('系统标识重复','409。'),('目标不存在','404。'),('系统仍有权限定义','409。')],
 '系统目录更新，权限仍由角色权限关系授予。',None,'System'),
 ('PERM-001','操作权限维护','REQ-F-06',['PERM-01','PERM-02','PERM-03','PERM-04'],'权限目录','持有 permission.manage 的管理用户','在所属业务系统内查询、新建、改名及删除权限。','管理员选择一个系统的权限目录。','已登录 rbac，具有权限管理许可。',[
 ('验证所属业务系统，分页查询权限。','systemId 来自路径。'),('新建 code、name 或修改展示名称。','systemId、code 创建后固定。'),('检查角色权限引用后删除。','权限码在所属系统内唯一。')],
 [('系统或目标权限不存在','404。'),('权限码重复','409。'),('仍被角色引用','409。'),('编码或名称无效','400。')],
 '目录更新；后续角色配置引用现有权限编号。',None,'Permission'),
 ('ROLE-001','角色维护与权限配置','REQ-F-03',['ROLE-01','ROLE-02','ROLE-03','ROLE-04','ROLE-05','ROLE-06','ROLE-07','ROLE-08','SYS-05','PERM-05'],'角色授权','持有 role.manage 的管理用户','维护本部门角色及其跨业务系统的权限集合。','管理用户创建角色或编辑现有角色权限。','已登录 rbac，角色与操作者属于同一部门。',[
 ('按本部门分页查询角色或创建角色。','部门从当前身份推导，名称在部门内唯一。'),('查询角色权限，选择现有权限授予或撤销。','授予与撤销幂等；权限可来自两个接入系统。'),('修改角色名称或检查引用后删除。','角色部门固定，存在用户或权限关系时拒绝删除。')],
 [('角色跨部门或无操作许可','403。'),('角色或权限不存在','404。'),('部门内重名或删除引用冲突','409。')],
 '角色及权限集合持久化，后续请求依据当前关系鉴权。',None,'Role'),
 ('GRANT-001','分配用户角色','REQ-F-02',['USER-06','USER-07','ROLE-09'],'用户角色授权','持有 user.role.assign 的管理用户','查看用户已有角色，为本部门用户分配本部门角色。','管理员选择用户并提交待分配角色。','已登录 rbac，操作者具有分配许可和非空部门。',[
 ('检查分配操作权限并读取用户、角色。','操作者身份来自认证上下文。'),('校验操作者、目标用户和角色同部门。','部门与角色的一对多关系限定可分配范围。'),('检查用户尚未持有该角色。','DDD 由 User.assignRole 检查集合重复和部门。'),('事务内新增 user_role 并提交。','联合主键裁决并发重复分配。')],
 [('无操作许可或跨部门','403，不写入关系。'),('用户或角色不存在','404。'),('已分配或并发重复插入','409，事务回滚。')],
 '新增关系持久化；用户下一次鉴权合并新角色的权限。','activity-assign',None),
 ('GRANT-002','撤销用户角色','REQ-F-02',['USER-08'],'用户角色授权','持有 user.role.assign 的管理用户','移除本部门用户的一项角色分配。','管理员撤销用户已有角色。','已登录 rbac，操作者、目标用户和角色同部门。',[
 ('检查操作权限及用户、角色归属。','跨部门请求在写入前拒绝。'),('移除指定 user_role。','关系已不存在时仍返回 204。')],
 [('无操作许可或跨部门','403。'),('用户或角色不存在','404。')],
 '后续鉴权按剩余角色取并集；其他角色仍授予同一权限时继续允许。',None,None),
 ('AUTHZ-001','运行时鉴权与统一身份','REQ-F-01',['AUTH-05','AUTH-06','AUTH-07','AUTH-08','AUTH-09'],'身份与鉴权','已登录用户及接入系统客户端','查询当前身份的一项权限，或取得统一用户身份资料。','平台或课程系统准备执行受控操作。','签名、会话、账号及当前系统 login 权限有效。',[
 ('从令牌和数据库会话确定用户及系统。','调用方只提交权限码、operationId 或身份编号。'),('读取当前用户角色及角色权限并集。','按当前 audience 限定权限命名空间。'),('返回 allowed 或统一用户资料。','未知 operationId 返回 404。课程身份目录要求 TEC。')],
 [('令牌或会话失效','401。'),('本系统 login 已撤销，或无身份查询资格','403。'),('编码已知但未授予','200，allowed=false。'),('数据库或身份依赖不可用','平台 503；课程拒绝业务操作。')],
 '本次权限结论来自已提交的当前关系；接入系统独立执行拒绝。',None,'Auth')]

CASES.append(('HIERARCHY-001','角色继承管理','REQ-F-03',['ROLE-10','ROLE-11','ROLE-12','ROLE-13'],'角色继承','持有 role.manage 的本部门管理员','建立同部门无环角色层次，使高级角色获得基础角色权限。','在角色页选择配置继承。','已登录，具有 API 许可，前端控件还需要 ui.role.inheritance。',[
 ('查询直接基础角色及有效权限。','直接边与传递闭包分开显示。'),('添加继承边。','事务先锁部门，再读取当前图；两套实现调用 RoleHierarchy，检查基础角色能否走回高级角色。'),('解除指定继承边。','其他路径及直接授权继续有效；JWT 不缓存权限。')],
 [('自环或间接环','409 ROLE_CYCLE，事务不落边。'),('跨部门或无 API 许可','403。'),('角色不存在','404。'),('角色仍被继承关系引用时删除','409，先解除关系。')],
 '继承图保持 DAG；两系统的界面及 API 决策都使用当前有效权限。',None,None))

INPUTS = {
 'AUTH-01': 'LoginDTO 接收 username、password 与非空且无重复的 systems。',
 'AUTH-02':'无请求体；身份来自 Bearer。','AUTH-03':'无请求体；撤销当前 JWT 对应会话。','AUTH-04':'ManagementDTOs.ProfileDTO：name、phone 至少出现一项。',
 'AUTH-05':'路径 permission；身份和目标系统来自令牌。','AUTH-06':'路径 operationId；由映射表定位权限码。','AUTH-07':'路径 userId 为字符串。','AUTH-08':'parentId 可空，keyword 可选，page 从 1 开始，pageSize 默认为 20。','AUTH-09':'departmentId 为正整数，支持 keyword、page、pageSize。',
 'USER-03':'ManagementDTOs.CreateUserDTO：username、name、phone。','USER-04':'ManagementDTOs.ProfileDTO：name、phone 至少出现一项。','USER-07':'ManagementDTOs.AssignRoleDTO：roleId；目标用户来自路径。',
 'ROLE-03':'ManagementDTOs.NameDTO：name。','ROLE-04':'ManagementDTOs.NameDTO：name。','DEPT-02':'ManagementDTOs.CreateDepartmentDTO：name、parentId；parentId 可空。','DEPT-03':'ManagementDTOs.NameDTO：name。','SYS-02':'ManagementDTOs.CreateSystemDTO：systemKey、name。','SYS-03':'ManagementDTOs.NameDTO：name。','PERM-02':'ManagementDTOs.CreatePermissionDTO：code、name；systemId 来自路径。','PERM-03':'ManagementDTOs.NameDTO：name。'}

def operation_input(op):
 if op.function in ['inherit','uninherit']:return '路径 id 为高级角色、juniorId 为基础角色，均为正整数；无请求体。'
 if op.key in INPUTS:return INPUTS[op.key]
 if op.function=='options':return ('查询参数 systemId；' if op.module=='Permission' else '')+'page 从 0 开始，size 默认为 20、最大 100；前端遍历分页。'
 if op.function=='list':return ('路径 systemId；' if op.module=='Permission' else '')+'page 从 0 开始，size 默认为 20、最大 100。'
 return '路径 '+('id、permissionId。' if op.module=='Role' and op.function in ['grant','revoke'] else 'id、roleId。' if op.key=='USER-08' else 'id。')+'无请求体。'

def controller_method(op):
 if op.key=='AUTH-07':return 'IdentityUserController.get'
 if op.key=='AUTH-08':return 'IdentityDepartmentController.list'
 if op.key=='AUTH-09':return 'IdentityDepartmentController.users'
 return ('AuthController' if op.module=='Auth' else op.module+'Controller')+'.'+op.function

def calls_table(ops):
 rows='\n'.join('|`'+controller_method(o)+'` |`'+o.method+' '+o.route+'` |'+o.rule for o in ops)
 return '\n[.scene-detail]\n--\n[cols="3,4,3",options="header",frame=all,grid=all]\n|===\n|图中处理／实际方法 |调用说明 URL |功能\n'+rows+'\n|===\n--\n'

def case_table(c):
 key,title,req,keys,package,actor,description,trigger,pre,steps,errors,post,*_=c
 return ('\n[cols="1,1",frame=all,grid=all,role="use-case"]\n|===\n'
  +f'|*用例编号：* UPMS-{key} |*用例名称：* {title}\n2+|*级别：* 用户目标\n2+|*包：* {package}\n2+|*参与者：* {actor}\n2+|*描述：* {description}\n2+|*触发事件：* {trigger}\n2+|*前置条件：* {pre}\n'
  +'|*主成功场景：* |*信息*\n'+'\n'.join(f'|{i}. {a} |{b}' for i,(a,b) in enumerate(steps,1))
  +'\n|*扩展场景：* |*处理及信息*\n'+'\n'.join('|'+a+' |'+b for a,b in errors)
  +f'\n2+|*后置条件：* {post}\n|===\n\n对应需求 {req}。\n')

def architecture_details(fig,table):
 s='''=== 两套架构设计

==== 分层与运行装配

两套业务实现共用 API 输入层、Service Port 和 Infrastructure。结构化服务在事务内组织规则与存储调用；DDD 应用服务通过领域对象处理资料和角色归属规则。两套实现都通过存储端口调用 Spring Data JPA，查询返回共享 Data record。
'''
 for mode,label in [('structured','结构化'),('ddd','DDD')]:s+=fig('packages-'+mode,label+'设计 分层包图')
 s+=fig('architecture-runtime','运行装配与依赖结构')
 s+='''[source,yaml]
----
app:
  backend-strategy: ${BACKEND_STRATEGY:structured}
----

app.backend-strategy 接受 structured 或 ddd。@ConditionalOnProperty 按配置注册五个用例端口的实现；默认 structured。切换后 API、数据库迁移及前端保持相同契约。认证 AuthService 与两种策略共用，应用重新启动后才使用新配置。

==== 系统总体架构类图

下列两图完整列出五类资源的 Controller、UseCase、Service 以及共享存储契约，说明类之间的依赖和实现关系。运行装配图说明策略选择，模块类图进一步展示输入、数据对象、权限策略和 JPA 适配器。
'''
 for mode,label in [('structured','结构化'),('ddd','DDD')]:s+=fig('architecture-classes-'+mode,'系统总体架构类图 '+label)
 s+='''==== 领域对象与数据契约

DDD User 持有原 Data.User 和当前 Profile。Role、Department、System 和 Permission 维护名称，编号及归属由共享 Data record 表达。应用服务检查权限、对象存在性和部门范围，并通过存储端口保存直接授权关系。
'''
 s+=fig('domain','DDD 领域对象完整类图')
 s+=table(['领域对象','实际状态','行为与职责'],[
 ('User','Data.User、Data.Profile','changeProfile 合并允许修改的字段；requireRoleInDepartment 校验目标角色的部门。用户角色关联由应用服务保存。'),
 ('Role','name','rename 更新名称。权限授予与撤销通过关联仓储执行，由联合主键维持唯一。重复授予通过角色行锁在事务中合并。'),
 ('Department / System / Permission','name','rename 改名；固定字段、对象存在性及引用约束由应用服务和存储协作。'),
 ('Data.User / Role / Department / System / Permission','编号、名称、归属及固定业务字段','共享不可变 record，用作查询结果、持久化契约与领域构造输入。')],'2,3,5')
 s+='''==== 共享持久化与适配器

ManagementStore 提供业务对象、直接授权与继承边的读写。EffectiveAccessStore 查询有效角色和有效权限，SessionStore 维护技术会话。三个适配器委托给 Spring Data JPA 仓储。新增通过 save 接收自增编号，更新由事务提交时同步受管实体的变化。用户删除依次清理会话、用户角色和账号。Hibernate 启动时校验 Flyway 维护的表结构。
'''
 s+=fig('class-persistence','共享持久化适配器 类图')
 for stem,title in [('adapter-insert','JPA 用户适配器 新建顺序图'),('adapter-update','JPA 用户适配器 更新顺序图'),('adapter-delete','JPA 用户关联清理与删除 顺序图')]:s+=fig(stem,title)
 s+='''==== 完整包图与文件组织

完整包图按 API、契约、业务实现与基础设施四列展开；同一真实 Java 包可按职责多处呈现。包内列出当前类名。入口与实现分图放大展示相关包依赖，源文件图列出 Java、资源、前端、部署及集成目录。
'''
 for mode,label in [('structured','结构化'),('ddd','DDD')]:
  s+=fig('packages-full-'+mode,'系统完整 '+label+' UML 包图')
  for part,caption in [('entry','入口与用例契约'),('implementation','服务与基础设施')]:s+=fig('packages-'+mode+'-'+part,label+' 包图 '+caption)
 s+=fig('packages-files','源文件与配置文件组织')
 s+='''==== 认证与授权关系

共享认证模块类图位于运行时鉴权功能下，覆盖 AuthController、AuthService、账号查询、有效授权、会话端口及 JWT 签发。用户角色和角色权限分别由 UserUseCase、RoleUseCase 提供方法，在两套业务服务中保持相同外部行为。
'''
 s+=fig('class-auth-request','Bearer 认证与实时鉴权 类图')
 for mode,label in [('structured','结构化'),('ddd','DDD')]:s+=fig('class-relations-'+mode,'授权关系 '+label+' 类图')
 s+='''==== 公共请求 拷贝与事务边界

BearerFilter 在进入 Controller 前检查签名、过期时间、会话绑定、账号及本系统 login 权限，CurrentIdentity 提供本次身份。Controller 按 @Valid、路径约束和字段白名单校验输入，使用 CloneFactory 将专用请求 record 转为 Data 输入契约。Service 继续核对具体操作许可及部门范围。
'''
 for stem,title in [('flow-preflight','受保护请求 校验顺序图'),('flow-copy','API 边界对象拷贝 顺序图'),('flow-adapter','持久化读取与数据映射 顺序图')]:s+=fig(stem,title)
 s+='''公开写服务方法使用 @Transactional。Controller 接收到服务结果时事务已完成；保存或提交失败时回滚。唯一及引用约束冲突映射为 409，数据库故障映射为 503，其他未预期错误映射为 500。普通查询和关联查询返回共享 Data，DDD 从 Data 构造领域对象并调用行为。

==== 对象拷贝与运行日志

老师的 CloneFactory 0.0.2 作为编译期注解处理器使用，Requests record 通过 @CopyTo 声明到 Data.CreateUser、Profile、Name 等六组目标类型。生成的 CloneFactory.copy 返回目标 record。Data.Profile 使用 @CopyNotNullFrom，资料更新通过 CloneFactory.copyNotNull 保留未提交字段。上游 master 40583ff 的工具版本仍为 0.0.2，已逐文件校验源码一致。JPA 将数据库记录映射为持久化实体，适配器将实体转换为 Data，领域对象由应用服务构造。
'''
 s+=table(['可执行层','日志内容','级别'],[
 ('API','方法、路由、响应结果、耗时；规则拒绝信息。','DEBUG / WARN'),('结构化服务','用例、操作者与目标编号，执行结果。','DEBUG / WARN'),('DDD 应用服务','用例编排、领域行为结果及事务异常。','DEBUG / WARN'),('DDD 领域对象','由应用服务记录行为结果，领域保持框架无关。','应用服务记录'),('Infrastructure','存储方法、结果、耗时及数据库异常。','DEBUG / ERROR')],'2,5,2')
 s+='\n各执行层通过 SLF4J 输出到 Logback 控制台与滚动文件；LayerLogging 覆盖 Controller、业务和存储方法。切面记录操作、耗时和异常类型。认证过滤器记录请求路径及状态。\n'
 return s
