"""Current HTTP operations, shared by diagrams, specification and coverage checks."""
from dataclasses import dataclass
@dataclass(frozen=True)
class Operation:
    key:str
    module:str
    method:str
    route:str
    function:str
    title:str
    permission:str
    result:str
    rule:str
OPS=[
 Operation('AUTH-01','Auth','POST','/auth/login','login','登录','目标系统 login','200 LoginResult','按用户名和手机号认证，逐一校验所请求系统的 login，保存会话并签发 JWT。'),
 Operation('AUTH-02','Auth','GET','/auth/me','me','读取当前身份和界面权限','目标系统 login','200 CurrentUserVO','返回 subject、no、name、departmentId、roles 和目标系统的界面权限 permissions。no 来自平台用户名，对接课程时用作工号或学号。'),
 Operation('AUTH-03','Auth','POST','/auth/logout','logout','退出','有效登录','204','更新当前会话 revoked_at；原令牌随后返回 401。'),
 Operation('AUTH-05','Auth','GET','/auth/permissions/{permission}','check','检查界面权限','有效登录','200 allowed','系统标识取自 audience；仅检查已登记界面码；API 码在此接口返回 false。'),
 Operation('AUTH-06','Auth','GET','/auth/api-permissions/{operationId}','api','检查接入 API 权限','有效登录','200 ApiAuthority','返回 operationId 和 allowed。AuthService 将两系统 operationId 映射到 API 权限；未知操作返回 404 API_OPERATION_NOT_FOUND。'),
 Operation('AUTH-07','Auth','GET','/auth/users/{userId}','get','查询统一身份用户','user.view 或 TEC','200 IdentityUser','返回 userId、no、name。平台限定本部门，课程端要求 TEC，不存在返回 IDENTITY_USER_NOT_FOUND。'),
 Operation('AUTH-08','Auth','GET','/auth/departments','directoryDepartments','查询统一身份部门','department.manage 或 TEC','200 IdentityDepartmentPage','按 parentId 逐层展开，按名称或编码搜索，并返回 hasChildren。'),
 Operation('AUTH-09','Auth','GET','/auth/departments/{departmentId}/users','directoryUsers','查询部门下的用户','user.view 或 TEC','200 IdentityDepartmentUserPage','按姓名或账号分页查询部门直接成员。平台限定本部门，响应仅包含身份字段。'),
 Operation('USER-01','User','GET','/api/users','list','查询本部门用户','user.view','200 Page<User>','分页并限定本部门直接成员。'),
 Operation('USER-06','User','GET','/api/users/{id}/roles','roles','查询用户角色','user.role.assign','200 List<Role>','校验目标用户的部门范围。'),
 Operation('USER-07','User','POST','/api/users/{id}/roles','assign','分配用户角色','user.role.assign','201','校验操作者、目标用户和角色同部门；重复分配返回 409。'),
 Operation('USER-08','User','DELETE','/api/users/{id}/roles/{roleId}','revoke','撤销用户角色','user.role.assign','204','校验用户及角色归属，重复撤销保持 204。'),
 Operation('ROLE-01','Role','GET','/api/roles','list','查询本部门角色','role.manage','200 Page<Role>','分页并限定本部门。'),
 Operation('ROLE-03','Role','POST','/api/roles','create','新建角色','role.manage','201 Role','角色部门取自操作者；名称在部门内唯一。'),
 Operation('ROLE-04','Role','PATCH','/api/roles/{id}','update','重命名角色','role.manage','204','部门固定，仅修改名称。'),
 Operation('ROLE-05','Role','DELETE','/api/roles/{id}','delete','删除角色','role.manage','204','存在用户角色或角色权限引用时返回 409，先处理关联。'),
 Operation('ROLE-06','Role','GET','/api/roles/{id}/permissions','permissions','查询角色权限','role.manage','200 List<Permission>','角色限定本部门；权限可来自两个接入系统。'),
 Operation('ROLE-07','Role','POST','/api/roles/{id}/permissions','grant','授予角色权限','role.manage','201','通过 permissionId 新建直接授权；重复提交返回 409。'),
 Operation('ROLE-08','Role','DELETE','/api/roles/{id}/permissions/{permissionId}','revoke','撤销角色权限','role.manage','204','同事务移除关联，提交后新鉴权请求读取新权限。'),
]
OPS.extend([
 Operation('DEPT-01','Department','GET','/api/departments','list','查询部门','department.manage','200 Page','有界分页读取部门。'),
 Operation('SYS-01','System','GET','/api/systems','list','查询业务系统','system.manage','200 Page','有界分页读取系统目录。'),
 Operation('PERM-01','Permission','GET','/api/systems/{systemId}/permissions','list','查询权限','permission.manage','200 Page','按所属系统分页读取权限。'),
])
OPS.extend([
 Operation('ROLE-09','Role','GET','/api/roles/options','options','查询可分配角色候选','user.role.assign','200 Page<Role>','仅返回本部门角色；不要求 role.manage，分页上限 100。'),
 Operation('SYS-05','System','GET','/api/systems/options','options','查询授权系统候选','role.manage 或 permission.manage','200 Page<System>','仅开放授权任务所需的系统只读目录，按授权任务放行。'),
 Operation('PERM-05','Permission','GET','/api/permissions/options','options','查询可授予权限候选','role.manage','200 Page<Permission>','查询参数 systemId；返回该系统权限，分页上限 100。')])
OPS.extend([
 Operation('ROLE-10','Role','GET','/api/roles/{id}/inherited-roles','inheritedRoles','查询直接基础角色','role.manage','200 List<Role>','同部门范围；返回直接继承边，不混入用户直接分配关系。'),
 Operation('ROLE-11','Role','POST','/api/roles/{id}/inherited-roles','inherit','添加角色继承','role.manage','201','从高级角色指向 juniorId；同部门且无环。组合主键拒绝重复继承。'),
 Operation('ROLE-12','Role','DELETE','/api/roles/{id}/inherited-roles/{juniorId}','uninherit','解除角色继承','role.manage','204','只解除指定直接边，其他路径继续有效；下次请求实时计算权限，重复解除幂等。'),
 Operation('ROLE-13','Role','GET','/api/roles/{id}/effective-permissions','effectivePermissions','查询角色有效权限','role.manage','200 List<Permission>','返回直接授权与全部可达基础角色权限的去重并集，权限保留系统编号。')])
MODULES={'User':('用户管理','REQ-F-02','app_user、user_role、login_session'),'Role':('角色授权','REQ-F-03','role、role_permission、role_inheritance'),'Department':('部门目录','REQ-F-04','department'),'System':('系统目录','REQ-F-05','business_system'),'Permission':('权限目录','REQ-F-06','permission'),'Auth':('认证与鉴权','REQ-F-01','app_user、login_session、两组授权关联')}
REQS=[('REQ-F-01','登录、退出、身份及当前权限'),('REQ-F-02','本部门用户查询和用户角色分配'),('REQ-F-03','本部门角色维护和角色权限配置'),('REQ-F-04','多级部门目录查询'),('REQ-F-05','两个业务系统目录'),('REQ-F-06','各系统权限目录查询'),('REQ-F-07','课程师生登录、我的课程、教师目录与管理操作'),('REQ-N-01','安全与部门隔离'),('REQ-N-02','性能可测量与有界查询'),('REQ-N-03','应用可用性与手动数据恢复'),('REQ-N-04','双实现可切换与契约一致性'),('REQ-N-05','容器部署与可重复构建')]
