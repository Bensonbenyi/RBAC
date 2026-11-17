"""v3.2.2 class-chain and package layouts, updated to the executable baseline."""
import draw_spec as u
import re
from draw_complete import Sequence,wrap
from v33_catalog import MODULES
D=u.D

def cls(d,name,x,y,w=340,methods=(),attrs=(),interface=False,stereo=''):
 m=d.model('UMLInterface' if interface else 'UMLClass',name=name,attributes=[],operations=[],stereotype=stereo)
 for a,t in attrs:m['attributes'].append(u.obj('UMLAttribute',m,name=a,type=t,visibility='private'))
 for a in methods:
  method=u.obj('UMLOperation',m,name=a,visibility='public',parameters=[])
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
 'User':['users','user','createUser','updateUser','deleteUser'],
 'Role':['roles','role','createRole','renameRole','deleteRole'],
 'Department':['departments','department','createDepartment','renameDepartment','deleteDepartment'],
 'System':['systems','system','createSystem','renameSystem','deleteSystem'],
 'Permission':['permissions','permission','createPermission','renamePermission','deletePermission']}
REQUEST={
 'User':('Requests.CreateUser',[('username','String'),('name','String'),('phone','String')]),
 'Role':('Requests.Name',[('name','String')]),
 'Department':('Requests.CreateDepartment',[('name','String'),('parentId','Long')]),
 'System':('Requests.CreateSystem',[('systemKey','String'),('name','String')]),
 'Permission':('Requests.CreatePermission',[('code','String'),('name','String')])}
DOMAIN={
 'User':([('data','Data.User'),('profile','Data.Profile'),('roleIds','Set<Long>')],['changeProfile','assignRole','revokeRole','profile','roleIds']),
 'Role':([('name','String'),('permissionIds','Set<Long>')],['rename','grantPermission','revokePermission','permissionIds']),
 'Department':([('name','String')],['rename','name']),
 'System':([('name','String')],['rename','name']),
 'Permission':([('name','String')],['rename','name'])}

def classes():
 for module in ['User','Role','Department','System','Permission']:
  for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
   title=MODULES[module][0];label='结构化' if mode=='structured' else 'DDD'
   d=D(f'class-{mode}-{module.lower()}',f'{title}模块 {label} 类图',1180,1710)
   methods=['list','get','create','update','delete'] if module in ['User','Role'] else ['list','create','update','delete']
   a=cls(d,module+'Controller',30,110,330,methods)
   b=cls(d,module+'UseCase',425,110,330,methods,interface=True)
   c=cls(d,prefix+module+'Service',820,110,330,methods)
   dep(d,a,b,[(365,190),(415,190)]);d.text(391,175,'useCase',17,True,bg=True)
   dep(d,c,b,[(815,245),(765,245)],True)
   request=cls(d,*((REQUEST[module][0],30,500,330)),attrs=REQUEST[module][1])
   data=cls(d,'Data.'+module,425,500,330,attrs=FIELDS[module],stereo='record')
   store=cls(d,'ManagementStore',820,500,330,STORE[module],interface=True)
   dep(d,c,data,[(850,bottom(c)+5),(850,440),(590,440),(590,490)])
   ref(d,c,store,[(985,bottom(c)+5),(985,490)],'store')
   copy=cls(d,'CloneFactory',30,1010,330,['copy'],stereo='utility')
   policy=cls(d,'AccessPolicy',425,1010,330,['require','scope','allowed'])
   adapter=cls(d,'JdbcManagementStore',820,1010,330,STORE[module])
   dep(d,adapter,store,[(985,1005),(985,bottom(store)+10)],True)
   dep(d,a,copy,[(30,285),(12,285),(12,1060),(25,1060)])
   dep(d,a,request,[(195,bottom(a)+5),(195,490)])
   dep(d,copy,data,[(195,bottom(copy)+5),(195,1220),(790,1220),(790,785),(590,785),(590,bottom(data)+10)])
   dep(d,c,policy,[(1155,280),(1168,280),(1168,940),(590,940),(590,1000)])
   if mode=='ddd':
    attrs,ops=DOMAIN[module]
    entity=cls(d,module,425,1290,330,ops,attrs)
    dep(d,c,entity,[(1155,320),(1175,320),(1175,1250),(590,1250),(590,1280)])
   else:
    note(d,30,1310,725,['Service 在事务内编排权限、范围和存储调用。','Data 同时作为共享查询结果和持久化数据契约。','方法在本图省略参数及返回类型，契约见对应功能。'])
   jdbc=cls(d,'JdbcTemplate',820,1460,330,['query','queryForObject','update'])
   ref(d,adapter,jdbc,[(985,bottom(adapter)+5),(985,1450)],'jdbc')
   d.text(590,1660,'Controller 依赖端口，Service 实现端口；JDBC 适配器实现 ManagementStore。',20,True)
 domain()

def domain():
 d=D('domain','DDD 领域对象完整类图',1120,1600)
 user=cls(d,'User',40,120,470,DOMAIN['User'][1],DOMAIN['User'][0],stereo='aggregate root')
 ud=cls(d,'Data.User',630,120,450,attrs=FIELDS['User'],stereo='record')
 profile=cls(d,'Data.Profile',630,490,450,attrs=[('name','String'),('phone','String')],stereo='record')
 ref(d,user,ud,[(515,225),(620,225)],'data')
 ref(d,user,profile,[(515,345),(560,345),(560,570),(620,570)],'profile')
 role=cls(d,'Role',40,720,470,DOMAIN['Role'][1],DOMAIN['Role'][0],stereo='aggregate root')
 rd=cls(d,'Data.Role',630,830,450,attrs=FIELDS['Role'],stereo='record')
 dep(d,user,rd,[(275,bottom(user)+5),(275,650),(855,650),(855,820)])
 d.text(455,625,'assignRole(role, actorDepartment)',18,True,bg=True)
 dep(d,role,rd,[(515,835),(620,835)])
 for i,module in enumerate(['Department','System','Permission']):
  attrs,ops=DOMAIN[module]
  cls(d,module,40+i*365,1210,330,ops,attrs,stereo='entity')
 note(d,630,1025,450,['角色通过 permissionIds 维护权限集合。','Data 保存编号、归属等只读契约。','跨聚合的对象范围由应用服务检查。'])
 d.text(560,1540,'五类领域对象均按当前源码列出实际字段与行为；业务实体关系另见陈氏图及核心设计模型。',19,True)


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
   dep(d,a,b,[(385,y+80),(420,y+80)])
   dep(d,c,b,[(815,y+120),(780,y+120)],True);svcs.append(c)
  policy=cls(d,'AccessPolicy',40,1320,340,['require','scope','allowed'])
  store=cls(d,'ManagementStore',430,1320,340,['users','roles','permissionsForUser'],interface=True)
  adapter=cls(d,'JdbcManagementStore',820,1320,340,['users','roles','permissionsForUser'])
  dep(d,adapter,store,[(815,1400),(780,1400)],True)
  # Shared dependencies are routed in the gutters, once for the service group.
  for c in svcs:dep(d,c,store,[(1165,c[1]['top']+65),(1180,c[1]['top']+65),(1180,1260),(600,1260),(600,1310)])
  dep(d,svcs[-1],policy,[(990,bottom(svcs[-1])+5),(990,1215),(210,1215),(210,1310)])
  note(d,40,1540,1120,['每个用例端口按配置装配一套实现。共用认证服务、数据契约及存储适配器。',('DDD 应用服务进一步依赖五类领域对象，详情见各模块类图。' if mode=='ddd' else '事务脚本通过 AccessPolicy 检查操作许可及部门范围，再调用 ManagementStore。')])


def package_diagrams():
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  label='结构化' if mode=='structured' else 'DDD'
  d=D('packages-full-'+mode,'系统完整 '+label+' UML 包图',1740,1750,'UMLPackageDiagram')
  for x,t in [(215,'API 输入层'),(635,'契约层'),(1055,'业务层'),(1510,'基础设施层')]:d.text(x,105,t,25,True,True)
  api_specs=[('api.user',['UserController']),('api.role',['RoleController']),('api.department',['DepartmentController']),('api.system',['SystemController']),('api.permission',['PermissionController'])]
  services=[]
  for i,(name,body) in enumerate(api_specs):
   y=180+i*210;module=['User','Role','Department','System','Permission'][i]
   a=pack(d,name,30,y,370,body,130)
   b=pack(d,'service.port',450,y,370,[module+'UseCase','Data.'+module],130)
   c=pack(d,'service.'+mode,870,y,370,[prefix+module+'Service'],130)
   dep(d,a,b,[(405,y+70),(440,y+70)])
   dep(d,c,b,[(865,y+105),(830,y+105)])
   services.append(c)
  auth=pack(d,'api.auth / api.identity',30,1280,370,['AuthController','IdentityUserController'],160)
  shared=pack(d,'api.shared',30,1510,370,['Requests / ApiErrors'],130)
  authservice=pack(d,'service.auth',450,1280,370,['AuthService'],160)
  ports=pack(d,'service.port',450,1510,370,['ManagementStore / SessionStore','Data / BusinessException'],130)
  security=pack(d,'service.security',870,1280,370,['AccessPolicy'],160)
  domainbox=pack(d,'service.ddd.domain' if mode=='ddd' else 'service.structured',870,1510,370,['User / Role / Department' if mode=='ddd' else '五个事务脚本服务','System / Permission' if mode=='ddd' else '以共享 Data 编排业务'],130)
  infrastructure=[]
  for name,y,body in [
   ('infrastructure.persistence',180,['JdbcManagementStore','JdbcSessionStore','JdbcTemplate / RowMapper']),
   ('infrastructure.security',520,['BearerFilter / CurrentIdentity','TokenConfiguration','SecurityConfiguration']),
   ('infrastructure.logging',930,['LayerLogging','SLF4J / Logback']),
   ('infrastructure.bootstrap',1280,['DemoData']),
   ('resources',1510,['application.yaml','logback-spring.xml','db/migration/V1__rbac.sql'])]:infrastructure.append(pack(d,name,1320,y,390,body,150))
  dep(d,auth,authservice,[(405,1360),(440,1360)])
  dep(d,authservice,ports,[(635,1445),(635,1500)])
  dep(d,infrastructure[0],ports,[(1315,265),(1280,265),(1280,1480),(830,1480),(830,1570)])
  dep(d,services[-1],security,[(1055,bottom(services[-1])+5),(1055,1270)])
  if mode=='ddd':dep(d,services[-1],domainbox,[(1245,1100),(1260,1100),(1260,1470),(1055,1470),(1055,1500)])
  d.text(870,1725,'包内列出实际类；同一真实包按职责重复呈现，均对应相同 Java 包。虚线箭头指向依赖。',20,True)
  # A simpler strategy-specific layer plate accompanies the full inventory.
  q=D('packages-'+mode,label+' 分层包图',900,1110,'UMLPackageDiagram')
  a=pack(q,'api',60,130,780,['auth / identity / user / role / department / system / permission','shared：Requests / ApiErrors'],140)
  p=pack(q,'service.port',60,370,780,['五个 UseCase / Data','ManagementStore / SessionStore / BusinessException'],140)
  s=pack(q,'service.'+mode,60,640,360,[prefix+'UserService / '+prefix+'RoleService','其他目录 Service'],180)
  n=pack(q,'infrastructure',480,640,360,['JDBC / Bearer / Logback','bootstrap / 配置'],180)
  dep(q,a,p,[(450,bottom(a)+5),(450,360)])
  dep(q,s,p,[(240,635),(240,bottom(p)+10)])
  dep(q,n,p,[(660,635),(660,bottom(p)+10)])
  note(q,60,925,780,['共享 API 与端口保持相同；通过 app.backend-strategy 在启动时选用服务。','DDD 服务使用 service.ddd.domain；结构化服务执行事务脚本。'])
  for part in ['entry','implementation']:
   q=D('packages-'+mode+'-'+part,label+' 包图 '+('入口与用例契约' if part=='entry' else '服务与基础设施'),900,1510,'UMLPackageDiagram')
   for i,module in enumerate(['User','Role','Department','System','Permission']):
    y=130+i*235
    if part=='entry':
     a=pack(q,'api.'+module.lower(),30,y,380,[module+'Controller'],145)
     b=pack(q,'service.port',490,y,380,[module+'UseCase','Data.'+module],145)
     dep(q,a,b,[(415,y+80),(480,y+80)])
    else:
     a=pack(q,'service.'+mode,30,y,380,[prefix+module+'Service'],145)
     b=pack(q,'service.port',490,y,380,[module+'UseCase','ManagementStore'],145)
     dep(q,a,b,[(415,y+80),(480,y+80)])
   if part=='entry':note(q,30,1360,840,['请求 DTO 为 api.shared.Requests；CloneFactory 生成到 Data 输入契约的拷贝。','AuthController、IdentityUserController 直接调用共享 AuthService。'])
   else:note(q,30,1360,840,['ManagementStore 由 JdbcManagementStore 实现；存储与安全代码共用。','领域对象见完整 DDD 类图；配置及各基础设施包见完整包图。'])


def supporting():
 overall_classes();package_diagrams()
 d=D('architecture-runtime','运行装配与依赖结构',900,1190)
 a=cls(d,'UserController',60,120,780,['create','update'])
 p=cls(d,'UserUseCase',60,350,780,['create','update'],interface=True)
 s=cls(d,'StructuredUserService',30,620,390,['create','update'])
 t=cls(d,'DddUserService',480,620,390,['create','update'])
 st=cls(d,'ManagementStore',60,900,780,['createUser','updateUser'],interface=True)
 dep(d,a,p,[(450,bottom(a)+5),(450,340)])
 dep(d,s,p,[(225,615),(225,bottom(p)+10)],True)
 dep(d,t,p,[(675,615),(675,bottom(p)+10)],True)
 for c in [s,t]:dep(d,c,st,[(center(c),bottom(c)+5),(center(c),890)])
 note(d,60,1090,780,['BACKEND_STRATEGY=structured 或 ddd，每个用例端口仅注册一个实现。','适配器、数据库和前端使用相同契约。'])
 persistence();relations();common_sequences();authentication()


def persistence():
 d=D('class-persistence','共享持久化适配器 类图',1120,1410)
 m=cls(d,'ManagementStore',30,120,490,['user','createUser','updateUser','deleteUser','permissionsForUser'],interface=True)
 s=cls(d,'SessionStore',630,120,450,['save','find','revoke'],interface=True)
 a=cls(d,'JdbcManagementStore',30,610,490,['user','createUser','updateUser','deleteUser','permissionsForUser'],[('jdbc','JdbcTemplate')])
 b=cls(d,'JdbcSessionStore',630,610,450,['save','find','revoke'],[('jdbc','JdbcTemplate')])
 dep(d,a,m,[(275,605),(275,bottom(m)+10)],True);dep(d,b,s,[(855,605),(855,bottom(s)+10)],True)
 jdbc=cls(d,'JdbcTemplate',330,1120,460,['query','queryForObject','update'])
 ref(d,a,jdbc,[(275,bottom(a)+5),(275,1070),(440,1070),(440,1110)],'jdbc')
 ref(d,b,jdbc,[(855,bottom(b)+5),(855,1070),(680,1070),(680,1110)],'jdbc')
 note(d,30,930,490,['RowMapper 映射 ResultSet 到 Data record。','SQL 及 JDBC 类型保留在基础设施。'])
 d.text(560,1370,'Service 的 @Transactional 边界通过 Spring 事务管理器管理这些 JDBC 操作。',20,True)
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
  dep(d,c,p,[(505,190),(620,190)])
  dep(d,s,p,[(855,465),(855,bottom(p)+10)],True)
  dep(d,r,rp,[(265,bottom(r)+5),(265,890)],True)
  ref(d,s,st,[(855,bottom(s)+5),(855,890)],'store')
  ref(d,r,st,[(505,570),(565,570),(565,820),(770,820),(770,890)],'store')
  note(d,30,1240,1050,['用户角色和角色权限分别由两张 M:N 关联表保存；角色归属一个部门。',('DDD User 与 Role 分别维护 roleIds、permissionIds，应用服务保存关系变更。' if mode=='ddd' else '结构化服务在事务内检查权限、范围、对象存在性和关系后调用存储端口。'),'RoleController 通过 RoleUseCase 调用角色权限方法，详见角色模块类图。'])


def common_sequences():
 for stem,title,parts,messages,note in [
 ('flow-copy','API 边界对象拷贝 顺序图',[('api','UserController'),('copy','CloneFactory'),('port','UserUseCase')],[(0,1,'copy(new Data.CreateUser(...), request)'),(1,0,'返回创建输入 record'),(0,2,'create(current.get(), command)'),(2,0,'Data.User；201 Location')],'@Valid 已完成格式校验；生成的 copy 仅覆盖 @CopyTo 声明类型，拒绝未知输入字段。'),
 ('flow-adapter','持久化读取与数据映射 顺序图',[('service','UserUseCase'),('store','JdbcManagementStore'),('jdbc','JdbcTemplate')],[(0,1,'user(id)'),(1,2,'query(SELECT, USER RowMapper, id)'),(2,1,'将结果行映射为 Data.User'),(1,0,'返回共享 Data record')],'JDBC ResultSet 由 RowMapper 显式映射；CloneFactory 用于 API 请求到 Command 的对象拷贝。')]:
  seq=Sequence(stem,title,parts,height=1120)
  for i,(a,b,text) in enumerate(messages):seq.msg(a,b,320+i*140,text,reply=(b==0 or (a==2 and b==1)))
  seq.note(1000,note)

 seq=Sequence('flow-preflight','受保护请求 校验顺序图',[('filter','BearerFilter'),('decoder','JwtDecoder'),('sessions','SessionStore'),('store','ManagementStore')],height=1230)
 for i,(a,b,text,reply) in enumerate([
  (0,1,'decode(Authorization 中的令牌)',False),(1,0,'验签后的 JWT；失败抛出异常',True),
  (0,2,'find(jti)；核验用户、系统及有效期',False),(2,0,'Session 或认证失败',True),
  (0,3,'user(userId)；permissionsForUser(userId, system)',False),(3,0,'账号与当前 login 权限',True),
  (0,0,'设置 Data.Identity；chain.doFilter',False)]):seq.msg(a,b,300+i*105,text,reply=reply)
 seq.note(1110,'认证失败终止请求并返回 401；login 撤销返回 403；数据库故障返回 503。业务 Service 另查操作许可及部门。')

def authentication():
 seq=Sequence('flow-login-session','建立登录会话与签发凭据',[('auth','AuthService'),('sessions','SessionStore'),('encoder','JwtEncoder')],height=1140)
 seq.msg(0,1,320,'save(UUID, userId, systemKey, expiresAt)',end=440)
 seq.msg(1,0,460,'会话写入当前事务',reply=True)
 seq.msg(0,2,610,'encode(RS256, iss/sub/aud/jti/iat/exp)',end=760)
 seq.msg(2,0,790,'JWT accessToken',reply=True)
 seq.msg(0,0,900,'返回 LoginResult；事务提交')
 seq.note(1030,'账号及目标系统 login 权限先校验。签发失败时回滚会话写入；成功响应包含 accessToken、tokenType、expiresIn。')
 d=D('class-auth-request','Bearer 认证与实时鉴权 类图',1120,1450)
 f=cls(d,'BearerFilter',30,120,470,['doFilterInternal'],[('decoder','JwtDecoder'),('sessions','SessionStore'),('store','ManagementStore')])
 decoder=cls(d,'JwtDecoder',630,120,450,['decode'],interface=True)
 sessions=cls(d,'SessionStore',630,470,450,['find'],interface=True)
 store=cls(d,'ManagementStore',630,830,450,['user','permissionsForUser'],interface=True)
 current=cls(d,'CurrentIdentity',30,830,470,['get'])
 context=cls(d,'SecurityContextHolder',30,1190,470,['getContext','clearContext'],stereo='framework')
 ref(d,f,decoder,[(505,215),(620,215)],'decoder')
 ref(d,f,sessions,[(505,300),(565,300),(565,550),(620,550)],'sessions')
 ref(d,f,store,[(505,350),(585,350),(585,910),(620,910)],'store')
 dep(d,f,context,[(25,250),(10,250),(10,1140),(265,1140),(265,1180)])
 dep(d,current,context,[(160,bottom(current)+5),(160,1180)])
 note(d,630,1180,450,['Filter 设置并在 finally 清理身份。','CurrentIdentity 供 Controller 读取。','后续每次请求重新检查当前权限。'])
