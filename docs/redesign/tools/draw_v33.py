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
 d=D('architecture','系统总体分层架构',900,874)
 box(d,80,76,740,79,'浏览器展现层',['Vue 3  TypeScript   权限后台 / 课程演示入口'])
 box(d,80,184,740,106,'API 输入层',['Controller 输入校验与 CloneFactory','Bearer 认证、异常映射与请求日志'])
 box(d,80,319,740,78,'Service Port',['五个用例端口  数据契约  管理、授权及会话端口'])
 box(d,80,426,345,104,'结构化业务实现',['事务脚本 Service','校验、编排、调用存储'])
 box(d,475,426,345,104,'DDD 业务实现',['应用服务','领域实体与业务规则'])
 box(d,80,559,740,106,'共享 Infrastructure',['Spring Data JPA  MySQL   JWT 签名与校验','存储适配器  Logback 分层日志'])
 box(d,80,694,740,78,'MySQL 8.4',['八张业务表和一张技术会话表'])
 for a,b in [(155,184),(290,319),(665,694)]:d.line([(450,a+5),(450,b-5)],'dep')
 for x in [252,647]:
  d.line([(x,402),(x,421)],'dep')
  d.line([(x,535),(x,554)],'dep')
 d.note(80,792,740,['启动时由 app.backend-strategy 选择业务实现。','两套实现共用认证、目录、存储和外部契约。'])

 d=D('packages','系统 UML 包图与依赖',900,694,'UMLPackageDiagram')
 nodes={}
 for key,title,x,y,w,h,body in [
  ('api','api',80,86,740,86,['auth  user  role  department  system  permission  identity']),
  ('port','service.port',80,222,740,86,['五个 UseCase  Data  三个存储端口']),
  ('structured','service.structured',80,358,345,86,['StructuredUserService  StructuredRoleService']),
  ('ddd','service.ddd',475,358,345,86,['DddUserService  DddRoleService  domain']),
  ('infra','infrastructure',80,500,740,90,['persistence  security  logging']),
 ]:
  nodes[key]=d.node(title,x,y,w,h,'UMLPackage',body=body)
 d.edge(nodes['api'],nodes['port'],[(450,172),(450,217)])
 d.edge(nodes['structured'],nodes['port'],[(250,340),(250,313)])
 d.edge(nodes['ddd'],nodes['port'],[(650,340),(650,313)])
 d.edge(nodes['infra'],nodes['port'],[(825,545),(857,545),(857,263),(825,263)])
 d.note(80,610,740,['API 依赖用例端口，两套业务实现经存储端口调用底层。','Infrastructure 实现存储端口。'])

def domain_relations():
 d=D('domain-relations','核心业务对象关系模型',900,1100)
 items={}
 for name,x,y,ops in [('Department',40,120,[]),('User',520,120,[('requireRoleInDepartment','')]),('Role',40,510,[('requireBaseRole','')]),('Permission',520,510,[]),('System',520,850,[])]:items[name]=d.node(name,x,y,340,170 if name in ['User','Role'] else 140,ops=ops)
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
  specs=[('UserController',[],[('assign','user: Identifier;role: Identifier','void')],'boundary'),('UserUseCase',[],[('assign','actor: Identity;user: Identifier;role: Identifier','void')],''),(prefix+'UserService',[],[('assign','actor: Identity;user: Identifier;role: Identifier','void')],'control'),('ManagementStore',[],[('user','id: Identifier','UserData'),('assignRole','user: Identifier;role: Identifier','void')],''),('User',[('username','String'),('name','String'),('phone','String')],[('requireRoleInDepartment','role: Data.Role','void')] if mode=='ddd' else [],'entity'),('Role',[('name','String')],[('requireBaseRole','baseRole: Data.Role','void')] if mode=='ddd' else [],'entity')]
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
 if op.key=='AUTH-02':
  return current_user_sequence(op)
 return auth_sequence(op)

def current_user_sequence(op):
 s=Sequence('auth-02-shared',f'{op.key} {op.title}',[
  ('client','Caller'),('api','AuthController'),('current','CurrentIdentity'),
  ('service','AuthService'),('store','ManagementStore'),('effective','EffectiveAccessStore'),
 ],height=790)
 s.msg(0,1,190,'GET /auth/me',end=705)
 s.msg(1,2,247,'get()',end=277)
 s.msg(2,1,282,'actor',reply=True)
 s.msg(1,3,333,'me(actor)',end=659)
 s.msg(3,4,385,'user(actor.userId())',end=415)
 s.msg(4,3,420,'Data.User',reply=True)
 s.msg(3,5,472,'rolesForUser(actor.userId())',end=502)
 s.msg(5,3,507,'角色名称',reply=True)
 s.msg(3,5,568,'permissionsForUser(user.id(), actor.systemKey())',end=598)
 s.msg(5,3,603,'有效权限码',reply=True)
 s.msg(3,1,660,'CurrentUserResult',reply=True)
 s.msg(1,0,706,'200 CurrentUserVO',reply=True)
 s.note(744,'课程端按契约转换角色，响应只带当前系统界面码。')

def auth_sequence(op):
 calls={
  'AUTH-01':[('store','ManagementStore','userByUsername(username)；核对手机号'),
             ('access','AccessPolicy','allowed(userId, 每个目标系统, login)')],
  'AUTH-03':[('sessions','SessionStore','revoke(sessionId, now)')],
  'AUTH-05':[('access','AccessPolicy','[界面码] allowed(userId, system, code)')],
  'AUTH-06':[('access','AccessPolicy','[操作已知] allowed(userId, system, code)')],
 }[op.key]
 parts=[('obj','Caller'),('api','AuthController'),('service','AuthService')]+[(name,typ) for name,typ,_ in calls]
 return_y = 420 + 96 * len(calls)
 s=Sequence(op.key.lower()+'-shared',f'{op.key} 共享 {op.title}',parts,height=return_y+152)
 fn={'AUTH-05':'uiAllowed','AUTH-06':'apiAllowed'}.get(op.key,op.function)
 s.msg(0,1,195,op.method+' '+op.route,end=return_y+52)
 s.msg(1,2,286,fn+'(输入 / 当前身份)',end=return_y)
 for i,(_,_,call) in enumerate(calls):
  y=375+96*i
  s.msg(2,3+i,y,call,end=y+46)
 s.msg(2,1,return_y,'结果或业务错误',reply=True)
 s.msg(1,0,return_y+54,op.result,reply=True)
 note={'AUTH-01':'验证后保存会话并签发 JWT，详见登录会话图。',
       'AUTH-03':'撤销当前会话，后续请求由 BearerFilter 拒绝。',
       'AUTH-06':'OperationPermissions 将 operationId 映射为权限码；未知操作返回 404。'
 }.get(op.key,'当前有效授权随请求读取。')
 s.note(return_y+91,note)

def overview_sequences():
 for key,title,parts,messages,note in [
 ('security','S1 安全 撤权后的受控调用',[('client','CourseModule'),('identity','IdentityModule'),('rbac','AuthorizationModule'),('db','MySQL')],[(0,1,'携带既有 Bearer 请求身份及 API 许可'),(1,2,'查询当前用户角色和角色权限'),(2,3,'读取撤销操作已提交的授权关系'),(3,2,'返回当前权限集合'),(2,1,'目标权限不存在'),(1,0,'allowed=false；课程操作拒绝')],'效用树叶 U-S1，需求 REQ-N-01；撤权提交后的新请求拒绝率 100%。'),
 ('performance','S2 性能 并发鉴权',[('client','ClientModule'),('api','AuthModule'),('rbac','AuthorizationModule'),('db','MySQL')],[(0,1,'同一负载下提交并发权限查询'),(1,2,'校验身份并查询权限'),(2,3,'通过有界连接池和关联索引查询'),(3,2,'当前授权集合'),(2,1,'生成布尔许可结果'),(1,0,'返回结果并记录耗时')],'效用树叶 U-P1，需求 REQ-N-02；记录吞吐量、P95、错误率和内存。'),
 ('availability','S3 可用性 应用重启后的会话验证',[('client','ClientModule'),('auth','AuthModule'),('sessions','SessionModule'),('db','MySQL')],[(0,1,'应用恢复后携带原 Bearer 请求'),(1,2,'验签并读取对应会话'),(2,3,'查询会话、账号及目标系统权限'),(3,2,'有效且未撤销的会话'),(2,1,'返回认证身份'),(1,0,'请求继续；依赖故障时返回 503')],'效用树叶 U-A1，需求 REQ-N-03；记录恢复时长，并核对会话和数据完整性。')]:
  s=Sequence('story-'+key,title,parts,height=775)
  for index,(a,b,msg) in enumerate(messages):s.msg(a,b,197+index*86,msg,reply=index>=3)
  s.note(691,note)

def lifecycle():
 machine=u.obj('UMLStateMachine',u.MODEL,name='登录会话生命周期',regions=[],ownedElements=[]);u.MODEL['ownedElements'].append(machine)
 region=u.obj('UMLRegion',machine,name='Session',vertices=[],transitions=[]);machine['regions'].append(region)
 d=D('session-state','登录会话状态设计',900,586,'UMLStatechartDiagram',region)
 initial=u.obj('UMLPseudostate',region,kind='initial');region['vertices'].append(initial)
 iv=d.view('UMLPseudostateView',initial,left=435,top=74,width=30,height=30)
 d.svg.append('<circle cx="450" cy="89" r="15" fill="black"/>')
 states={}
 for name,x,y,desc in [('有效',270,181,'未撤销且当前时间早于 expiresAt'),('已撤销',40,390,'revokedAt 非空'),('已过期',500,390,'当前时间达到 expiresAt')]:
  m=u.obj('UMLState',region,name=name,regions=[],entryActivities=[],exitActivities=[]);region['vertices'].append(m)
  v=d.view('UMLStateView',m,left=x,top=y,width=360,height=90,subViews=[]);box(d,x,y,360,90,name,[desc]);states[name]=(m,v)
 def transition(a,b,pts,label):
  m=u.obj('UMLTransition',region,source=u.ref(a[0]),target=u.ref(b[0]),name=label);region['transitions'].append(m)
  d.line(pts,'dep');d.view('UMLTransitionView',m,tail=u.ref(a[1]),head=u.ref(b[1]),points=';'.join(f'{x}:{y}' for x,y in pts),name=label)
 transition((initial,iv),states['有效'],[(450,109),(450,176)],'登录校验通过 / 保存会话');d.text(475,150,'登录校验通过 / 保存会话',19)
 transition(states['有效'],states['已撤销'],[(350,276),(220,385)],'logout / 记录撤销时间');d.text(70,332,'logout / 记录撤销时间',20)
 transition(states['有效'],states['已过期'],[(550,276),(680,385)],'at(expiresAt)');d.text(630,332,'at(expiresAt)',20)
 d.note(60,495,780,['状态由撤销时间和过期时间推导；不增加业务用户状态。','已撤销和已过期均拒绝认证。'])

def deployment():
 d=D('deployment','Docker Swarm 物理部署',900,816,'UMLDeploymentDiagram')
 def node(title,x,y,w,h,body):
  m=d.model('UMLNode',name=title,ownedElements=[]);v=d.view('UMLNodeView',m,left=x,top=y,width=w,height=h,subViews=[])
  box(d,x,y,w,h,title,body);d.line([(x,y),(x+12,y-12),(x+w+12,y-12),(x+w,y)]);d.line([(x+w+12,y-12),(x+w+12,y+h-12),(x+w,y+h)])
  return m,v
 browser=node('演示浏览器',240,81,420,79,['IP 加 HTTP 访问两个前端入口'])
 manager=node('Swarm Manager  2 vCPU / 2 GB',60,217,780,140,['113.44.144.119  /  172.31.7.102','Nginx 静态前端与反向代理','RBAC JVM 384 MB  课程 JVM 512 MB'])
 db=node('Swarm Worker 1  2 vCPU / 2 GB',60,429,780,100,['119.3.221.88  /  172.31.12.231','MySQL 512 MB  两个独立库与数据库账号'])
 old=node('Swarm Worker 2  2 vCPU / 1 GB',60,575,360,110,['120.46.176.79','保留现有应用'])
 backup=node('辅助节点  2 vCPU / 1 GB',480,575,360,110,['1.92.106.198','备份与验收辅助'])
 d.line([(450,165),(450,200)],'dep');d.text(468,187,'HTTP :80',19)
 d.line([(450,362),(450,412)],'dep');d.text(468,392,'加密 Overlay  MySQL :3306',19)
 d.note(60,712,780,['新服务使用独立网络与卷，数据库不发布公网端口。','现有容器保留；当前为单管理节点与单数据库实例。'])

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
  q=Sequence('course-'+key,'COURSE-'+key+' '+operation,[('client','Browser'),('filter','RbacIntegrationFilter'),('identity','IdentityPlatform'),('business',target)],height=705)
  q.msg(0,1,190,method+' '+route+' + Bearer',end=590)
  q.msg(1,2,253,'GET /auth/me',end=287)
  q.msg(2,1,299,'身份与界面码；无效凭据 401',reply=True)
  q.msg(1,2,376,'GET /auth/api-permissions/\n'+operation,end=411)
  q.msg(2,1,425,'allowed；拒绝 403；依赖不可用 503',reply=True)
  q.msg(1,3,486,'允许后执行 '+operation,end=531)
  q.msg(3,1,538,'业务处理结果',reply=True)
  q.msg(1,0,590,'返回 HTTP 响应',reply=True)
  q.note(633,'课程服务保留上游的输入、对象和状态校验；平台权限在每次请求时读取。')

def utility():
 d=D('utility','质量属性效用树',900,850)
 box(d,40,340,160,100,'效用')
 for i,(name,story,rank) in enumerate([('安全','U-S1 撤权后拒绝','H / M'),('性能','U-P1 并发鉴权','H / M'),('可用性','U-A1 会话恢复','H / M'),('可修改性','U-M1 双实现切换','H / M')]):
  y=100+i*170;box(d,290,y,180,100,name);box(d,560,y,300,100,story,[rank]);d.line([(205,390),(245,390),(245,y+50),(280,y+50)],'dep');d.line([(475,y+50),(550,y+50)],'dep')
 d.note(40,790,820,['叶节点标记顺序为：重要性 / 实现难度。'])

def dataflow():
 flows = [
  ('登录及本人资料请求', '凭据及本人资料结果', '1', '认证会话'),
  ('用户与目录请求', '用户与目录结果', '2', '目录查询'),
  ('角色授权请求', '角色授权结果', '3', '角色授权'),
  ('身份与鉴权请求', '身份与鉴权结果', '4', '接入身份鉴权'),
 ]

 def node(d, x, y, w, h, title, process=False):
  d.rect(x, y, w, h, round=12 if process else 0)
  d.view('RoundRectView' if process else 'RectangleView', left=x, top=y, width=w, height=h)
  for index, label in enumerate(title):
   baseline = y + h / 2 + 7 + (index - (len(title) - 1) / 2) * 29
   d.text(x + w / 2, baseline, label, 20, True, True)

 def free_line(d, points):
  d.view('FreelineEdgeView', points=';'.join(f'{x}:{y}' for x, y in points), lineStyle=1)

 def flow(d, start, end, y, label):
  points = [(start, y), (end, y)]
  d.line(points, 'flow')
  free_line(d, points)
  back = end + (-9 if end > start else 9)
  free_line(d, [(back, y - 5), (end, y), (back, y + 5)])
  d.text((start + end) / 2, y - 8, label, 17, True)

 def exchange(d, left, right, y, request, result):
  flow(d, left + 5, right - 5, y - 17, request)
  flow(d, right - 5, left + 5, y + 18, result)

 def store(d, x, y, title):
  for line_y in (y - 20, y + 20):
   points = [(x, line_y), (870, line_y)]
   d.line(points)
   free_line(d, points)
  d.text((x + 870) / 2, y + 6, title, 17, True)

 top = D('dfd-0', '系统级数据流图 顶层', 900, 338)
 node(top, 27, 76, 126, 235, ['管理用户'])
 node(top, 381, 76, 172, 235, ['0', '统一权限管理平台'], process=True)
 node(top, 733, 149, 140, 90, ['课程管理系统'])
 for (request, result, _, _), y in zip(flows[:3], (118, 193, 268)):
  exchange(top, 153, 381, y, request, result)
 request, result, _, _ = flows[3]
 flow(top, 728, 558, 176, request)
 flow(top, 558, 728, 211, result)

 first = D('dfd-1', '系统级数据流图 第一层', 900, 632)
 node(first, 27, 76, 126, 364, ['管理用户'])
 node(first, 27, 478, 126, 79, ['课程管理系统'])
 for (request, result, number, name), y in zip(flows, (122, 254, 386, 518)):
  node(first, 344, y - 39, 153, 104, [number, name], process=True)
  exchange(first, 153, 344, y, request, result)
  store(first, 736, y - 8, 'D1 业务数据')
  flow(first, 731, 502, y - 20, '账号与有效权限' if number in ('1', '4') else '用户与目录数据' if number == '2' else '角色及授权关系')
  if number in ('1', '4'):
   store(first, 736, y + 42, 'D2 登录会话')
   flow(first, 731, 502, y + 33, '当前会话')
   flow(first, 502, 731, y + 59, '会话记录或撤销时间')
  elif number == '3':
   flow(first, 502, 731, y + 10, '角色与授权变更')
 first.text(450, 613, 'D1 为八张业务表，D2 为登录会话表。同编号表示同一数据存储。', 17, True)

def analysis():
 d=D('usecases','系统用例图',900,668,'UMLUseCaseDiagram')
 # Native actors, use cases and associations retain their UML semantics.
 actors=[]
 for name,x,y in [('管理用户',80,248),('业务系统',740,505)]:
  m=d.model('UMLActor',name=name);v=d.view('UMLActorView',m,left=x,top=y,width=80,height=110);actors.append((m,v))
  d.svg.append(f'<circle cx="{x+40}" cy="{y+15}" r="14" fill="white" stroke="black"/>');d.line([(x+40,y+29),(x+40,y+68)]);d.line([(x+15,y+40),(x+65,y+40)]);d.line([(x+40,y+68),(x+17,y+94)]);d.line([(x+40,y+68),(x+63,y+94)]);d.text(x+40,y+125,name,21,True)
 for i,title in enumerate(['登录、查看本人资料与退出','查询用户与分配角色','维护角色与授权','查看部门、系统与权限目录','查询身份与权限']):
  y=86+i*107;m=d.model('UMLUseCase',name=title);v=d.view('UMLUseCaseView',m,left=270,top=y,width=360,height=77)
  d.svg.append(f'<ellipse cx="450" cy="{y+38}" rx="180" ry="38" fill="white" stroke="black"/>');d.text(450,y+46,title,22,True)
  actor=actors[1] if i==4 else actors[0];x0=actor[1]['left']+(0 if i==4 else 80);y0=actor[1]['top']+55;x1=630 if i==4 else 270
  d.line([(x0,y0),(x1,y+38)])
  rel=d.model('UMLAssociation',name='');rel['end1']=u.obj('UMLAssociationEnd',rel,reference=u.ref(actor[0]),multiplicity='');rel['end2']=u.obj('UMLAssociationEnd',rel,reference=u.ref(m),multiplicity='')
  d.view('UMLAssociationView',rel,tail=u.ref(actor[1]),head=u.ref(v),points=f'{x0}:{y0};{x1}:{y+38}')
 d.note(80,591,740,['AN-UC-01：详细功能编号见需求表和各功能用例。'])
 for key,title,steps in [('login','登录活动',['接收并校验登录输入','账号及目标系统许可有效？','保存会话并签发 JWT','返回 accessToken']),('assign','角色分配活动',['读取身份、用户和角色','具备操作权限且同部门？','同事务分配角色','返回分配结果'])]:
  d=D('activity-'+key,title,900,707,'UMLActivityDiagram')
  activity=d.model('UMLActivity',name=title,nodes=[],edges=[])
  def node(typ,name,x,y,w,h):
   m=u.obj(typ,activity,name=name);activity['nodes'].append(m)
   v=d.view(typ+'View',m,left=x,top=y,width=w,height=h)
   return m,v
  def flow(a,b,pts,guard=''):
   m=u.obj('UMLControlFlow',activity,source=u.ref(a[0]),target=u.ref(b[0]),guard=guard);activity['edges'].append(m)
   d.line(pts,'flow');d.view('UMLControlFlowView',m,tail=u.ref(a[1]),head=u.ref(b[1]),points=';'.join(f'{x}:{y}' for x,y in pts))
  start=node('UMLInitialNode','',435,67,30,30);d.svg.append('<circle cx="450" cy="82" r="15" fill="black"/>')
  first=node('UMLAction',steps[0],188,125,524,64);box(d,188,125,524,64,steps[0])
  decision=node('UMLDecisionNode',steps[1],390,249,120,80)
  d.svg.append('<polygon points="450,249 510,289 450,329 390,289" fill="white" stroke="black"/>');d.text(450,235,steps[1],21,True)
  action=node('UMLAction',steps[2],188,378,524,65);box(d,188,378,524,65,steps[2])
  result=node('UMLAction',steps[3],188,491,524,65);box(d,188,491,524,65,steps[3])
  end=node('UMLActivityFinalNode','',435,595,30,30)
  d.svg.append('<circle cx="450" cy="610" r="16" fill="white" stroke="black"/><circle cx="450" cy="610" r="10" fill="black"/>')
  rejected=node('UMLAction','返回校验错误',655,257,217,64);box(d,655,257,217,64,'返回校验错误')
  flow(start,first,[(450,102),(450,120)])
  flow(first,decision,[(450,194),(450,244)])
  flow(decision,action,[(450,334),(450,373)],'[是]');d.text(466,359,'[是]',19)
  flow(decision,rejected,[(515,289),(650,289)],'[否]');d.text(580,277,'[否]',19)
  flow(action,result,[(450,448),(450,486)])
  flow(result,end,[(450,561),(450,589)])
  flow(rejected,end,[(763,326),(817,326),(817,610),(471,610)])
  d.note(100,637,700,['写入失败时事务回滚，异常按统一错误契约返回。'])

if __name__=='__main__':
 from draw_detail_v331 import classes as detailed_classes, supporting
 architecture();detailed_classes();domain_relations();core_classes();supporting();overview_sequences();lifecycle();deployment();files_and_integration();identity_directory_class();utility();dataflow();analysis()
 for op in OPS:
  if op.module in ['Auth','Department','System','Permission'] or op.function=='options':sequence_for(op,'shared')
  else:
   sequence_for(op,'structured');sequence_for(op,'ddd')
 from v34_sequences import ui_sequences
 ui_sequences()
 from v35_diagrams import classes as hierarchy_classes
 hierarchy_classes()
 u.finish()
 (u.OUT/'coverage.json').write_text(json.dumps({'operations':len(OPS),'diagrams':len(u.DIAGRAMS),'operationKeys':[o.key for o in OPS]},ensure_ascii=False,indent=2))
