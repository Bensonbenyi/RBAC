"""Executable design inventory. Every concrete HTTP endpoint has a diagram mapping."""
from dataclasses import dataclass
@dataclass(frozen=True)
class Module:
 key:str; title:str; controller:str; port:str; store:str; domain:str; permission:str; scope:str; refs:str
MODULES=[
 Module('user','用户','UserController','UserUseCase','UserStore','User','user.*','本部门直接成员','user_role'),
 Module('department','部门','DepartmentController','DepartmentUseCase','DepartmentStore','Department','department.manage','部门树','子部门、用户、角色'),
 Module('system','业务系统','SystemController','SystemUseCase','SystemStore','BusinessSystem','system.manage','全局系统目录','permission'),
 Module('permission','权限','PermissionController','PermissionUseCase','PermissionStore','Permission','permission.manage','指定系统权限目录','role_permission'),
 Module('role','角色','RoleController','RoleUseCase','RoleStore','Role','role.manage','本部门角色','user_role、role_permission'),
]
# slug, title, module, action, concrete endpoints, business rules
WRITES=[]
for m in MODULES:
 base={'user':'/users','department':'/departments','system':'/systems','permission':'/systems/{id}/permissions','role':'/roles'}[m.key]
 item={'user':'/users/{id}','department':'/departments/{id}','system':'/systems/{id}','permission':'/permissions/{id}','role':'/roles/{id}'}[m.key]
 checks={
 'user':('部门由操作者确定；用户名全局唯一','仅修改 name、phone；校验本部门范围','同事务删除 user_role 与用户'),
 'department':('父部门存在或为空；创建新节点','仅修改 name；parentId 固定','有子部门、用户或角色时拒绝删除'),
 'system':('systemKey 全局唯一','仅修改 name；systemKey 固定','系统仍有权限定义时拒绝删除'),
 'permission':('路径系统存在；code 在系统内唯一','仅修改 name；systemId、code 固定','被角色引用时拒绝删除'),
 'role':('部门取操作者部门；名称部门内唯一','校验本部门；仅修改 name','有用户或权限关联时拒绝删除'),
 }[m.key]
 for action,verb,path,rule in [('create','POST',base,checks[0]),('update','PATCH',item,checks[1]),('delete','DELETE',item,checks[2])]:
  WRITES.append(dict(slug=f'{m.key}-{action}',title={'create':'新建','update':'修改','delete':'删除'}[action]+m.title,module=m.key,action=action,endpoints=[f'{verb} /api{path}'],rule=rule))
WRITES += [
 dict(slug='self-update',title='修改本人资料',module='user',action='updateMe',endpoints=['PATCH /api/auth/me'],rule='目标编号从 Session 取得；仅修改 name、phone'),
 dict(slug='user-assign',title='分配用户角色',module='user',action='assign',endpoints=['POST /api/users/{id}/roles'],rule='操作者、目标用户、角色同一非空部门；重复分配返回 409'),
 dict(slug='user-revoke',title='撤销用户角色',module='user',action='revoke',endpoints=['DELETE /api/users/{id}/roles/{roleId}'],rule='操作者、目标用户、角色同部门；关联已撤销时返回 204'),
 dict(slug='role-grant',title='授予角色权限',module='role',action='grant',endpoints=['PUT /api/roles/{id}/permissions/{permissionId}'],rule='本部门角色；权限存在；已授予时幂等返回 204'),
 dict(slug='role-revoke',title='撤销角色权限',module='role',action='revokePermission',endpoints=['DELETE /api/roles/{id}/permissions/{permissionId}'],rule='本部门角色；权限存在；已撤销时幂等返回 204'),
]
READS=[
 ('query-user-detail','用户详情','user',['GET /api/users/{id}'],'get','目标用户存在且属于本部门，否则 404 或 403'),
 ('query-role-detail','角色详情','role',['GET /api/roles/{id}'],'get','目标角色存在且属于本部门，否则 404 或 403'),
 ('query-user','用户列表与详情','user',['GET /api/users'],'list','列表强制按操作者部门过滤；详情校验同部门'),
 ('query-department','部门树查询','department',['GET /api/departments'],'list','读取节点后按 parentId 组织为树'),
 ('query-system','业务系统查询','system',['GET /api/systems'],'list','分页读取系统目录'),
 ('query-permission','系统权限查询','permission',['GET /api/systems/{id}/permissions'],'list','先校验系统存在，再按 systemId 分页'),
 ('query-role','角色列表与详情','role',['GET /api/roles'],'list','列表与详情限定本部门'),
 ('query-user-roles','用户已分配角色查询','user',['GET /api/users/{id}/roles'],'roles','先校验用户存在与同部门，再读取角色'),
 ('query-role-permissions','角色已有权限查询','role',['GET /api/roles/{id}/permissions'],'permissions','先校验角色存在与同部门，再读取权限'),
]
AUTH=[
 ('csrf','取得 CSRF 凭据',['GET /api/auth/csrf']),
 ('login','账号与登录资格校验',['POST /api/auth/login']),
 ('me','查看当前用户',['GET /api/auth/me']),
 ('logout','退出当前会话',['POST /api/auth/logout']),
 ('authorize','当前权限判断',['GET /api/auth/permissions/{code}']),
]
CASE_GROUPS={'AUTH-001':['csrf','login','session'],'AUTH-002':['me','logout'],'USER-001':['query-user','query-user-detail','user-create','user-update','user-delete','self-update'],'DEPT-001':['query-department','department-create','department-update','department-delete'],'SYS-001':['query-system','system-create','system-update','system-delete'],'PERM-001':['query-permission','permission-create','permission-update','permission-delete'],'ROLE-001':['query-role','query-role-detail','role-create','role-update','role-delete','query-role-permissions','role-grant','role-revoke'],'GRANT-001':['query-user-roles','user-assign'],'GRANT-002':['user-revoke'],'AUTHZ-001':['authorize']}
def figures(slug):
 return [f'flow-{slug}-{mode}' for mode in ['structured','ddd']] if any(w['slug']==slug for w in WRITES) else [f'flow-{slug}']
def entries():
 for w in WRITES:yield w['slug'],w['title'],w['endpoints'],figures(w['slug'])
 for slug,title,_,ep,_,_ in READS:yield slug,title,ep,figures(slug)
 for slug,title,ep in AUTH:yield slug,title,ep,figures(slug)
