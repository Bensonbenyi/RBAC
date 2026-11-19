"""服务调用与适配器交互分图；全部位置使用手工坐标。"""
from draw_complete import Sequence

# Entries are concrete calls; each call owns its execution specification.
READS={
 'USER-01':['result = users(actor.departmentId, page, size)'],
 'USER-02':['user = user(id)'],
 'USER-03':['result = createUser(actor.departmentId, command)'],
 'USER-04':['user = user(id)'],
 'USER-05':['user = user(id)'],
 'USER-06':['user = user(id)'],
 'USER-07':['user = user(userId)','role = role(roleId)'],
 'USER-08':['user = user(userId)','role = role(roleId)'],
 'ROLE-01':['result = roles(actor.departmentId, page, size)'],
 'ROLE-02':['role = role(id)'],
 'ROLE-03':['result = createRole(actor.departmentId, command.name)'],
 'ROLE-04':['role = role(id)'],
 'ROLE-05':['role = role(id)'],
 'ROLE-06':['role = role(id)'],
 'ROLE-07':['role = role(id)','permission = permission(permissionId)'],
 'ROLE-08':['role = role(id)','permission = permission(permissionId)'],
}
WRITES={
 'USER-04':'updateUser(id, profile)', 'USER-05':'deleteUser(id)',
 'USER-06':'result = userRoles(id)', 'USER-07':'assignRole(userId, roleId)',
 'USER-08':'revokeRole(userId, roleId)', 'ROLE-04':'renameRole(id, name)',
 'ROLE-05':'deleteRole(id)', 'ROLE-06':'result = rolePermissions(id)',
 'ROLE-07':'grantPermission(id, permissionId)', 'ROLE-08':'revokePermission(id, permissionId)'}
DOMAIN={
 'USER-04':'changeProfile(command)', 'USER-07':'assignRole(role, actor.departmentId)',
 'ROLE-04':'rename(command.name)',
 'ROLE-07':'grantPermission(permission)',
 'DEPT-03':'rename(command.name)', 'SYS-03':'rename(command.name)', 'PERM-03':'rename(command.name)'}

def service_sequence(op,mode):
 prefix='Structured' if mode=='structured' else 'Ddd'
 domain=mode=='ddd' and op.key in DOMAIN
 module=op.module;plural={'Department':'departments','System':'systems','Permission':'permissions'}.get(module)
 calls=list(READS.get(op.key,[]));write=WRITES.get(op.key)
 if plural:
  noun=module.lower()
  if op.function=='list':calls=['result = '+plural+'('+('systemId, ' if module=='Permission' else '')+'page, size)']
  elif op.function=='create':
   calls=({'Department':['[parentId 非空] department(command.parentId)'],'Permission':['system(systemId)']}.get(module,[]))+['result = create'+module+'('+('systemId, ' if module=='Permission' else '')+'command)']
  else:
   calls=[noun+' = '+noun+'(id)'];write=('rename'+module+'(id, name)' if op.function=='update' else 'delete'+module+'(id)')
 extras=[]
 if op.key=='USER-07':extras=['hasRole(userId, roleId)'] if not domain else ['assigned = userRoles(userId)']
 if domain and op.key=='ROLE-07':extras=['assigned = rolePermissions(id)']
 scoped=module in ['User','Role'] and op.function not in ['list','create']
 count=len(calls)+len(extras)+(1 if scoped else 0)+(2 if domain else 0)+(1 if write else 0)
 scope_height=(205 if op.key in ["USER-07","USER-08"] and not (domain and op.key=="USER-07") else 115) if scoped else 0
 final_y=575+115*(len(calls)+len(extras))+scope_height+(285 if domain else 0)+(115 if write else 0)
 height=max(1150,final_y+210)
 if domain:height=575+115*(len(calls)+len(extras))+scope_height+340
 parts=[('obj','Caller'),('service',prefix+module+'Service'),('access','AccessPolicy'),('store','ManagementStore')]
 # Domain changes are expanded in a separate interaction plate.
 # Domain objects are created after the queries, not at the top of the interaction.
 create_y=575+115*(len(calls)+len(extras))+scope_height
 seq=Sequence(op.key.lower()+'-'+mode,op.key+' '+('DDD' if mode=='ddd' else '结构化')+' '+op.title,parts,height=height,created=None)
 seq.msg(0,1,290,op.function+'(actor, 输入)',end=height-170)
 seq.msg(1,2,400,'require(actor, '+op.permission+')',end=445)
 seq.frame(460,80,'opt','无操作许可')
 seq.msg(2,0,515,'«exception» FORBIDDEN',reply=True)
 y=575
 for call in calls:
  seq.msg(1,3,y,call,end=y+45);y+=115
 if scoped:
  target='actor.departmentId' if op.function in ['list','create'] else ('user.departmentId；role.departmentId' if op.key in ['USER-07','USER-08'] else module.lower()+'.departmentId')
  if domain and op.key=='USER-07':seq.msg(1,2,y,'scope(actor, user.departmentId)',end=y+40)
  elif '；' in target:
   seq.msg(1,2,y,'scope(actor, user.departmentId)',end=y+35);y+=90
   seq.msg(1,2,y,'scope(actor, role.departmentId)',end=y+35)
  else:seq.msg(1,2,y,'scope(actor, '+target+')',end=y+40)
  y+=115
 for call in extras:seq.msg(1,3,y,call,end=y+40);y+=115
 if domain:
  import draw_spec as u
  sub=Sequence(op.key.lower()+'-ddd-domain',op.key+' DDD 聚合修改与保存',[('obj','Caller'),('service',prefix+module+'Service'),('entity',module),('store','ManagementStore')],height=1150,created={2:450})
  sub.msg(0,1,300,'继续 '+op.function+'(actor, 已读取数据)',end=945)
  sub.msg(1,2,450,'«create» '+module+'(当前数据)',create=True)
  sub.msg(1,2,650,DOMAIN[op.key],end=700)
  sub.frame(730,95,'opt','违反领域规则')
  sub.msg(2,0,795,'«exception» BusinessException',reply=True)
  sub.msg(1,3,890,write,end=935)
  sub.msg(1,0,980,'返回结果；事务结束',reply=True)
  sub.note(1050,'与前图属于同一事务；业务状态及集合由领域对象维护，保存失败时整体回滚。')
  y+=20
  seq.d.rect(35,y+35,830,95);seq.d.text(65,y+70,'ref',20,native=False)
  seq.d.text(450,y+105,'聚合修改与保存（见续图）',20,True,native=False)
  model=u.obj('UMLInteractionUse',seq.owner,name='聚合修改与保存',refersTo=u.ref(sub.owner))
  seq.owner['fragments'].append(model)
  seq.d.view('UMLInteractionUseView',model,left=35,top=y+35,width=830,height=95,name='聚合修改与保存')
  y+=160
 else:
  if write:seq.msg(1,3,y,write,end=y+45);y+=115
 seq.msg(1,0,y,'返回结果；事务结束',reply=True)
 seq.note(height-105,'@Transactional 覆盖服务。对象缺失为 404，部门越界为 403，重复或引用冲突为 409；异常经调用栈传播，写事务回滚。')

def selection_sequence(op):
 name={'Role':'roles','System':'systems','Permission':'permissions'}[op.module]
 seq=Sequence(op.key.lower()+'-shared',op.key+' 共享 '+op.title,[('obj','Caller'),('service','SelectionService'),('access','AccessPolicy'),('store','ManagementStore')],height=1150)
 seq.msg(0,1,300,name+'(actor, page, size)',end=940)
 seq.msg(1,2,430,'require / allowed(actor, 当前授权任务)',end=470)
 seq.frame(500,95,'opt','授权任务许可不足')
 seq.msg(2,0,565,'«exception» FORBIDDEN',reply=True)
 seq.msg(1,3,700,'result = '+name+'('+('actor.departmentId, ' if name=='roles' else 'systemId, ' if name=='permissions' else '')+'page, size)',end=760)
 seq.msg(1,0,920,'返回 Page；不授予目录写权限',reply=True)
 seq.note(1030,op.rule)

def ui_sequences():
 seq=Sequence('course-08','COURSE-08 getCurrentUser 支撑接口',[('obj','Caller'),('service','CurrentUserService'),('identity','UnifiedIdentityGateway'),('profile','CurrentUserProfileGateway')],height=1250)
 seq.msg(0,1,300,'getCurrentUser()',end=1030)
 seq.msg(1,2,440,'identity = currentIdentity()',end=490)
 seq.msg(1,3,620,'profile = findByUserId(identity.userId)',end=680)
 seq.frame(730,95,'opt','平台账号尚未关联本地教师或学生')
 seq.msg(1,0,795,'«exception» ForbiddenException',reply=True)
 seq.msg(1,0,1040,'CurrentUserResult（本地资料、角色、界面码）',reply=True)
 seq.note(1140,'入口沿用课程适配器的 Bearer、会话及 API 检查；两种 CurrentUserService 实现共用端口和本地资料网关。')
 seq=Sequence('flow-ui-api','界面码与 API 码分别判断',[('page','Browser'),('auth','AuthService'),('adapter','CourseAdapter'),('service','CourseService')],height=1300)
 seq.msg(0,1,300,'me(actor) -> permissions（仅界面码）',end=355)
 seq.msg(0,0,440,'依据 MCOR 设置审核按钮 disabled',end=490)
 seq.msg(0,2,600,'有界面码时提交审核请求',end=1090)
 seq.msg(2,1,710,'apiAllowed(actor, reviewCourse)',end=760)
 seq.frame(790,95,'opt','没有 CR 后端许可')
 seq.msg(2,0,855,'403 FORBIDDEN',reply=True)
 seq.msg(2,3,970,'许可通过后执行业务审核',end=1020)
 seq.msg(2,0,1120,'审核结果',reply=True)
 seq.note(1200,'界面与 API 码分别经角色授予。只有界面码时接口仍拒绝；只有 API 码时控件保持 disabled。')
