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
 Operation('AUTH-01','Auth','POST','/auth/login','login','登录','目标系统 login','200 LoginResult','按用户名和手机号认证，校验单个系统 login，保存会话并签发 JWT。'),
 Operation('AUTH-02','Auth','GET','/auth/me','me','读取本人资料和权限','有效登录','200 Me','返回 subject、姓名、用户名、部门及当前系统权限；权限实时查询。'),
 Operation('AUTH-03','Auth','POST','/auth/logout','logout','退出','有效登录','204','更新当前会话 revoked_at；原令牌随后返回 401。'),
 Operation('AUTH-04','Auth','PATCH','/auth/me','updateMe','修改本人资料','有效登录','204','本人编号取自认证上下文，仅接收姓名和手机号。'),
 Operation('AUTH-05','Auth','GET','/auth/permissions/{permission}','check','检查操作权限','有效登录','200 allowed','系统标识取自 audience；按用户角色权限并集判断。'),
 Operation('AUTH-06','Auth','GET','/auth/api-permissions/{operationId}','api','检查接入 API 权限','有效登录','200 allowed','课程 operationId 在适配层映射到老师的权限码；未映射返回 false。'),
 Operation('AUTH-07','Auth','GET','/identity/users/{id}','get','查询统一身份','本人或 UL','200 IdentityUser','返回 userId、no、name；no 使用 username，查询他人要求当前系统 UL。'),
 Operation('USER-01','User','GET','/api/users','list','查询本部门用户','user.view','200 Page<User>','分页并限定本部门直接成员。'),
 Operation('USER-02','User','GET','/api/users/{id}','get','查询用户详情','user.view','200 User','校验对象部门等于操作者部门。'),
 Operation('USER-03','User','POST','/api/users','create','新建用户','user.create','201 User','部门取自操作者；用户名全局唯一，编号由数据库生成。'),
 Operation('USER-04','User','PATCH','/api/users/{id}','update','修改用户资料','user.edit','204','仅更新出现的姓名和手机号，保留未提交字段。'),
 Operation('USER-05','User','DELETE','/api/users/{id}','delete','删除用户','user.delete','204','同事务删除其登录会话、用户角色关联及用户。'),
 Operation('USER-06','User','GET','/api/users/{id}/roles','roles','查询用户角色','user.role.assign','200 List<Role>','校验目标用户的部门范围。'),
 Operation('USER-07','User','POST','/api/users/{id}/roles','assign','分配用户角色','user.role.assign','201','校验操作者、目标用户和角色同部门；重复分配返回 409。'),
 Operation('USER-08','User','DELETE','/api/users/{id}/roles/{roleId}','revoke','撤销用户角色','user.role.assign','204','校验用户及角色归属，重复撤销保持 204。'),
 Operation('ROLE-01','Role','GET','/api/roles','list','查询本部门角色','role.manage','200 Page<Role>','分页并限定本部门。'),
 Operation('ROLE-02','Role','GET','/api/roles/{id}','get','查询角色详情','role.manage','200 Role','校验角色属于本部门。'),
 Operation('ROLE-03','Role','POST','/api/roles','create','新建角色','role.manage','201 Role','角色部门取自操作者；名称在部门内唯一。'),
 Operation('ROLE-04','Role','PATCH','/api/roles/{id}','update','重命名角色','role.manage','204','部门固定，仅修改名称。'),
 Operation('ROLE-05','Role','DELETE','/api/roles/{id}','delete','删除角色','role.manage','204','存在用户角色或角色权限引用时返回 409，先处理关联。'),
 Operation('ROLE-06','Role','GET','/api/roles/{id}/permissions','permissions','查询角色权限','role.manage','200 List<Permission>','角色限定本部门；权限可来自两个接入系统。'),
 Operation('ROLE-07','Role','PUT','/api/roles/{id}/permissions/{permissionId}','grant','授予角色权限','role.manage','204','校验角色和权限存在，重复授予保持 204。'),
 Operation('ROLE-08','Role','DELETE','/api/roles/{id}/permissions/{permissionId}','revoke','撤销角色权限','role.manage','204','同事务移除关联，提交后新鉴权请求读取新权限。'),
]
for module,route,permission,label in [('Department','departments','department.manage','部门'),('System','systems','system.manage','业务系统'),('Permission','permissions','permission.manage','权限')]:
    prefix={'Department':'DEPT','System':'SYS','Permission':'PERM'}[module]
    collection='/api/systems/{systemId}/permissions' if module=='Permission' else '/api/'+route
    for number,method,path,function,title,result,rule in [
      (1,'GET',collection,'list','查询'+label,'200 Page','有界分页；权限按所属系统查询。' if module=='Permission' else '有界分页读取目录。'),
      (2,'POST',collection,'create','新建'+label,'201','校验所属对象存在及唯一约束；业务系统标识固定。'),
      (3,'PATCH','/api/'+route+'/{id}','update','重命名'+label,'204','仅修改名称，父部门、系统标识和权限编码固定。'),
      (4,'DELETE','/api/'+route+'/{id}','delete','删除'+label,'204','有引用时返回 409；不存在返回 404。')]:
        OPS.append(Operation(f'{prefix}-{number:02}',module,method,path,function,title,permission,result,rule))
MODULES={'User':('用户管理','REQ-F-02','app_user、user_role、login_session'),'Role':('角色授权','REQ-F-03','role、role_permission'),'Department':('部门目录','REQ-F-04','department'),'System':('系统目录','REQ-F-05','business_system'),'Permission':('权限目录','REQ-F-06','permission'),'Auth':('认证与鉴权','REQ-F-01','app_user、login_session、两组授权关联')}
REQS=[('REQ-F-01','登录、退出、身份及当前权限'),('REQ-F-02','本部门用户维护和用户角色分配'),('REQ-F-03','本部门角色维护和角色权限配置'),('REQ-F-04','多级部门目录维护'),('REQ-F-05','两个业务系统登记'),('REQ-F-06','各系统权限目录维护'),('REQ-F-07','课程系统学期维护与课程审核接入'),('REQ-N-01','安全与部门隔离'),('REQ-N-02','性能可测量与有界查询'),('REQ-N-03','应用恢复与数据可恢复性'),('REQ-N-04','双实现可切换与契约一致性'),('REQ-N-05','容器部署与可重复构建')]
