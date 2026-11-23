"""v3.2.2 class-chain and package layouts, updated to the executable baseline."""
import draw_spec as u
import re
from draw_complete import Sequence,wrap
from v33_catalog import MODULES
D=u.D

def cls(d,name,x,y,w=340,methods=(),attrs=(),interface=False,stereo='',static_methods=(),compact=False):
 m=d.model('UMLInterface' if interface else 'UMLClass',name=name,attributes=[],operations=[],stereotype=stereo)
 for a,t in attrs:m['attributes'].append(u.obj('UMLAttribute',m,name=a,type=t,visibility='private'))
 for a in methods:
  method=u.obj('UMLOperation',m,name=a,visibility='public',parameters=[],isStatic=a in static_methods)
  m['operations'].append(method)
 old=u.CLASSES.get(name);u.CLASSES[name]=m
 start=len(d.svg)
 result=u.klass(d,name,x,y,w,compact=compact)
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
METHODS = {
 'User':['list','roles','assign','revoke'],
 'Role':['list','create','update','delete','permissions','grant','revoke'],
 'Department':['list'], 'System':['list'],
 'Permission':['list']}
STORE={
 'User':['users','user','userRoles','assignRole','revokeRole'],
 'Role':['roles','role','createRole','renameRole','deleteRole','rolePermissions','grantPermission','revokePermission'],
 'Department':['departments','department'],
 'System':['systems'],
 'Permission':['permissions']}
REQUEST={
 'User':('ManagementDTOs.AssignRoleDTO',[('roleId','Long')]),
 'Role':('ManagementDTOs.NameDTO',[('name','String')])}
DOMAIN={
 'User':([('data','Data.User')],['requireRoleInDepartment']),
 'Role':([('data','Data.Role')],['requireBaseRole'])}

def classes():
 for module in ['User','Role','Department','System','Permission']:
  modes=[('structured','Structured'),('ddd','Ddd')] if module in DOMAIN else [('shared','')]
  for mode,prefix in modes:
   title=MODULES[module][0];label={'structured':'结构化','ddd':'DDD','shared':'共享'}[mode]
   d=D(f'class-{mode}-{module.lower()}',f'{title}模块 {label} 类图',1180,1710)
   methods=METHODS[module]
   a=cls(d,module+'Controller',30,110,330,methods)
   b=cls(d,module+'UseCase',425,110,330,methods,interface=True)
   c=cls(d,prefix+module+'Service',820,110,330,methods)
   ref(d,a,b,[(360,190),(425,190)],'service')
   dep(d,c,b,[(820,174),(755,174)],True)
   request=cls(d,REQUEST[module][0],30,500,330,attrs=REQUEST[module][1]) if module in REQUEST else None
   data=cls(d,'Data.'+module,425,500,330,attrs=FIELDS[module],stereo='record')
   store=cls(d,'ManagementStore',820,500,330,STORE[module],interface=True)
   dep(d,c,data,[(850,bottom(c)+5),(850,440),(590,440),(590,490)])
   ref(d,c,store,[(985,bottom(c)+5),(985,490)],'store')
   command_name='Data.Name' if module == 'Role' else None
   if command_name:
    command=cls(d,command_name,30,730,330,attrs=REQUEST[module][1],stereo='record')
    copy=cls(d,'CloneFactory',30,1010,330,['copy'],stereo='utility')
    dep(d,a,copy,[(30,285),(12,285),(12,1060),(25,1060)])
    dep(d,copy,command,[(195,1005),(195,bottom(command)+10)])
   policy=cls(d,'AccessPolicy',425,1010,330,['require','scope','allowed'])
   adapter=cls(d,'JpaManagementStore',820,1010,330,STORE[module])
   dep(d,adapter,store,[(985,1005),(985,bottom(store)+10)],True)
   if request:dep(d,a,request,[(195,bottom(a)+5),(195,490)])
   ref(d,c,policy,[(1150,280),(1168,280),(1168,940),(590,940),(590,1010)],'access')
   if mode=='ddd':
    attrs,ops=DOMAIN[module]
    entity=cls(d,module,425,1290,330,ops,attrs)
    y=min(1400,bottom(entity)-20)
    dep(d,c,entity,[(1150,320),(1175,320),(1175,y),(765,y)])
   else:
    note(d,30,1310,725,['Data 是共享查询结果及持久化契约。','参数及返回类型见对应功能调用表。'])
   repository=cls(d,module+'JpaRepository',820,1460,330,(['findById','findByDepartmentId'] if module=='User' else ['findAll'] if module=='Department' else ['findById','save','deleteById']),interface=True)
   ref(d,adapter,repository,[(985,bottom(adapter)+5),(985,1450)])
   d.text(590,1660,('继承操作及 RoleHierarchy 见 RBAC1 专题类图。' if module=='Role' else 'Controller 依赖端口，Service 实现端口；JPA 适配器实现 ManagementStore。'),20,True)
 domain()

def domain():
 d=D('domain','DDD 领域对象类图',1000,980)
 user=cls(d,'User',35,105,430,['requireRoleInDepartment'],DOMAIN['User'][0],stereo='aggregate root')
 role=cls(d,'Role',535,105,430,['requireBaseRole'],DOMAIN['Role'][0],stereo='aggregate root')
 ud=cls(d,'Data.User',35,360,430,attrs=FIELDS['User'],stereo='record')
 rd=cls(d,'Data.Role',535,360,430,attrs=FIELDS['Role'],stereo='record')
 graph=cls(d,'RoleHierarchy',535,650,430,['requireAcyclic'],static_methods=['requireAcyclic'])
 ref(d,user,ud,[(250,bottom(user)),(250,360)],'data')
 ref(d,role,rd,[(750,bottom(role)),(750,360)],'data')
 dep(d,user,rd,[(465,212),(500,212),(500,420),(535,420)])
 dep(d,role,graph,[(965,210),(985,210),(985,720),(965,720)])
 note(d,35,820,930,['User 校验角色所属部门。Role 校验基础角色归属并委托无环规则。','应用服务通过 EffectiveAccessStore 取得可达集合后调用领域对象。'])


def overall_classes():
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  label='结构化' if mode=='structured' else 'DDD'
  d=D('architecture-classes-'+mode,'系统总体架构类图 '+label,1200,1600)
  for x,t in [(210,'API 输入'),(600,'Service Port'),(990,'业务实现')]:
   d.text(x,84,t,24,True,True)
  y=113
  for module in ['User','Role','Department','System','Permission']:
   methods=list(METHODS[module])
   if module=='Role':methods+=['inheritedRoles','effectivePermissions','inherit','uninherit']
   controller_methods=methods+['options'] if module in ['Role','System','Permission'] else methods
   a=cls(d,module+'Controller',40,y,340,controller_methods,compact=True)
   b=cls(d,module+'UseCase',430,y,340,methods,interface=True,compact=True)
   service_type=(prefix if module in DOMAIN else '')+module+'Service'
   c=cls(d,service_type,820,y,340,methods,[('store','ManagementStore'),('access','AccessPolicy')],compact=True)
   ref(d,a,b,[(380,y+64),(430,y+64)])
   dep(d,c,b,[(820,y+64),(770,y+64)],True)
   y=max(bottom(a),bottom(b),bottom(c))+24
  cls(d,'AccessPolicy',40,y,340,['require','scope','allowed'],compact=True)
  store=cls(d,'ManagementStore',430,y,340,['users','roles','inheritRole'],interface=True,compact=True)
  adapter=cls(d,'JpaManagementStore',820,y,340,['users','roles','inheritRole'],compact=True)
  dep(d,adapter,store,[(820,y+64),(770,y+64)],True)
  y=max(bottom(store),bottom(adapter))+20
  caption='DDD 用户与角色服务使用领域对象校验规则。' if mode=='ddd' else '结构化用户与角色服务使用事务脚本编排规则。'
  note(d,40,y,1120,['Controller 的 options 方法由独立 SelectionService 提供。',caption+'三类目录、认证和存储共用。'])
  d.h=y+102
  d.diagram['ownedViews'][0]['height']=d.h


def package_diagrams():
 for mode,label in [('structured','结构化'),('ddd','DDD')]:
  layer='service.'+mode
  prefix='Structured' if mode=='structured' else 'Ddd'
  d=D('packages-full-'+mode,'系统完整 '+label+' UML 包图',2240,1860,'UMLPackageDiagram')
  for x,title in [(205,'API 输入层'),(760,'Service Port 契约层'),
                  (1330,'Service 业务实现层'),(1970,'Infrastructure 基础设施层')]:
   d.text(x,104,title,27,True,True)
  specs=[
   ('api.user',55,200,320,['UserController']),
   ('api.role',55,425,320,['RoleController']),
   ('api.department',55,686,320,['DepartmentController']),
   ('api.system',55,825,320,['SystemController']),
   ('api.permission',55,966,320,['PermissionController']),
   ('api.auth',55,1140,320,['AuthController','dto / vo']),
   ('api.identity',55,1330,320,['IdentityDepartmentController','IdentityUserController']),
   ('api.shared',55,1580,320,['ApiErrors','api.shared.dto / api.shared.vo']),
   ('service.port',565,200,405,['UserUseCase、RoleUseCase','DepartmentUseCase','SystemUseCase、PermissionUseCase','Data、BusinessException','ManagementStore、SessionStore','EffectiveAccessStore']),
   (layer,1160,200,330,[prefix+'UserService',prefix+'RoleService']),
   ('service.catalog',1160,580,330,['DepartmentService','SystemService','PermissionService']),
   ('service.auth',1160,865,330,['AuthService、SelectionService','IdentityDirectoryService','OperationPermissions','UiPermissions']),
   ('service.security',1160,1190,330,['AccessPolicy']),
   ('service.role',1160,1490,330,['RoleHierarchy']),
   ('infrastructure.persistence.adapter',1750,430,430,['JpaManagementStore','JpaEffectiveAccessStore','JpaSessionStore']),
   ('infrastructure.persistence.repository',1750,745,430,['User / Role / Department / System','Permission / UserRole / RolePermission','RoleInheritance / LoginSession']),
   ('infrastructure.persistence.entity',1750,1045,430,['九张表的持久化实体','三种复合主键']),
   ('infrastructure.security',1750,1330,430,['BearerFilter、CurrentIdentity','SecurityConfiguration','TokenConfiguration']),
   ('infrastructure.logging',1750,1600,430,['LayerLogging']),
  ]
  if mode=='ddd':
   specs.append(('service.ddd.domain',1160,365,330,['User、Role']))
  n={name:pack(d,name,x,y,w,body) for name,x,y,w,body in specs}
  annotations=[]
  def edge(a,b,points,name,at):
   d.edge(n[a],n[b],points,name=name)
   annotations.append((at,name))
  for name,y,port_y,text in [
   ('api.user',241,241,'调用 UserUseCase'),
   ('api.role',467,293,'调用 RoleUseCase'),
   ('api.department',729,331,'调用 DepartmentUseCase'),
   ('api.system',870,369,'调用 SystemUseCase'),
   ('api.permission',1009,404,'调用 PermissionUseCase')]:
   bend=479-(y-241)//11
   points=[(375,y),(565,y)] if y==port_y else [(375,y),(bend,y),(bend,port_y),(565,port_y)]
   edge(name,'service.port',points,text,(470 if y==port_y else 500,y-12))
  edge(layer,'service.port',[(1160,249),(970,249)],'实现用户与角色用例',(1063,234))
  edge('service.catalog','service.port',[(1160,632),(1002,632),(1002,349),(970,349)],
       '实现三个目录用例',(1070,616))
  edge('service.auth','service.port',[(1160,915),(995,915),(995,396),(970,396)],
       '查询权限与会话',(1070,899))
  edge('service.security','service.port',[(1160,1235),(982,1235),(982,425),(970,425)],
       '读取有效授权',(1072,1219))
  edge('api.role','api.shared',[(55,509),(28,509),(28,1618),(55,1618)],
       '读 DTO、写 VO',(140,1535))
  edge('api.auth','service.auth',[(375,1188),(530,1188),(530,1025),(1100,1025),(1100,952),(1160,952)],
       '登录与鉴权',(725,1011))
  edge('api.identity','service.auth',[(375,1377),(1060,1377),(1060,995),(1160,995)],
       '查询统一身份目录',(702,1362))
  edge('infrastructure.persistence.adapter','service.port',
       [(1750,480),(1645,480),(1645,151),(930,151),(930,182)],'实现三个存储端口',(1210,139))
  edge('infrastructure.persistence.adapter','infrastructure.persistence.repository',
       [(1950,bottom(n['infrastructure.persistence.adapter'])),(1950,727)],
       '委托 JPA 查询与写入',(2075,688))
  edge('infrastructure.persistence.repository','infrastructure.persistence.entity',
       [(1950,bottom(n['infrastructure.persistence.repository'])),(1950,1027)],
       '映射九类实体',(2048,1001))
  edge('infrastructure.security','service.port',
       [(1750,1375),(1580,1375),(1580,185),(850,185),(850,200)],
       '读取会话、账号与授权',(1160,178))
  edge('service.catalog','service.security',
       [(1430,bottom(n['service.catalog'])),(1430,785),(1557,785),(1557,1205),(1490,1205)],
       '校验目录许可',(1630,1030))
  edge('service.auth','service.security',[(1310,bottom(n['service.auth'])),(1310,1172)],
       '检查授权',(1363,1138))
  edge(layer,'service.security',[(1490,268),(1540,268),(1540,1235),(1490,1235)],
       '检查操作许可与部门',(1655,1170))
  if mode=='ddd':
   edge(layer,'service.ddd.domain',[(1320,bottom(n[layer])),(1320,347)],
        '校验领域规则',(1427,335))
   edge('service.ddd.domain','service.role',[(1490,408),(1510,408),(1510,1530),(1490,1530)],
        '校验继承无环',(1584,1533))
  else:
   edge(layer,'service.role',[(1490,302),(1510,302),(1510,1530),(1490,1530)],
        '校验继承无环',(1584,1533))
  for at,name in annotations:
   d.text(*at,name,16,True,bg=True)
  note(d,55,1755,2100,['虚线开箭头从引用方指向被引用的包。「实现」描述包内类实现接口，详细的 UML 实现关系见对应类图。','LayerLogging 通过 Spring AOP 匹配入口、业务和持久化方法，源码通过切点配置关联。'])

  d=D('packages-'+mode,label+' 四层包依赖总览',1640,545,'UMLPackageDiagram')
  api=pack(d,'api',45,195,265,['HTTP 入口、DTO 与 VO'])
  port=pack(d,'service.port',420,195,315,['用例、共享数据及仓储端口'])
  business=pack(d,layer,860,195,295,['用户和角色事务脚本' if mode=='structured' else '应用服务与领域对象'])
  infra=pack(d,'infrastructure',1280,195,315,['安全、JPA 持久化与日志'])
  d.edge(api,port,[(310,247),(420,247)],name='调用用例')
  d.edge(business,port,[(860,247),(735,247)],name='实现用例')
  d.edge(infra,port,[(1410,bottom(infra)),(1410,382),(635,382),(635,bottom(port))],
         name='实现存储端口')
  for x,y,label_text in [(365,233,'调用用例'),(797,233,'实现用例'),(1000,367,'实现存储端口')]:
   d.text(x,y,label_text,17,True)
  note(d,45,445,1550,['虚线开箭头指向被依赖的包。共享认证、目录和安全规则见完整包图。'])

  d=D('packages-'+mode+'-entry',label+' 输入与身份包依赖',920,1150,'UMLPackageDiagram')
  api=pack(d,'api',40,130,380,['auth identity shared','user role department system permission'],185)
  auth=pack(d,'service.auth',500,130,380,['登录 身份 界面与 API 决策','授权候选项查询'],185)
  security=pack(d,'service.security',500,490,380,['RBAC 操作许可与部门范围'],150)
  infra=pack(d,'infrastructure.security',40,490,380,['CurrentIdentity BearerFilter','SecurityConfiguration TokenConfiguration'],185)
  port=pack(d,'service.port',270,850,380,['Identity Page 数据及会话端口'],150)
  d.edge(api,auth,[(420,225),(500,225)],name='调用认证与目录')
  d.edge(api,infra,[(230,315),(230,490)],name='读取当前身份')
  d.edge(api,port,[(40,245),(15,245),(15,930),(270,930)],name='调用应用用例')
  d.edge(auth,security,[(690,315),(690,490)],name='检查当前许可')
  d.edge(auth,port,[(880,230),(905,230),(905,1040),(460,1040),(460,1000)],name='读取数据与会话')
  d.edge(security,port,[(690,640),(690,770),(580,770),(580,850)],name='读取有效授权')
  d.edge(infra,port,[(230,675),(230,770),(340,770),(340,850)],name='核验令牌会话')
  for x,y,name in [(460,210,'调用认证与目录'),(155,388,'读取当前身份'),
                   (135,915,'调用应用用例'),(760,385,'检查当前许可'),
                   (690,1026,'读取数据与会话'),(752,756,'读取有效授权'),
                   (268,755,'核验令牌会话')]:
   d.text(x,y,name,15,True)
  note(d,40,1080,840,['图示包引用方向；输入类通过 CurrentIdentity 读取认证身份。'])

  d=D('packages-'+mode+'-implementation',label+' 业务与持久化包依赖',920,1280 if mode=='ddd' else 920,'UMLPackageDiagram')
  business=pack(d,'service.'+mode,40,130,380,['用户与角色业务 Service'],150)
  security=pack(d,'service.security',500,130,380,['操作许可 部门边界'],150)
  port=pack(d,'service.port',500,530,380,['UseCase Data ManagementStore','EffectiveAccessStore SessionStore'],185)
  persistence=pack(d,'infrastructure.persistence.adapter',40,530,380,['JpaManagementStore','JpaEffectiveAccessStore','JpaSessionStore'],185)
  d.edge(business,security,[(420,205),(500,205)],name='检查权限与部门')
  d.edge(business,port,[(420,245),(460,245),(460,610),(500,610)],name='调用存储端口')
  d.edge(security,port,[(690,280),(690,530)],name='读取有效权限')
  d.edge(persistence,port,[(420,610),(500,610)],name='实现存储端口')
  if mode=='ddd':
   domain=pack(d,'service.ddd.domain',40,930,380,['User Role','角色分配与继承规则'],185)
   d.edge(business,domain,[(40,230),(15,230),(15,1005),(40,1005)],name='委托领域规则')
   d.edge(domain,port,[(420,1005),(905,1005),(905,620),(880,620)],name='使用共享数据')
   d.text(132,904,'委托领域规则',16,True)
   d.text(663,991,'使用共享数据',16,True)
  for x,y,name in [(460,188,'检查权限与部门'),(370,464,'调用存储端口'),
                   (755,406,'读取有效权限'),(460,594,'实现存储端口')]:
   d.text(x,y,name,15,True)
  note(d,40,1180 if mode=='ddd' else 820,840,['基础设施实现存储端口；包图以依赖表达引用，实现关系在对应类图中展开。'])


def supporting():
 overall_classes();package_diagrams()
 d=D('architecture-runtime','运行装配与依赖结构',900,1190)
 a=cls(d,'UserController',60,120,780,['assign','revoke'])
 p=cls(d,'UserUseCase',60,350,780,['assign','revoke'],interface=True)
 s=cls(d,'StructuredUserService',30,620,390,['assign','revoke'])
 t=cls(d,'DddUserService',480,620,390,['assign','revoke'])
 st=cls(d,'ManagementStore',60,900,780,['assignRole','revokeRole'],interface=True)
 ref(d,a,p,[(450,bottom(a)),(450,350)],'service')
 dep(d,s,p,[(225,615),(225,bottom(p)+10)],True)
 dep(d,t,p,[(675,615),(675,bottom(p)+10)],True)
 for c in [s,t]:ref(d,c,st,[(center(c),bottom(c)),(center(c),900)],'store')
 note(d,60,1090,780,['BACKEND_STRATEGY=structured 或 ddd，选择用户和角色用例实现。','适配器、数据库和前端使用相同契约。'])
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
 d=D('class-persistence','共享持久化适配器 类图',1160,1030)
 adapters=[
  ('ManagementStore',30,['createRole','renameRole','deleteRole'],
   'RoleJpaRepository',['findById','save','deleteById']),
  ('EffectiveAccessStore',410,['reachableRoles','rolesForUser','effectiveRolePermissions'],
   'RoleInheritanceJpaRepository',['juniorIds']),
  ('SessionStore',790,['save','find','revoke'],
   'LoginSessionJpaRepository',['findById','save','revoke']),
 ]
 for name,x,methods,repository_name,repositories in adapters:
  port=cls(d,name,x,110,340,methods,interface=True)
  adapter=cls(d,'Jpa'+name,x,380,340,methods)
  repository=cls(d,repository_name,x,680,340,repositories,interface=True)
  mid=x+170
  dep(d,adapter,port,[(mid,375),(mid,bottom(port))],True)
  ref(d,adapter,repository,[(mid,bottom(adapter)),(mid,680)])
 note(d,30,925,1100,['三个适配器委托给 Spring Data JPA 仓储。有效授权查询逐层读取基础角色。'])
 for stem,title,steps in [
 ('adapter-insert','JPA 角色适配器 新建顺序图',[
  (0,1,'createRole(departmentId, name)'),(1,2,'save(new RoleEntity(...))'),
  (2,1,'带生成编号的 RoleEntity'),(1,0,'返回 Data.Role')]),
 ('adapter-update','JPA 角色适配器 更新顺序图',[
  (0,1,'renameRole(id, name)'),(1,2,'findById(id)'),(2,1,'事务内受管 RoleEntity'),
  (1,1,'role.rename(name)'),(1,0,'返回；提交时同步实体')]),
 ('adapter-delete','JPA 角色适配器 删除顺序图',[
  (0,1,'deleteRole(id)'),(1,2,'deleteById(id)'),(2,1,'当前事务记录删除'),
  (1,0,'返回；提交时数据库校验引用')])]:
  seq=Sequence(stem,title,[('service','RoleUseCase'),('adapter','JpaManagementStore'),('roles','RoleJpaRepository')],height=195+(len(steps)-1)*81+138)
  for i,(a,b,text) in enumerate(steps):
   y=195+i*81
   seq.msg(a,b,y,text,end=195+(len(steps)-1)*81 if i==0 else y+39,reply=(a==2 or b==0))
  seq.note(y+58,'写入在服务事务内执行。外键或唯一约束冲突返回 409。')


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
 ('flow-copy','API 边界对象拷贝 顺序图',[('api','RoleController'),('copy','CloneFactory'),('port','RoleUseCase')],[(0,1,'copy(new Data.Name(null), request)'),(1,0,'返回 Data.Name'),(0,2,'create(current.get(), command)'),(2,0,'Data.Role；201 Location')],'@Valid 已完成格式校验；生成的 copy 仅覆盖 @CopyTo 声明类型，拒绝未知输入字段。'),
 ('flow-adapter','持久化读取与数据映射 顺序图',[('service','UserUseCase'),('store','JpaManagementStore'),('users','UserJpaRepository')],[(0,1,'user(id)'),(1,2,'findById(id)'),(2,1,'返回 UserEntity 或空结果'),(1,0,'返回 Data.User 或对象缺失错误')],'JPA 负责实体和表的映射，适配器把持久化实体转换为共享 Data。')]:
  seq=Sequence(stem,title,parts,height=608)
  for i,(a,b,text) in enumerate(messages):seq.msg(a,b,195+i*89,text,reply=(b==0 or (a==2 and b==1)))
  seq.note(525,note)

 seq=Sequence('flow-preflight','受保护请求 校验顺序图',[('filter','BearerFilter'),('decoder','JwtDecoder'),('sessions','SessionStore'),('store','ManagementStore'),('effective','EffectiveAccessStore')],height=1018)
 for i,(a,b,text,reply) in enumerate([
  (0,1,'decode(Authorization 中的令牌)',False),(1,0,'验签后的 JWT；失败抛出异常',True),
  (0,2,'find(jti)；核验用户及有效期',False),(2,0,'Session 或认证失败',True),
  (0,3,'user(userId)',False),(3,0,'当前账号',True),
  (0,4,'permissionsForUser(userId, system)',False),(4,0,'当前 login 权限',True),
  (0,0,'设置 Data.Identity；chain.doFilter',False)]):seq.msg(a,b,195+i*82,text,reply=reply)
 seq.note(927,'认证失败终止请求并返回 401；login 撤销返回 403；数据库故障返回 503。业务 Service 另查操作许可及部门。')

def authentication():
 seq=Sequence('flow-login-session','建立登录会话与签发凭据',[('auth','AuthService'),('sessions','SessionStore'),('encoder','JwtEncoder')],height=675)
 seq.msg(0,1,195,'save(UUID, userId, expiresAt)',end=249)
 seq.msg(1,0,251,'会话写入当前事务',reply=True)
 seq.msg(0,2,329,'encode(RS256, iss/sub/aud/jti/iat/exp)',end=393)
 seq.msg(2,0,398,'JWT accessToken',reply=True)
 seq.msg(0,0,486,'返回 LoginResult；事务提交')
 seq.note(581,'账号及目标系统 login 权限先校验。签发失败时回滚会话写入；成功响应包含 accessToken、tokenType、expiresIn。')
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
