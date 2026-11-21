"""v3.2.2 class-chain and package layouts, updated to the executable baseline."""
import draw_spec as u
import re
from draw_complete import Sequence,wrap
from v33_catalog import MODULES
D=u.D

def cls(d,name,x,y,w=340,methods=(),attrs=(),interface=False,stereo='',static_methods=()):
 m=d.model('UMLInterface' if interface else 'UMLClass',name=name,attributes=[],operations=[],stereotype=stereo)
 for a,t in attrs:m['attributes'].append(u.obj('UMLAttribute',m,name=a,type=t,visibility='private'))
 for a in methods:
  method=u.obj('UMLOperation',m,name=a,visibility='public',parameters=[],isStatic=a in static_methods)
  m['operations'].append(method)
 old=u.CLASSES.get(name);u.CLASSES[name]=m
 start=len(d.svg)
 result=u.klass(d,name,x,y,w)
 # As in v3.2.2, module plates project operation names; the detailed
 # function tables carry the inputs and results.
 result[1]['showOperationSignature']=False
 for i in range(start,len(d.svg)):
  d.svg[i]=re.sub(r'(?<=>)\+ (\w+)\([^<]*\): [^<]*(?=</text>)',r'+ \1()',d.svg[i])
 for compartment in result[1]['subViews']:
  if compartment['_type']=='UMLOperationCompartmentView':
   for item in compartment['subViews']:
    item['text']=re.sub(r': void$', '',item['text'])
 if old:u.CLASSES[name]=old
 else:u.CLASSES.pop(name,None)
 return result

def note(d,x,y,w,lines):
 d.note(x,y,w,[s for line in lines for s in wrap(line,w-30,20)])

def bottom(a):return a[1]['top']+a[1]['height']
def center(a):return a[1]['left']+a[1]['width']/2

def ref(d,a,b,pts,label=''):
 u.assoc(d,a,b,pts,role=label,mult='1')
def dep(d,a,b,pts,real=False):d.edge(a,b,pts,real=real)

def pack(d,name,x,y,w,body,h=None):
 lines=[s for row in body for s in wrap(row,w-26,19)]
 return d.node(name,x,y,w,max(h or 0,85+len(lines)*28),'UMLPackage',body=lines)

FIELDS={
 'User':[('id','Long'),('username','String'),('name','String'),('phone','String'),('departmentId','Long')],
 'Role':[('id','Long'),('name','String'),('departmentId','Long')],
 'Department':[('id','Long'),('name','String'),('parentId','Long')],
 'System':[('id','Long'),('systemKey','String'),('name','String')],
 'Permission':[('id','Long'),('systemId','Long'),('code','String'),('name','String')]}
STORE={
 'User':['users','user','createUser','updateUser','deleteUser','userRoles','assignRole','revokeRole'],
 'Role':['roles','role','createRole','renameRole','deleteRole','rolePermissions','grantPermission','revokePermission'],
 'Department':['departments','department','createDepartment','renameDepartment','deleteDepartment'],
 'System':['systems','system','createSystem','renameSystem','deleteSystem'],
 'Permission':['permissions','permission','createPermission','renamePermission','deletePermission']}
REQUEST={
 'User':('ManagementDTOs.CreateUserDTO',[('username','String'),('name','String'),('phone','String')]),
 'Role':('ManagementDTOs.NameDTO',[('name','String')]),
 'Department':('ManagementDTOs.CreateDepartmentDTO',[('name','String'),('parentId','Long')]),
 'System':('ManagementDTOs.CreateSystemDTO',[('systemKey','String'),('name','String')]),
 'Permission':('ManagementDTOs.CreatePermissionDTO',[('code','String'),('name','String')])}
DOMAIN={
 'User':([('data','Data.User'),('profile','Data.Profile')],['changeProfile','requireRoleInDepartment','profile']),
 'Role':([('name','String')],['rename','name']),
 'Department':([('name','String')],['rename','name']),
 'System':([('name','String')],['rename','name']),
 'Permission':([('name','String')],['rename','name'])}

def classes():
 for module in ['User','Role','Department','System','Permission']:
  for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
   title=MODULES[module][0];label='结构化' if mode=='structured' else 'DDD'
   d=D(f'class-{mode}-{module.lower()}',f'{title}模块 {label} 类图',1180,1710)
   methods=['list','get','create','update','delete','roles','assign','revoke'] if module=='User' else ['list','get','create','update','delete','permissions','grant','revoke'] if module=='Role' else ['list','create','update','delete']
   a=cls(d,module+'Controller',30,110,330,methods)
   b=cls(d,module+'UseCase',425,110,330,methods,interface=True)
   c=cls(d,prefix+module+'Service',820,110,330,methods)
   ref(d,a,b,[(360,190),(425,190)],'service')
   dep(d,c,b,[(815,245),(765,245)],True)
   request=cls(d,*((REQUEST[module][0],30,500,330)),attrs=REQUEST[module][1])
   data=cls(d,'Data.'+module,425,500,330,attrs=FIELDS[module],stereo='record')
   store=cls(d,'ManagementStore',820,500,330,STORE[module],interface=True)
   dep(d,c,data,[(850,bottom(c)+5),(850,440),(590,440),(590,490)])
   ref(d,c,store,[(985,bottom(c)+5),(985,490)],'store')
   command_name={'User':'Data.CreateUser','Role':'Data.Name','Department':'Data.CreateDepartment','System':'Data.CreateSystem','Permission':'Data.CreatePermission'}[module]
   command=cls(d,command_name,30,730,330,attrs=REQUEST[module][1],stereo='record')
   copy=cls(d,'CloneFactory',30,1010,330,['copy','copyNotNull'] if module=='User' else ['copy'],stereo='utility')
   policy=cls(d,'AccessPolicy',425,1010,330,['require','scope','allowed'])
   adapter=cls(d,'JdbcManagementStore',820,1010,330,STORE[module])
   dep(d,adapter,store,[(985,1005),(985,bottom(store)+10)],True)
   dep(d,a,copy,[(30,285),(12,285),(12,1060),(25,1060)])
   dep(d,a,request,[(195,bottom(a)+5),(195,490)])
   dep(d,copy,command,[(195,1005),(195,bottom(command)+10)])
   ref(d,c,policy,[(1150,280),(1168,280),(1168,940),(590,940),(590,1010)],'access')
   if mode=='ddd':
    attrs,ops=DOMAIN[module]
    entity=cls(d,module,425,1290,330,ops,attrs)
    if module=='User':dep(d,entity,copy,[(425,1400),(390,1400),(390,1080),(365,1080)])
    y=min(1400,bottom(entity)-20)
    dep(d,c,entity,[(1150,320),(1175,320),(1175,y),(765,y)])
   else:
    note(d,30,1310,725,['Service 在事务内编排权限、范围和存储调用。','Data 同时作为共享查询结果和持久化数据契约。','方法在本图省略参数及返回类型，契约见对应功能。'])
   jdbc=cls(d,'JdbcTemplate',820,1460,330,['query','queryForObject','update'])
   ref(d,adapter,jdbc,[(985,bottom(adapter)+5),(985,1450)],'jdbc')
   d.text(590,1660,('继承操作及 RoleHierarchy 见 RBAC1 专题类图。' if module=='Role' else 'Controller 依赖端口，Service 实现端口；JDBC 适配器实现 ManagementStore。'),20,True)
 domain()

def domain():
 d=D('domain','DDD 领域对象完整类图',1120,1910)
 user=cls(d,'User',40,120,470,DOMAIN['User'][1],DOMAIN['User'][0],stereo='aggregate root')
 ud=cls(d,'Data.User',630,120,450,attrs=FIELDS['User'],stereo='record')
 profile=cls(d,'Data.Profile',630,490,450,attrs=[('name','String'),('phone','String')],stereo='record')
 ref(d,user,ud,[(515,225),(620,225)],'data')
 ref(d,user,profile,[(515,345),(560,345),(560,570),(620,570)],'profile')
 role=cls(d,'Role',40,720,470,DOMAIN['Role'][1],DOMAIN['Role'][0],stereo='aggregate root')
 rd=cls(d,'Data.Role',630,775,450,attrs=FIELDS['Role'],stereo='record')
 dep(d,user,rd,[(275,bottom(user)+5),(275,650),(855,650),(855,765)])
 d.text(455,625,'requireRoleInDepartment(role)',18,True,bg=True)
 dep(d,role,rd,[(515,825),(620,825)])
 for i,module in enumerate(['Department','System','Permission']):
  attrs,ops=DOMAIN[module]
  cls(d,module,40+i*365,1210,330,ops,attrs,stereo='entity')
 note(d,630,1025,450,['Role 维护名称；权限关系由关联表保存。','Data 保存编号、归属等只读契约。','跨聚合的对象范围由应用服务检查。'])
 graph=cls(d,'RoleHierarchy',40,1530,470,['requireAcyclic'],stereo='domain rule',static_methods=['requireAcyclic'])
 edge=cls(d,'Data.Inheritance',630,1530,450,attrs=[('seniorRoleId','Long'),('juniorRoleId','Long')],stereo='record')
 dep(d,graph,edge,[(515,1610),(620,1610)])
 note(d,40,1760,1040,['RoleHierarchy 读取本次操作的部门继承边，检查从基础角色能否走回高级角色。','应用服务负责事务、部门锁与持久化，领域规则负责图的无环性。'])


def overall_classes():
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  label='结构化' if mode=='structured' else 'DDD'
  d=D('architecture-classes-'+mode,'系统总体架构类图 '+label,1200,1670)
  for x,t in [(210,'API 输入'),(600,'Service Port'),(990,'业务实现')]:d.text(x,95,t,24,True,True)
  svcs=[]
  for i,module in enumerate(['User','Role','Department','System','Permission']):
   y=150+i*225
   a=cls(d,module+'Controller',40,y,340,['list','create'])
   b=cls(d,module+'UseCase',430,y,340,['list','create'],interface=True)
   c=cls(d,prefix+module+'Service',820,y,340,['list','create'])
   ref(d,a,b,[(380,y+80),(430,y+80)],'service')
   dep(d,c,b,[(815,y+120),(780,y+120)],True);svcs.append(c)
  policy=cls(d,'AccessPolicy',40,1320,340,['require','scope','allowed'])
  store=cls(d,'ManagementStore',430,1320,340,['users','roles','inheritanceEdges'],interface=True)
  adapter=cls(d,'JdbcManagementStore',820,1320,340,['users','roles','inheritanceEdges'])
  dep(d,adapter,store,[(815,1400),(780,1400)],True)
  # Shared dependencies are routed in the gutters, once for the service group.
  for c in svcs:ref(d,c,store,[(1165,c[1]['top']+65),(1180,c[1]['top']+65),(1180,1260),(600,1260),(600,1320)],'store')
  ref(d,svcs[-1],policy,[(990,bottom(svcs[-1])),(990,1215),(210,1215),(210,1320)],'access')
  note(d,40,1540,1120,['每个用例端口按配置装配一套实现。共用认证服务、数据契约及存储适配器。',('DDD 应用服务进一步依赖五类领域对象，详情见各模块类图。' if mode=='ddd' else '事务脚本通过 AccessPolicy 检查操作许可及部门范围，再调用 ManagementStore。')])


def package_diagrams():
 for mode in ['structured','ddd']:
  label='结构化' if mode=='structured' else 'DDD'
  specs=[
   ('api',40,120,380,185,['auth / identity / shared','user / role / department','system / permission']),
   ('service.port',500,120,380,155,['共享用例与持久化契约']),
   ('service.'+mode,40,470,380,155,['事务脚本' if mode=='structured' else '应用用例编排']),
   ('service.auth',500,470,380,155,['身份、界面和 API 决策']),
   ('service.security',500,820,380,155,['RBAC 与部门范围校验']),
   ('infrastructure',40,820,380,185,['persistence / security','logging / bootstrap'])]
  specs.append(('service.role',500,1170,380,155,['RoleHierarchy 无环规则']))
  if mode=='ddd':specs.append(('service.ddd.domain',40,1170,380,155,['User / Role','目录对象行为']))
  for stem,title in [('packages-full-'+mode,'系统完整 '+label+' UML 包图')]:
   d=D(stem,title,920,1500,'UMLPackageDiagram');nodes={}
   for name,x,y,w,h,body in specs:nodes[name]=pack(d,name,x,y,w,body,h)
   for a,b,pts in [
    ('api','service.port',[(420,195),(500,195)]),
    ('api','service.auth',[(420,280),(440,280),(440,395),(690,395),(690,470)]),
    ('api','infrastructure',[(40,200),(5,200),(5,925),(40,925)]),
    ('service.security','service.port',[(880,900),(910,900),(910,240),(880,240)]),
    ('service.'+mode,'service.port',[(420,545),(460,545),(460,265),(500,265)]),
    ('service.auth','service.port',[(690,470),(690,275)]),
    ('service.'+mode,'service.security',[(230,625),(230,740),(690,740),(690,820)]),
    ('service.auth','service.security',[(690,625),(690,820)]),
    ('infrastructure','service.port',[(40,895),(15,895),(15,330),(895,330),(895,195),(880,195)])]:dep(d,nodes[a],nodes[b],pts)
   dep(d,nodes['service.'+mode],nodes['service.role'],[(405,625),(465,625),(465,1240),(500,1240)])
   dep(d,nodes['service.role'],nodes['service.port'],[(880,1210),(905,1210),(905,270),(880,270)])
   if mode=='ddd':
    dep(d,nodes['service.ddd'],nodes['service.ddd.domain'],[(230,625),(445,625),(445,1090),(230,1090),(230,1170)])
    dep(d,nodes['service.ddd.domain'],nodes['service.port'],[(40,1240),(25,1240),(25,345),(690,345),(690,275)])
   note(d,40,1380,840,['虚线开箭头指向所依赖的包。包图表达组织和依赖，类及字段关系见模块类图。'])


  # Each projection answers a different question; the full plate retains all packages.
  d=D('packages-'+mode,label+' 四层包依赖总览',920,1120,'UMLPackageDiagram')
  api=pack(d,'api',40,130,380,['HTTP 资源输入与响应'],150)
  port=pack(d,'service.port',500,130,380,['用例与存储接口 数据契约'],150)
  business=pack(d,'service.'+mode,40,510,380,['事务脚本' if mode=='structured' else '应用服务与领域对象'],150)
  infra=pack(d,'infrastructure',500,510,380,['持久化 安全 日志 初始化'],150)
  dep(d,api,port,[(420,210),(500,210)])
  dep(d,business,port,[(230,510),(230,385),(690,385),(690,280)])
  dep(d,infra,port,[(690,510),(690,280)])
  note(d,40,840,840,['Service Port 声明输入输出契约和存储端口；业务实现与基础设施均依赖它。','service.auth 和 service.security 的完整依赖见完整包图。'])

  d=D('packages-'+mode+'-entry',label+' 输入与身份包依赖',920,1150,'UMLPackageDiagram')
  api=pack(d,'api',40,130,380,['auth identity shared','user role department system permission'],185)
  auth=pack(d,'service.auth',500,130,380,['登录 身份 界面与 API 决策','授权候选项查询'],185)
  security=pack(d,'service.security',500,490,380,['RBAC 操作许可与部门范围'],150)
  infra=pack(d,'infrastructure.security',40,490,380,['CurrentIdentity BearerFilter','SecurityConfiguration TokenConfiguration'],185)
  port=pack(d,'service.port',270,850,380,['Identity Page 数据及会话端口'],150)
  dep(d,api,auth,[(420,225),(500,225)])
  dep(d,api,infra,[(230,315),(230,490)])
  dep(d,api,port,[(40,245),(15,245),(15,930),(270,930)])
  dep(d,auth,security,[(690,315),(690,490)])
  dep(d,auth,port,[(880,230),(905,230),(905,1040),(460,1040),(460,1000)])
  dep(d,security,port,[(690,640),(690,770),(580,770),(580,850)])
  dep(d,infra,port,[(230,675),(230,770),(340,770),(340,850)])
  note(d,40,1080,840,['图示包引用方向；输入类通过 CurrentIdentity 读取认证身份。'])

  d=D('packages-'+mode+'-implementation',label+' 业务与持久化包依赖',920,1280 if mode=='ddd' else 920,'UMLPackageDiagram')
  business=pack(d,'service.'+mode,40,130,380,['五个业务 Service'],150)
  security=pack(d,'service.security',500,130,380,['操作许可 部门边界'],150)
  port=pack(d,'service.port',500,530,380,['UseCase Data ManagementStore','EffectiveAccessStore SessionStore'],185)
  persistence=pack(d,'infrastructure.persistence',40,530,380,['JdbcManagementStore','JdbcEffectiveAccessStore','JdbcSessionStore'],185)
  dep(d,business,security,[(420,205),(500,205)])
  dep(d,business,port,[(420,245),(460,245),(460,610),(500,610)])
  dep(d,security,port,[(690,280),(690,530)])
  dep(d,persistence,port,[(420,610),(500,610)])
  if mode=='ddd':
   domain=pack(d,'service.ddd.domain',40,930,380,['User Role Department System Permission','字段变化与授权集合规则'],185)
   dep(d,business,domain,[(40,230),(15,230),(15,1005),(40,1005)])
   dep(d,domain,port,[(420,1005),(905,1005),(905,620),(880,620)])
  note(d,40,1180 if mode=='ddd' else 820,840,['基础设施实现存储端口；包图以依赖表达引用，实现关系在对应类图中展开。'])


def supporting():
 overall_classes();package_diagrams()
 d=D('architecture-runtime','运行装配与依赖结构',900,1190)
 a=cls(d,'UserController',60,120,780,['create','update'])
 p=cls(d,'UserUseCase',60,350,780,['create','update'],interface=True)
 s=cls(d,'StructuredUserService',30,620,390,['create','update'])
 t=cls(d,'DddUserService',480,620,390,['create','update'])
 st=cls(d,'ManagementStore',60,900,780,['createUser','updateUser'],interface=True)
 ref(d,a,p,[(450,bottom(a)),(450,350)],'service')
 dep(d,s,p,[(225,615),(225,bottom(p)+10)],True)
 dep(d,t,p,[(675,615),(675,bottom(p)+10)],True)
 for c in [s,t]:ref(d,c,st,[(center(c),bottom(c)),(center(c),900)],'store')
 note(d,60,1090,780,['BACKEND_STRATEGY=structured 或 ddd，每个用例端口仅注册一个实现。','适配器、数据库和前端使用相同契约。'])
 selection_class();persistence();relations();common_sequences();authentication()


def selection_class():
 d=D('class-selection','共享授权候选查询 类图',1100,1220)
 svc=cls(d,'SelectionService',360,390,390,['roles','systems','permissions'])
 for i,name in enumerate(['RoleController','SystemController','PermissionController']):
  ctrl=cls(d,name,30+i*365,100,330,['options'])
  ref(d,ctrl,svc,[(195+i*365,bottom(ctrl)),(195+i*365,320),(555,320),(555,390)],'selections')
 policy=cls(d,'AccessPolicy',30,850,450,['require','allowed','scope'])
 store=cls(d,'ManagementStore',620,850,450,['roles','systems','permissions'],interface=True)
 ref(d,svc,policy,[(360,495),(255,495),(255,850)],'access')
 ref(d,svc,store,[(750,495),(845,495),(845,850)],'store')
 note(d,30,1080,1040,['三个只读候选接口共用授权任务许可；数据仍由同一 ManagementStore 分页读取。'])


def persistence():
 d=D('class-persistence','共享持久化适配器 类图',1160,1210)
 adapters=[
  ('ManagementStore',30,['user','createUser','updateUser','deleteUser']),
  ('EffectiveAccessStore',410,['rolesForUser','permissionsForUser','effectiveRolePermissions']),
  ('SessionStore',790,['save','find','revoke']),
 ]
 jdbc=cls(d,'JdbcTemplate',410,870,340,['query','queryForObject','update'])
 for name,x,methods in adapters:
  port=cls(d,name,x,120,340,methods,interface=True)
  adapter=cls(d,'Jdbc'+name,x,470,340,methods,[('jdbc','JdbcTemplate')])
  mid=x+170
  dep(d,adapter,port,[(mid,465),(mid,bottom(port)+10)],True)
  target={200:465,580:580,960:695}[mid]
  points=[(mid,bottom(adapter)+5),(mid,780),(target,780),(target,860)] if mid!=target else [(mid,bottom(adapter)+5),(target,860)]
  ref(d,adapter,jdbc,points,'jdbc')
 note(d,30,1090,1100,['目录与关联写入、有效授权读取、会话分别使用各自端口。','有效授权从当前继承边递归计算，适配器之间共用 JdbcTemplate。'])
 for stem,title,steps in [
 ('adapter-insert','JDBC 用户适配器 新建顺序图',[(0,1,'createUser(departmentId, command)'),(1,2,'INSERT app_user；GeneratedKeyHolder'),(2,1,'返回生成编号'),(1,2,'SELECT * WHERE id=?；USER RowMapper'),(2,1,'Data.User'),(1,0,'返回创建结果')]),
 ('adapter-update','JDBC 用户适配器 更新顺序图',[(0,1,'updateUser(id, profile)'),(1,2,'UPDATE app_user SET name=?,phone=?'),(2,1,'返回更新行数'),(1,0,'完成；事务由 Service 提交')]),
 ('adapter-delete','JDBC 用户关联清理与删除 顺序图',[(0,1,'deleteUser(id)'),(1,2,'DELETE login_session WHERE user_id=?'),(1,2,'DELETE user_role WHERE user_id=?'),(1,2,'DELETE app_user WHERE id=?'),(2,1,'删除完成'),(1,0,'完成；事务由 Service 提交')])]:
  seq=Sequence(stem,title,[('service','UserUseCase'),('adapter','JdbcManagementStore'),('jdbc','JdbcTemplate')],height=1120)
  for i,(a,b,text) in enumerate(steps):seq.msg(a,b,310+i*105,text,reply=(a==2 or b==0))
  seq.note(1000,'同一服务事务中执行；约束冲突回滚并映射为 409，数据库故障映射为 503。')


def relations():
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  d=D('class-relations-'+mode,'授权关系 '+('DDD' if mode=='ddd' else '结构化')+' 类图',1120,1430)
  c=cls(d,'UserController',30,120,470,['roles','assign','revoke'])
  p=cls(d,'UserUseCase',630,120,450,['roles','assign','revoke'],interface=True)
  s=cls(d,prefix+'UserService',630,470,450,['roles','assign','revoke'])
  r=cls(d,prefix+'RoleService',30,470,470,['permissions','grant','revoke'])
  st=cls(d,'ManagementStore',630,900,450,['userRoles','rolePermissions','assignRole','revokeRole','grantPermission','revokePermission'],interface=True)
  rp=cls(d,'RoleUseCase',30,900,470,['permissions','grant','revoke'],interface=True)
  ref(d,c,p,[(500,190),(630,190)],'service')
  dep(d,s,p,[(855,465),(855,bottom(p)+10)],True)
  dep(d,r,rp,[(265,bottom(r)+5),(265,890)],True)
  ref(d,s,st,[(855,bottom(s)+5),(855,890)],'store')
  ref(d,r,st,[(505,570),(565,570),(565,820),(770,820),(770,890)],'store')
  note(d,30,1240,1050,['用户角色和角色权限分别由两张 M:N 关联表保存；角色归属一个部门。',('DDD User 校验角色分配，应用服务保存直接关联。' if mode=='ddd' else '结构化服务检查权限、范围和对象存在性，数据库约束拒绝重复分配。'),'RoleController 通过 RoleUseCase 调用角色权限方法，详见角色模块类图。'])


def common_sequences():
 for stem,title,parts,messages,note in [
 ('flow-copy','API 边界对象拷贝 顺序图',[('api','UserController'),('copy','CloneFactory'),('port','UserUseCase')],[(0,1,'copy(new Data.CreateUser(...), request)'),(1,0,'返回创建输入 record'),(0,2,'create(current.get(), command)'),(2,0,'Data.User；201 Location')],'@Valid 已完成格式校验；生成的 copy 仅覆盖 @CopyTo 声明类型，拒绝未知输入字段。'),
 ('flow-adapter','持久化读取与数据映射 顺序图',[('service','UserUseCase'),('store','JdbcManagementStore'),('jdbc','JdbcTemplate')],[(0,1,'user(id)'),(1,2,'query(SELECT, USER RowMapper, id)'),(2,1,'将结果行映射为 Data.User'),(1,0,'返回共享 Data record')],'JDBC ResultSet 由 RowMapper 显式映射；CloneFactory 用于 API 请求到 Command 的对象拷贝。')]:
  seq=Sequence(stem,title,parts,height=1120)
  for i,(a,b,text) in enumerate(messages):seq.msg(a,b,320+i*140,text,reply=(b==0 or (a==2 and b==1)))
  seq.note(1000,note)

 seq=Sequence('flow-preflight','受保护请求 校验顺序图',[('filter','BearerFilter'),('decoder','JwtDecoder'),('sessions','SessionStore'),('store','ManagementStore'),('effective','EffectiveAccessStore')],height=1400)
 for i,(a,b,text,reply) in enumerate([
  (0,1,'decode(Authorization 中的令牌)',False),(1,0,'验签后的 JWT；失败抛出异常',True),
  (0,2,'find(jti)；核验用户、系统及有效期',False),(2,0,'Session 或认证失败',True),
  (0,3,'user(userId)',False),(3,0,'当前账号',True),
  (0,4,'permissionsForUser(userId, system)',False),(4,0,'当前 login 权限',True),
  (0,0,'设置 Data.Identity；chain.doFilter',False)]):seq.msg(a,b,300+i*105,text,reply=reply)
 seq.note(1280,'认证失败终止请求并返回 401；login 撤销返回 403；数据库故障返回 503。业务 Service 另查操作许可及部门。')

def authentication():
 seq=Sequence('flow-login-session','建立登录会话与签发凭据',[('auth','AuthService'),('sessions','SessionStore'),('encoder','JwtEncoder')],height=1140)
 seq.msg(0,1,320,'save(UUID, userId, systemKey, expiresAt)',end=440)
 seq.msg(1,0,460,'会话写入当前事务',reply=True)
 seq.msg(0,2,610,'encode(RS256, iss/sub/aud/jti/iat/exp)',end=760)
 seq.msg(2,0,790,'JWT accessToken',reply=True)
 seq.msg(0,0,900,'返回 LoginResult；事务提交')
 seq.note(1030,'账号及目标系统 login 权限先校验。签发失败时回滚会话写入；成功响应包含 accessToken、tokenType、expiresIn。')
 d=D('class-auth-request','Bearer 认证与实时鉴权 类图',1120,1450)
 f=cls(d,'BearerFilter',30,120,470,['doFilterInternal'],[('decoder','JwtDecoder'),('sessions','SessionStore'),('store','ManagementStore'),('effectiveAccess','EffectiveAccessStore')])
 decoder=cls(d,'JwtDecoder',630,120,450,['decode'],interface=True)
 sessions=cls(d,'SessionStore',630,470,450,['find'],interface=True)
 store=cls(d,'ManagementStore',630,775,450,['user'],interface=True)
 effective=cls(d,'EffectiveAccessStore',630,1030,450,['permissionsForUser'],interface=True)
 current=cls(d,'CurrentIdentity',30,830,470,['get'])
 context=cls(d,'SecurityContextHolder',30,1190,470,['getContext','clearContext'],stereo='framework')
 ref(d,f,decoder,[(505,215),(620,215)],'decoder')
 ref(d,f,sessions,[(505,300),(565,300),(565,550),(620,550)])
 ref(d,f,store,[(505,285),(585,285),(585,855),(620,855)])
 ref(d,f,effective,[(505,320),(550,320),(550,1120),(620,1120)])
 dep(d,f,context,[(25,250),(10,250),(10,1140),(265,1140),(265,1180)])
 dep(d,current,context,[(160,bottom(current)+5),(160,1180)])
 note(d,630,1250,450,['Filter 设置并在 finally 清理身份。','CurrentIdentity 供 Controller 读取。','后续每次请求重新检查当前权限。'])
