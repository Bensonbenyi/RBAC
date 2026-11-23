"""服务调用与适配器交互分图；全部位置使用手工坐标。"""
from draw_complete import Sequence

# Entries are concrete calls; each call owns its execution specification.
READS={
 'USER-01':['result = users(actor.departmentId, page, size)'],
 'USER-06':['user = user(id)'],
 'USER-07':['user = user(userId)','role = role(roleId)'],
 'USER-08':['user = user(userId)','role = role(roleId)'],
 'ROLE-01':['result = roles(actor.departmentId, page, size)'],
 'ROLE-03':['result = createRole(actor.departmentId, command.name)'],
 'ROLE-04':['role = role(id)'],
 'ROLE-05':['role = role(id)'],
 'ROLE-06':['role = role(id)'],
 'ROLE-07':['role = role(id)','permission = permission(permissionId)'],
 'ROLE-08':['role = role(id)','permission = permission(permissionId)'],
}
WRITES={
 'USER-06':'result = userRoles(id)', 'USER-07':'assignRole(userId, roleId)',
 'USER-08':'revokeRole(userId, roleId)', 'ROLE-04':'renameRole(id, name)',
 'ROLE-05':'deleteRole(id)', 'ROLE-06':'result = rolePermissions(id)',
 'ROLE-07':'grantPermission(id, permissionId)', 'ROLE-08':'revokePermission(id, permissionId)'}
DOMAIN={'USER-07':'requireRoleInDepartment(role)'}

def service_sequence(op,mode):
 prefix={'structured':'Structured','ddd':'Ddd','shared':''}[mode]
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
 scoped=module in ['User','Role'] and op.function not in ['list','create']
 scope_calls=[]
 if scoped:
  if op.key in ['USER-07','USER-08']:
   scope_calls=['scope(actor, user.departmentId)']
   if not domain:scope_calls.append('scope(actor, role.departmentId)')
  else:
   scope_calls=['scope(actor, '+module.lower()+'.departmentId)']
 y=410
 final_y=y+67*(len(calls)+len(scope_calls)+int(bool(write) and not domain))
 if domain:final_y+=107
 height=final_y+128
 parts=[('obj','Caller'),('service',prefix+module+'Service'),('access','AccessPolicy'),('store','ManagementStore')]
 seq=Sequence(op.key.lower()+'-'+mode,op.key+' '+{'ddd':'DDD','structured':'结构化','shared':'共享'}[mode]+' '+op.title,parts,height=height)
 seq.msg(0,1,192,op.function+'(actor, 输入)',end=final_y)
 seq.msg(1,2,255,'require(actor, '+op.permission+')',end=286)
 seq.frame(294,80,'break','无操作许可')
 seq.msg(2,0,359,'403 FORBIDDEN',reply=True)
 for call in calls:
  seq.msg(1,3,y,call,end=y+32);y+=67
 for call in scope_calls:
  seq.msg(1,2,y,call,end=y+32);y+=67
 if domain:
  import draw_spec as u
  sub=Sequence(op.key.lower()+'-ddd-domain',op.key+' DDD 聚合修改与保存',[('obj','Caller'),('service',prefix+module+'Service'),('entity',module),('store','ManagementStore')],height=777,created={2:288})
  sub.msg(0,1,195,'继续 '+op.function+'(actor, 已读取数据)',end=660)
  sub.msg(1,2,288,'«create» '+module+'(当前数据)',create=True)
  sub.msg(1,2,430,DOMAIN[op.key],end=460)
  sub.frame(477,80,'break','违反领域规则')
  sub.msg(2,0,542,'«exception» BusinessException',reply=True)
  sub.msg(1,3,592,write,end=626)
  sub.msg(1,0,660,'返回结果；事务结束',reply=True)
  sub.note(708,'与前图属于同一事务；领域对象校验角色归属，保存失败时整体回滚。')
  seq.d.rect(35,y+35,830,64);seq.d.text(65,y+60,'ref',20,native=False)
  seq.d.text(450,y+78,'聚合修改与保存（见续图）',20,True,native=False)
  model=u.obj('UMLInteractionUse',seq.owner,name='聚合修改与保存',refersTo=u.ref(sub.owner))
  seq.owner['fragments'].append(model)
  seq.d.view('UMLInteractionUseView',model,left=35,top=y+35,width=830,height=64,name='聚合修改与保存')
 elif write:
  seq.msg(1,3,y,write,end=y+35)
 seq.msg(1,0,final_y,'返回结果；事务结束',reply=True)
 seq.note(final_y+46,'对象缺失为 404，部门越界为 403，重复或引用冲突为 409；写事务随异常回滚。')

def selection_sequence(op):
 name={'Role':'roles','System':'systems','Permission':'permissions'}[op.module]
 seq=Sequence(op.key.lower()+'-shared',op.key+' 共享 '+op.title,[('obj','Caller'),('service','SelectionService'),('access','AccessPolicy'),('store','ManagementStore')],height=716)
 query = 'systemId, page, size' if name == 'permissions' else 'page, size'
 seq.msg(0,1,195,name+'(actor, '+query+')',end=590)
 seq.msg(1,2,280,'require / allowed(actor, 当前授权任务)',end=315)
 seq.frame(331,80,'break','授权任务许可不足')
 seq.msg(2,0,396,'403 FORBIDDEN',reply=True)
 seq.msg(1,3,470,'result = '+name+'('+('actor.departmentId, ' if name=='roles' else 'systemId, ' if name=='permissions' else '')+'page, size)',end=510)
 seq.msg(1,0,590,'返回 Page；不授予目录写权限',reply=True)
 seq.note(636,op.rule)

def ui_sequences():
 seq=Sequence('course-08','COURSE-08 getCurrentUser 支撑接口',[('obj','Caller'),('service','CurrentUserService'),('identity','UnifiedIdentityGateway'),('profile','CurrentUserProfileGateway')],height=1250)
 seq.msg(0,1,300,'getCurrentUser()',end=1030)
 seq.msg(1,2,440,'读取当前请求中的已验证身份',end=490)
 seq.msg(1,3,620,'profile = findByUserId(identity.userId)',end=680)
 seq.frame(730,95,'opt','平台账号尚未关联本地教师或学生')
 seq.msg(1,0,795,'«exception» ForbiddenException',reply=True)
 seq.msg(1,0,1040,'CurrentUserResult（本地资料、角色、界面码）',reply=True)
 seq.note(1140,'课程过滤器先向平台查本人身份，业务服务在同一请求中复用结果，随后读取课程库资料。')
 seq=Sequence('course-09','COURSE-09 课程登录',[
  ('web','CourseWeb'),('api','LoginController'),('svc','LoginUseCase'),
  ('db','CourseRepository'),('auth','UnifiedIdentityClient')],height=1230)
 seq.msg(0,1,285,'POST /course-api/login',end=1050)
 seq.msg(1,2,390,'login(role, account, password)',end=995)
 seq.msg(2,3,490,'按工号或学号查询本地资料',end=540)
 seq.msg(2,4,620,'POST /auth/login',end=675)
 seq.msg(2,4,750,'GET /auth/me',end=805)
 seq.msg(2,2,885,'核对登录角色并绑定 userId',end=930)
 seq.msg(2,1,1000,'LoginResult',reply=True)
 seq.msg(1,0,1060,'200 accessToken  currentUser',reply=True)
 seq.note(1140,'工号与学号沿用平台用户名。课程库用 userId 关联平台主键，账号角色与入口一致后进入工作台。')
 seq=Sequence('course-10','COURSE-10 课程网页跨域读取身份目录',[
  ('web','CourseWeb'),('cors','CorsFilter'),('security','BearerFilter'),('identity','Identity API')],height=861)
 seq.msg(0,1,200,'OPTIONS /auth/...',end=257)
 seq.msg(1,0,263,'200 允许已配置来源与 Authorization',reply=True)
 seq.msg(0,1,340,'GET /auth/...  Authorization: Bearer',end=700)
 seq.msg(1,2,408,'通过链路 检查当前令牌和系统登录许可',end=655)
 seq.msg(2,3,480,'查询 UI 码或校验 TEC 并查询目录',end=585)
 seq.msg(3,2,590,'allowed 或部门及候选用户分页',reply=True)
 seq.msg(1,0,700,'HTTP JSON 与允许来源响应头',reply=True)
 seq.note(764,'前端直连权限平台。预检校验来源和请求头，正式请求按当前 RBAC 授权处理。')
 seq=Sequence('course-11','COURSE-11 学生查看我的课程',[
  ('web','CourseWeb'),('filter','RbacIntegrationFilter'),('controller','StudentCourseController'),
  ('usecase','StudentCourseUseCase'),('db','课程库')],height=867)
 seq.msg(0,1,208,'GET /course-api/me/courses',end=713)
 seq.msg(1,1,273,'向统一身份查询 listStudentCourses 许可',end=316)
 seq.msg(1,2,370,'通过登录许可后继续请求',end=672)
 seq.msg(2,3,436,'listStudentCourses(termId)',end=622)
 seq.msg(3,4,502,'按 userId 读取学生及选课关系',end=566)
 seq.msg(4,3,576,'已选且开放的课程',reply=True)
 seq.msg(3,2,628,'StudentCoursePageResult',reply=True)
 seq.msg(2,0,715,'200 StudentCoursePageVO',reply=True)
 seq.note(771,'学生页面按登录身份和学期显示课程。以同一学号在教师入口登录时，课程名册校验拒绝。')
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
