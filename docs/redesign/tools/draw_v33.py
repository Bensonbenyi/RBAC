"""Explicitly placed black-and-white figures for the executable v3.3 baseline."""
from pathlib import Path
import json
import draw_spec as u
from draw_complete import Sequence
from v33_catalog import OPS,MODULES
R=Path(__file__).resolve().parents[1]
D=u.D

def box(d,x,y,w,h,title,body=()):
 d.rect(x,y,w,h);d.text(x+w/2,y+31,title,22,True,True)
 for i,s in enumerate(body):d.text(x+w/2,y+63+28*i,s,18,True)
 d.view('RectangleView',left=x,top=y,width=w,height=h)
def architecture():
 d=D('architecture','系统总体分层架构',900,1120)
 box(d,80,100,740,100,'浏览器展现层',['Vue 3  TypeScript   权限后台 / 课程演示入口'])
 box(d,80,240,740,120,'API 输入层',['Controller 输入校验与 CloneFactory','Bearer 认证、异常映射与请求日志'])
 box(d,80,400,740,90,'Service Port',['五个用例端口  数据契约  管理、授权及会话端口'])
 box(d,80,530,345,160,'结构化业务实现',['事务脚本 Service','校验、编排、调用存储'])
 box(d,475,530,345,160,'DDD 业务实现',['应用服务','领域实体与业务规则'])
 box(d,80,730,740,120,'共享 Infrastructure',['Spring Data JPA  MySQL   JWT 签名与校验','存储适配器  Logback 分层日志'])
 box(d,80,890,740,90,'MySQL 8.4',['八张业务表和一张技术会话表'])
 for a,b in [(200,240),(360,400),(850,890)]:d.line([(450,a+5),(450,b-10)],'dep')
 for x in [252,647]:d.line([(x,495),(x,520)],'dep');d.line([(x,695),(x,720)],'dep')
 d.note(80,1010,740,['app.backend-strategy 在启动时选择一套业务实现。','认证、存储及外部契约共用。'])
 d=D('packages','系统 UML 包图与依赖',900,1120,'UMLPackageDiagram')
 nodes={}
 for key,title,x,y,w,h,body in [('api','api',80,120,740,130,['auth  user  role  department  system  permission  identity']),('port','service.port',80,300,740,130,['五个 UseCase  Data  三个存储端口']),('structured','service.structured',80,500,345,150,['五个 Structured Service']),('ddd','service.ddd',475,500,345,150,['应用服务  domain']),('infra','infrastructure',80,770,740,140,['persistence  security  logging  bootstrap'])]:nodes[key]=d.node(title,x,y,w,h,'UMLPackage',body=body)
 d.edge(nodes['api'],nodes['port'],[(450,250),(450,290)])
 d.edge(nodes['structured'],nodes['port'],[(250,500),(250,440)])
 d.edge(nodes['ddd'],nodes['port'],[(650,500),(650,440)])
 d.edge(nodes['infra'],nodes['port'],[(825,825),(860,825),(860,365),(825,365)],real=False)
 d.note(80,960,740,['业务实现依赖存储端口；Infrastructure 实现该端口。','API 通过共享用例端口调用选中的业务实现。'])

def domain_relations():
 d=D('domain-relations','核心业务对象关系模型',900,1100)
 items={}
 for name,x,y,ops in [('Department',40,120,[('rename','')]),('User',520,120,[('changeProfile',''),('requireRoleInDepartment','')]),('Role',40,510,[('rename','')]),('Permission',520,510,[('rename','')]),('System',520,850,[('rename','')])]:items[name]=d.node(name,x,y,340,170 if name in ['User','Role'] else 140,ops=ops)
 def association(a,b,pts,ma,mb,label,labels):
  m=d.model('UMLAssociation',name=label)
  m['end1']=u.obj('UMLAssociationEnd',m,reference=u.ref(items[a][0]),multiplicity=ma,aggregation='none',navigable='unspecified')
  m['end2']=u.obj('UMLAssociationEnd',m,reference=u.ref(items[b][0]),multiplicity=mb,aggregation='none',navigable='unspecified')
  d.line(pts);d.view('UMLAssociationView',m,tail=u.ref(items[a][1]),head=u.ref(items[b][1]),points=';'.join(f'{x}:{y}' for x,y in pts))
  for x,y,text in labels:d.text(x,y,text,18)
 association('Department','User',[(385,190),(515,190)],'1','0..*','属于',[(395,172,'1'),(468,172,'0..*')])
 association('Department','Role',[(130,265),(130,505)],'1','0..*','设有',[(145,295,'1'),(145,490,'0..*')])
 association('User','Role',[(690,295),(690,385),(300,385),(300,505)],'0..*','0..*','分配',[(705,325,'0..*'),(315,490,'0..*')])
 association('Role','Permission',[(385,585),(515,585)],'0..*','0..*','授权',[(390,567,'0..*'),(468,567,'0..*')])
 association('Permission','System',[(690,655),(690,845)],'0..*','1','所属系统',[(705,690,'0..*'),(705,825,'1')])
 association('Role','Role',[(40,600),(15,600),(15,820),(280,820),(280,680)],'0..*','0..*','继承',[(22,715,'0..* 高级'),(285,755,'0..* 基础')])
 d.note(40,870,380,['User 和 Role 为聚合根。','跨聚合通过标识关联。','图中展示领域关系。'])

def core_classes():
 # Coarse design model: boundaries, control, entities and interfaces.
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  for name in ['UserController','UserUseCase','ManagementStore','User','Role']:u.CLASSES.pop(name,None)
  d=D('core-'+mode,('结构化' if mode=='structured' else 'DDD')+' 核心设计类图',900,1180)
  specs=[('UserController',[],[('assign','user: Identifier;role: Identifier','void')],'boundary'),('UserUseCase',[],[('assign','actor: Identity;user: Identifier;role: Identifier','void')],''),(prefix+'UserService',[],[('assign','actor: Identity;user: Identifier;role: Identifier','void')],'control'),('ManagementStore',[],[('user','id: Identifier','UserData'),('assignRole','user: Identifier;role: Identifier','void')],''),('User',[('username','String'),('name','String'),('phone','String')],[('changeProfile','name: String;phone: String','void'),('requireRoleInDepartment','role: Data.Role','void')] if mode=='ddd' else [],'entity'),('Role',[('name','String')],[('rename','name: String','void')] if mode=='ddd' else [],'entity')]
  nodes={}
  # Short diagram method signatures keep prose at subsystem level elsewhere.
  for name,attrs,methods,stereo in specs:
   u.classifier(name,methods,attrs,interface=name in ['UserUseCase','ManagementStore'],stereo=stereo)
  for name,x,y,w in [('UserController',35,110,830),('UserUseCase',35,300,830),(prefix+'UserService',35,500,830),('ManagementStore',35,700,830),('User',35,950,390),('Role',475,950,390)]:
   nodes[name]=u.klass(d,name,x,y,w)
  u.assoc(d,nodes['UserController'],nodes['UserUseCase'],[(450,220),(450,290)],role='service')
  d.edge(nodes[prefix+'UserService'],nodes['UserUseCase'],[(450,495),(450,430)],real=True)
  u.assoc(d,nodes[prefix+'UserService'],nodes['ManagementStore'],[(450,605),(450,690)],role='store')
  if mode=='ddd':
   d.edge(nodes[prefix+'UserService'],nodes['User'],[(30,560),(15,560),(15,930),(225,930),(225,945)])
  else:d.edge(nodes['ManagementStore'],nodes['User'],[(225,860),(225,945)])
  u.assoc(d,nodes['User'],nodes['Role'],[(430,1000),(470,1000)],role='',mult='0..*')
  d.text(433,980,'*',18);d.text(456,980,'*',18)

def sequence_for(op,mode):
 if op.function in ['inheritedRoles','inherit','uninherit','effectivePermissions']:
  from v35_diagrams import hierarchy_sequence
  return hierarchy_sequence(op,mode)
 if op.function=="options":
  from v34_sequences import selection_sequence
  return selection_sequence(op)
 if op.module!="Auth":
  from v34_sequences import service_sequence
  return service_sequence(op,mode)
 if op.key in {'AUTH-07','AUTH-08','AUTH-09'}:
  return identity_directory_sequence(op)
 if op.key=='AUTH-04':
  return profile_sequence(op,mode)
 return auth_sequence(op)

def profile_sequence(op,mode):
 stem=op.key.lower()+'-'+mode
 if mode=='ddd':
  parts=[('api','AuthController'),('app','DddUserService'),('domain','User'),('store','ManagementStore')]
  s=Sequence(stem,f'{op.key} DDD {op.title}',parts,height=1280,created={2:560})
  s.msg(0,1,300,'updateMe(actor, command)',end=1090)
  s.msg(1,3,405,'user(actor.userId)',end=500)
  s.msg(3,1,500,'当前用户',reply=True)
  s.msg(1,2,560,'«create» User(data)',create=True)
  s.msg(1,2,755,'changeProfile(command)',end=850)
  s.msg(2,1,850,'更新后的 profile()',reply=True)
  s.msg(1,3,950,'updateUser(actor.userId, profile)',end=1020)
  s.msg(3,1,1020,'持久化完成',reply=True)
  s.msg(1,0,1090,op.result,reply=True)
  s.note(1160,'本人编号来自认证上下文。User 保留未提交的字段，应用服务保存资料。')
  return
 parts=[('client','Browser'),('api','AuthController'),('service','StructuredUserService'),('store','ManagementStore')]
 s=Sequence(stem,f'{op.key} 结构化 {op.title}',parts,height=1140)
 s.msg(0,1,300,op.method+' '+op.route,end=940)
 s.msg(1,2,410,'updateMe(actor, command)',end=880)
 s.msg(2,3,560,'user(actor.userId)',end=630)
 s.msg(2,2,695,'copyNotNull(当前资料, command)',end=745)
 s.msg(2,3,800,'updateUser(actor.userId, profile)',end=850)
 s.msg(2,1,900,'更新完成',reply=True)
 s.msg(1,0,970,op.result,reply=True)
 s.note(1040,'本人编号来自认证上下文；资料仅含姓名和手机号。')

def auth_sequence(op):
 calls={
  'AUTH-01':[('store','ManagementStore','userByUsername(username)；核对手机号'),
             ('access','AccessPolicy','allowed(userId, 每个目标系统, login)')],
  'AUTH-02':[('store','ManagementStore','user(actor.userId)'),
             ('effective','EffectiveAccessStore','rolesForUser；permissionsForUser')],
  'AUTH-03':[('sessions','SessionStore','revoke(sessionId, now)')],
  'AUTH-05':[('access','AccessPolicy','[界面码] allowed(userId, system, code)')],
  'AUTH-06':[('access','AccessPolicy','[操作已知] allowed(userId, system, code)')],
 }[op.key]
 parts=[('obj','Caller'),('api','AuthController'),('service','AuthService')]+[(name,typ) for name,typ,_ in calls]
 s=Sequence(op.key.lower()+'-shared',f'{op.key} 共享 {op.title}',parts,height=1140)
 fn={'AUTH-05':'uiAllowed','AUTH-06':'apiAllowed'}.get(op.key,op.function)
 s.msg(0,1,300,op.method+' '+op.route,end=940)
 s.msg(1,2,410,fn+'(输入 / 当前身份)',end=880)
 for i,(_,_,call) in enumerate(calls):
  y=560+155*i
  s.msg(2,3+i,y,call,end=y+70)
 s.msg(2,1,900,'结果或业务错误',reply=True)
 s.msg(1,0,970,op.result,reply=True)
 note={'AUTH-01':'验证后保存会话并签发 JWT，详见登录会话图。',
       'AUTH-03':'撤销当前会话，后续请求由 BearerFilter 拒绝。',
       'AUTH-06':'OperationPermissions 将 operationId 映射为权限码；未知操作返回 404。'
 }.get(op.key,'当前有效授权随请求读取。')
 s.note(1040,note)

def overview_sequences():
 for key,title,parts,messages,note in [
 ('security','S1 安全 撤权后的受控调用',[('client','CourseModule'),('identity','IdentityModule'),('rbac','AuthorizationModule'),('db','MySQL')],[(0,1,'携带既有 Bearer 请求身份及 API 许可'),(1,2,'查询当前用户角色和角色权限'),(2,3,'读取撤销操作已提交的授权关系'),(3,2,'返回当前权限集合'),(2,1,'目标权限不存在'),(1,0,'allowed=false；课程操作拒绝')],'效用树叶 U-S1，需求 REQ-N-01；撤权提交后的新请求拒绝率 100%。'),
 ('performance','S2 性能 并发鉴权',[('client','ClientModule'),('api','AuthModule'),('rbac','AuthorizationModule'),('db','MySQL')],[(0,1,'同一负载下提交并发权限查询'),(1,2,'校验身份并查询权限'),(2,3,'通过有界连接池和关联索引查询'),(3,2,'当前授权集合'),(2,1,'生成布尔许可结果'),(1,0,'返回结果并记录耗时')],'效用树叶 U-P1，需求 REQ-N-02；记录吞吐量、P95、错误率和内存。'),
 ('availability','S3 可用性 应用重启后的会话验证',[('client','ClientModule'),('auth','AuthModule'),('sessions','SessionModule'),('db','MySQL')],[(0,1,'应用恢复后携带原 Bearer 请求'),(1,2,'验签并读取对应会话'),(2,3,'查询会话、账号及目标系统权限'),(3,2,'有效且未撤销的会话'),(2,1,'返回认证身份'),(1,0,'请求继续；依赖故障时返回 503')],'效用树叶 U-A1，需求 REQ-N-03；记录恢复时长，并核对会话和数据完整性。')]:
  s=Sequence('story-'+key,title,parts,height=1120)
  for index,(a,b,msg) in enumerate(messages):s.msg(a,b,310+index*115,msg,reply=index>=3)
  s.note(1020,note)

def lifecycle():
 machine=u.obj('UMLStateMachine',u.MODEL,name='登录会话生命周期',regions=[],ownedElements=[]);u.MODEL['ownedElements'].append(machine)
 region=u.obj('UMLRegion',machine,name='Session',vertices=[],transitions=[]);machine['regions'].append(region)
 d=D('session-state','登录会话状态设计',900,1050,'UMLStatechartDiagram',region)
 initial=u.obj('UMLPseudostate',region,kind='initial');region['vertices'].append(initial)
 iv=d.view('UMLPseudostateView',initial,left=435,top=120,width=30,height=30)
 d.svg.append('<circle cx="450" cy="135" r="15" fill="black"/>')
 states={}
 for name,x,y,desc in [('有效',270,280,'未撤销且当前时间早于 expiresAt'),('已撤销',40,650,'revokedAt 非空'),('已过期',500,650,'当前时间达到 expiresAt')]:
  m=u.obj('UMLState',region,name=name,regions=[],entryActivities=[],exitActivities=[]);region['vertices'].append(m)
  v=d.view('UMLStateView',m,left=x,top=y,width=360,height=130,subViews=[]);box(d,x,y,360,130,name,[desc]);states[name]=(m,v)
 def transition(a,b,pts,label):
  m=u.obj('UMLTransition',region,source=u.ref(a[0]),target=u.ref(b[0]),name=label);region['transitions'].append(m)
  d.line(pts,'dep');d.view('UMLTransitionView',m,tail=u.ref(a[1]),head=u.ref(b[1]),points=';'.join(f'{x}:{y}' for x,y in pts),name=label)
 transition((initial,iv),states['有效'],[(450,155),(450,270)],'登录校验通过 / 保存会话');d.text(475,220,'登录校验通过 / 保存会话',19)
 transition(states['有效'],states['已撤销'],[(350,415),(220,640)],'logout / 记录撤销时间');d.text(80,520,'logout / 记录撤销时间',20)
 transition(states['有效'],states['已过期'],[(550,415),(680,640)],'at(expiresAt)');d.text(610,520,'at(expiresAt)',20)
 d.note(60,850,780,['状态由撤销时间和过期时间推导；不增加业务用户状态。','已撤销和已过期均拒绝认证。删除用户同时清理其会话。'])

def deployment():
 d=D('deployment','Docker Swarm 物理部署',900,1230,'UMLDeploymentDiagram')
 def node(title,x,y,w,h,body):
  m=d.model('UMLNode',name=title,ownedElements=[]);v=d.view('UMLNodeView',m,left=x,top=y,width=w,height=h,subViews=[])
  box(d,x,y,w,h,title,body);d.line([(x,y),(x+12,y-12),(x+w+12,y-12),(x+w,y)]);d.line([(x+w+12,y-12),(x+w+12,y+h-12),(x+w,y+h)])
  return m,v
 browser=node('演示浏览器',240,100,420,100,['IP 加 HTTP 访问两个前端入口'])
 manager=node('Swarm Manager  2 vCPU / 2 GB',60,290,780,220,['113.44.144.119  /  172.31.7.102','Nginx 静态前端与反向代理','RBAC JVM 384 MB  课程 JVM 512 MB'])
 db=node('Swarm Worker 1  2 vCPU / 2 GB',60,650,780,170,['119.3.221.88  /  172.31.12.231','MySQL 512 MB  两个独立库与数据库账号'])
 old=node('Swarm Worker 2  2 vCPU / 1 GB',60,880,360,160,['120.46.176.79','保留现有应用'])
 backup=node('辅助节点  2 vCPU / 1 GB',480,880,360,160,['1.92.106.198','备份与验收辅助'])
 d.line([(450,205),(450,280)],'dep');d.text(470,255,'HTTP :80',19)
 d.line([(450,515),(450,640)],'dep');d.text(475,590,'加密 Overlay  MySQL :3306',19)
 d.note(60,1090,780,['新服务使用独立网络与卷，数据库不发布公网端口。','现有容器保留；当前为单管理节点与单数据库实例。'])

def identity_directory_sequence(op):
 controller,action,query,http={
  'AUTH-07':('IdentityUserController','lookup(actor, userId)','user(id)','GET /auth/users/{userId}'),
  'AUTH-08':('IdentityDepartmentController','departments(actor, query)','directoryDepartments(查询条件)','GET /auth/departments'),
  'AUTH-09':('IdentityDepartmentController','users(actor, departmentId, query)','directoryUsers(查询条件)','GET 部门成员目录'),
 }[op.key]
 height=1300 if op.key=='AUTH-09' else 1160
 q=Sequence(op.key.lower()+'-shared',op.key+' '+op.title,[
  ('obj','Caller'),('api',controller),('svc','IdentityDirectoryService'),
  ('rbac','AccessPolicy'),('store','ManagementStore')
 ],height=height,compact_headers=True)
 x=q.parts[0][3]
 q.d.rect(x-5,305,10,height-390)
 q.activations.append((0,305,height-85))
 q.msg(0,1,290,http,end=height-155)
 q.msg(1,2,410,action,end=height-245)
 q.msg(2,3,535,'granted = allowed(userId, system, code)',end=585)
 if op.key=='AUTH-09':
  q.msg(2,4,665,'department(departmentId)',end=714)
  q.msg(2,3,780,'[平台] scope(actor, departmentId)',end=825)
  y=915
 elif op.key=='AUTH-07':
  y=685
 else:
  y=685
 q.msg(2,4,y,'result = '+query,end=y+50)
 if op.key=='AUTH-07':
  q.msg(2,3,795,'[平台] scope(actor, departmentId)',end=835)
 q.msg(2,1,height-240,'查询结果',reply=True)
 q.msg(1,0,height-145,'200 目录响应',reply=True)
 note='课程使用 TEC，平台使用已有目录权限。无权限返回 403。'
 if op.key=='AUTH-07':note+=' 用户不存在返回 IDENTITY_USER_NOT_FOUND。'
 if op.key=='AUTH-09':note+=' 部门不存在返回 IDENTITY_DEPARTMENT_NOT_FOUND。'
 q.note(height-85,note)

def identity_directory_class():
 from draw_detail_v331 import cls,ref,bottom
 d=D('class-identity-directory','统一身份目录类图',1000,1080)
 departments=cls(d,'IdentityDepartmentController',40,115,420,['list','users'])
 users=cls(d,'IdentityUserController',540,115,420,['get'])
 service=cls(d,'IdentityDirectoryService',290,420,420,['departments','users','lookup'])
 store=cls(d,'ManagementStore',40,770,420,['directoryDepartments','directoryUsers','user'],interface=True)
 access=cls(d,'AccessPolicy',540,770,420,['allowed','scope'])
 ref(d,departments,service,[(330,bottom(departments)),(330,420)],'service')
 ref(d,users,service,[(670,bottom(users)),(670,420)],'service')
 ref(d,service,store,[(330,bottom(service)),(330,770)],'store')
 ref(d,service,access,[(670,bottom(service)),(670,770)],'access')

def files_and_integration():
 d=D('packages-files','源文件与配置文件组织',900,1180,'UMLPackageDiagram')
 for title,x,y,w,h,body in [
 ('backend/src/main/java/cn/edu/xmu/rbac',50,110,800,350,['api/auth user role department system permission identity','service/port 共享用例与存储端口','service/structured 五个事务脚本 Service','service/ddd 应用服务与 domain 领域对象','service/role 继承图防环规则','infrastructure/persistence security logging bootstrap']),
 ('backend/src/main/resources',50,520,800,160,['application.yaml 运行配置   logback-spring.xml 分层日志','db/migration/V1__rbac.sql 数据库迁移']),
 ('frontend/src',50,740,380,150,['App.vue 入口   auth.ts 凭据','views/ 页面   main.ts 路由']),
 ('deploy / tooling',480,740,370,150,['stack.yaml 容器编排','测试 构建 备份 部署脚本']),
 ('vendor / integrations / docs',50,950,800,150,['vendor/clonefactory 教师源码与许可','integrations/ 固定上游版本与适配补丁   docs/ 文档和图稿'])]:d.node(title,x,y,w,h,'UMLPackage',body=body)
 d=D('class-auth-shared','共享认证模块类图',900,1150)
 ctrl=d.node('AuthController',40,110,380,220,ops=[('login',''),('me',''),('logout',''),('check',''),('api','')])
 svc=d.node('AuthService',480,110,380,300,ops=[('login',''),('me',''),('logout',''),('uiAllowed',''),('apiAllowed','')])
 session=d.node('SessionStore',40,510,380,185,'UMLInterface',ops=[('save',''),('find',''),('revoke','')])
 store=d.node('ManagementStore',480,510,380,185,'UMLInterface',ops=[('userByUsername',''),('user','')])
 encoder=d.node('JwtEncoder',40,850,380,150,'UMLInterface',ops=[('encode','')])
 effective=d.node('EffectiveAccessStore',480,850,380,150,'UMLInterface',ops=[('permissionsForUser',''),('rolesForUser','')])
 u.assoc(d,ctrl,svc,[(420,190),(480,190)],role='service')
 u.assoc(d,svc,store,[(670,410),(670,510)],role='store')
 u.assoc(d,svc,session,[(530,410),(530,455),(230,455),(230,510)],role='sessions')
 u.assoc(d,svc,encoder,[(480,295),(445,295),(445,780),(230,780),(230,850)],role='encoder')
 u.assoc(d,svc,effective,[(860,300),(880,300),(880,780),(670,780),(670,850)])
 d.note(40,1040,820,['会话保存在数据库中，JWT 记录身份和目标系统。有效授权在请求时查询。'])
 for key,method,route,operation,target in [('01','GET','/terms','listTerms','TermController'),('02','POST','/terms','createTerm','TermController'),('03','PATCH','/terms/{id}','updateTerm','TermController'),('04','DELETE','/terms/{id}','deleteTerm','TermController'),('05','GET','/admin/courses','listAdminCourses','CourseController'),('06','GET','/courses/{id}','getCourse','CourseController'),('07','POST','/courses/{id}/review','reviewCourse','CourseController')]:
  q=Sequence('course-'+key,'COURSE-'+key+' '+operation,[('client','Browser'),('filter','RbacIntegrationFilter'),('identity','IdentityPlatform'),('business',target)],height=1140)
  q.msg(0,1,290,method+' '+route+' + Bearer',end=970)
  q.msg(1,2,410,'GET /auth/me；检查会话和目标系统',end=500)
  q.msg(2,1,520,'身份与界面码；无效凭据 401',reply=True)
  q.msg(1,2,620,'GET /auth/api-permissions/'+operation,end=710)
  q.msg(2,1,730,'allowed；拒绝 403；依赖不可用 503',reply=True)
  q.msg(1,3,830,'允许后执行 '+operation,end=900)
  q.msg(3,0,990,'老师接口业务结果',reply=True)
  q.note(1050,'课程服务保留上游的输入、对象和状态校验；平台权限在每次请求时读取。')

def utility():
 d=D('utility','质量属性效用树',900,850)
 box(d,40,340,160,100,'效用')
 for i,(name,story,rank) in enumerate([('安全','U-S1 撤权后拒绝','H / M'),('性能','U-P1 并发鉴权','H / M'),('可用性','U-A1 会话恢复','H / M'),('可修改性','U-M1 双实现切换','H / M')]):
  y=100+i*170;box(d,290,y,180,100,name);box(d,560,y,300,100,story,[rank]);d.line([(205,390),(245,390),(245,y+50),(280,y+50)],'dep');d.line([(475,y+50),(550,y+50)],'dep')
 d.note(40,790,820,['叶节点标记顺序为：重要性 / 实现难度。'])

def dataflow():
 for level in [0,1]:
  d=D('dfd-'+str(level),'系统级数据流图 '+('顶层' if level==0 else '第一层'),900,1190)
  flows=[('管理用户','登录及本人资料请求','凭据及本人资料结果','P1','认证会话'),('管理用户','账号与目录维护请求','账号与目录维护结果','P2','目录维护'),('管理用户','角色授权请求','角色授权结果','P3','角色授权'),('课程管理系统','身份与鉴权请求','身份与鉴权结果','P4','身份鉴权')]
  def flow(pts,text,x,y):d.line(pts,'flow');d.text(x,y,text,17,True,bg=True)
  if level==0:
   d.rect(530,100,310,1000,round=20);d.view('RoundRectView',left=530,top=100,width=310,height=1000)
   d.text(685,550,'0',26,True);d.text(685,600,'统一权限管理平台',24,True)
  for i,(actor,request,result,pid,title) in enumerate(flows):
   y=120+i*260;box(d,30,y,180,110,actor)
   if level==1:
    d.svg.append(f'<circle cx="495" cy="{y+55}" r="85" fill="white" stroke="black" stroke-width="1.4"/>');d.view('EllipseView',left=410,top=y-30,width=170,height=170)
    d.text(495,y+42,pid,22,True);d.text(495,y+78,title,22,True)
   x=530 if level==0 else 410
   flow([(215,y+28),(x-10,y+28)],request,(215+x-10)/2,y+8)
   flow([(x-10,y+82),(215,y+82)],result,(215+x-10)/2,y+119)
   if level==1:
    names=[('D1 业务数据',y-5),('D2 登录会话',y+95)] if pid in ['P1','P4'] else [('D1 业务数据',y+35)]
    for name,sy in names:
     d.line([(725,sy),(870,sy)]);d.line([(725,sy+45),(870,sy+45)]);d.text(797,sy+28,name,17,True);d.view('RectangleView',left=725,top=sy,width=145,height=45)
     flow([(715,sy+12),(590,sy+12)],'读取',650,sy-5)
     if pid!='P4':flow([(590,sy+35),(715,sy+35)],'写入',650,sy+65)
  d.note(30,1120,840,['外部实体同名表示同一实体；D1 为八张业务表，D2 为会话表。'])

def analysis():
 d=D('usecases','当前基线用例图',900,1080,'UMLUseCaseDiagram')
 # Native actors, use cases and associations retain their UML semantics.
 actors=[]
 for name,x,y in [('管理用户',80,340),('业务系统',740,700)]:
  m=d.model('UMLActor',name=name);v=d.view('UMLActorView',m,left=x,top=y,width=80,height=110);actors.append((m,v))
  d.svg.append(f'<circle cx="{x+40}" cy="{y+15}" r="14" fill="white" stroke="black"/>');d.line([(x+40,y+29),(x+40,y+68)]);d.line([(x+15,y+40),(x+65,y+40)]);d.line([(x+40,y+68),(x+17,y+94)]);d.line([(x+40,y+68),(x+63,y+94)]);d.text(x+40,y+125,name,21,True)
 for i,title in enumerate(['登录与退出','维护本部门用户','维护角色与授权','维护目录','查询身份与权限']):
  y=120+i*165;m=d.model('UMLUseCase',name=title);v=d.view('UMLUseCaseView',m,left=270,top=y,width=360,height=110)
  d.svg.append(f'<ellipse cx="450" cy="{y+55}" rx="180" ry="55" fill="white" stroke="black"/>');d.text(450,y+62,title,22,True)
  actor=actors[1] if i==4 else actors[0];x0=actor[1]['left']+(0 if i==4 else 80);y0=actor[1]['top']+55;x1=630 if i==4 else 270
  d.line([(x0,y0),(x1,y+55)])
  rel=d.model('UMLAssociation',name='');rel['end1']=u.obj('UMLAssociationEnd',rel,reference=u.ref(actor[0]),multiplicity='');rel['end2']=u.obj('UMLAssociationEnd',rel,reference=u.ref(m),multiplicity='')
  d.view('UMLAssociationView',rel,tail=u.ref(actor[1]),head=u.ref(v),points=f'{x0}:{y0};{x1}:{y+55}')
 d.note(80,990,740,['AN-UC-01：详细功能编号见需求表和各功能用例。'])
 for key,title,steps in [('login','登录活动',['接收并校验登录输入','账号及目标系统许可有效？','保存会话并签发 JWT','返回 accessToken']),('assign','角色分配活动',['读取身份、用户和角色','具备操作权限且同部门？','同事务分配角色','返回分配结果'])]:
  d=D('activity-'+key,title,900,1140,'UMLActivityDiagram')
  activity=d.model('UMLActivity',name=title,nodes=[],edges=[])
  def node(typ,name,x,y,w,h):
   m=u.obj(typ,activity,name=name);activity['nodes'].append(m)
   v=d.view(typ+'View',m,left=x,top=y,width=w,height=h)
   return m,v
  def flow(a,b,pts,guard=''):
   m=u.obj('UMLControlFlow',activity,source=u.ref(a[0]),target=u.ref(b[0]),guard=guard);activity['edges'].append(m)
   d.line(pts,'flow');d.view('UMLControlFlowView',m,tail=u.ref(a[1]),head=u.ref(b[1]),points=';'.join(f'{x}:{y}' for x,y in pts))
  start=node('UMLInitialNode','',435,95,30,30);d.svg.append('<circle cx="450" cy="110" r="15" fill="black"/>')
  first=node('UMLAction',steps[0],160,185,580,90);box(d,160,185,580,90,steps[0])
  decision=node('UMLDecisionNode',steps[1],390,355,120,100)
  d.svg.append('<polygon points="450,355 510,405 450,455 390,405" fill="white" stroke="black"/>');d.text(450,330,steps[1],21,True)
  action=node('UMLAction',steps[2],160,550,580,90);box(d,160,550,580,90,steps[2])
  result=node('UMLAction',steps[3],160,750,580,90);box(d,160,750,580,90,steps[3])
  end=node('UMLActivityFinalNode','',435,960,30,30)
  d.svg.append('<circle cx="450" cy="975" r="16" fill="white" stroke="black"/><circle cx="450" cy="975" r="10" fill="black"/>')
  rejected=node('UMLAction','返回校验错误',650,360,210,90);box(d,650,360,210,90,'返回校验错误')
  flow(start,first,[(450,130),(450,175)])
  flow(first,decision,[(450,280),(450,345)])
  flow(decision,action,[(450,460),(450,540)],'[是]');d.text(475,505,'[是]',19)
  flow(decision,rejected,[(515,405),(640,405)],'[否]');d.text(575,390,'[否]',19)
  flow(action,result,[(450,645),(450,740)])
  flow(result,end,[(450,845),(450,950)])
  flow(rejected,end,[(755,455),(810,455),(810,975),(470,975)])
  d.note(100,1040,700,['写入失败时事务回滚，异常按统一错误契约返回。'])

if __name__=='__main__':
 from draw_detail_v331 import classes as detailed_classes, supporting
 architecture();detailed_classes();domain_relations();core_classes();supporting();overview_sequences();lifecycle();deployment();files_and_integration();identity_directory_class();utility();dataflow();analysis()
 for op in OPS:
  if (op.module=='Auth' and op.function!='updateMe') or op.function=='options':sequence_for(op,'shared')
  else:
   sequence_for(op,'structured');sequence_for(op,'ddd')
 from v34_sequences import ui_sequences
 ui_sequences()
 from v35_diagrams import classes as hierarchy_classes
 hierarchy_classes()
 u.finish()
 (u.OUT/'coverage.json').write_text(json.dumps({'operations':len(OPS),'diagrams':len(u.DIAGRAMS),'operationKeys':[o.key for o in OPS]},ensure_ascii=False,indent=2))
