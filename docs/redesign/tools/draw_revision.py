"""Hand positioned class chains and package dependencies, reviewed against teaching examples."""
import draw_spec as u

def generate(c):
 D=c.D; K=c.compact_class
 targets={f'class-{m.key}-{mode}' for m in c.MODULES for mode in ['structured','ddd']}|{'domain-identity','domain-catalog','packages-full-structured','packages-full-ddd'}
 old=[d for d in u.DIAGRAMS if d.stem in targets]
 u.DIAGRAMS[:]=[d for d in u.DIAGRAMS if d.stem not in targets]
 ids={d.owner['_id'] for d in old};u.MODEL['ownedElements'][:]=[m for m in u.MODEL['ownedElements'] if m['_id'] not in ids]
 def cls(d,name,x,y,w=330,methods=None,attrs=True):return K(d,name,x,y,w,methods=methods,attrs=attrs,short=True)
 def bottom(a):return a[1]['top']+a[1]['height']
 def cx(a):return a[1]['left']+a[1]['width']/2
 def dep(d,a,b,pts,label='',real=False):
  d.edge(a,b,pts,real,name=label)
 def label(d,x,y,text):d.text(x,y,text,21,True,bg=True)
 for m in c.MODULES:
  for mode in ['structured','ddd']:
   prefix='Ddd' if mode=='ddd' else 'Structured'; service=prefix+m.domain+'Service'
   adapter='Jpa'+m.domain+'Store'; repo=m.domain+'JpaRepository'; po=m.domain+'PO'; result=m.domain+'Result'
   u.classifier(po);u.classifier(result)
   u.classifier(m.domain+'Data')
   for name in [po,result,m.domain+'Data']:
    model=u.CLASSES[name]
    if not model['attributes']:
     for a in u.CLASSES[m.domain]['attributes']:
      if a['name'] in ['roleIds','permissionIds']:continue
      model['attributes'].append(u.obj('UMLAttribute',model,name=a['name'],type=a['type'],visibility='private'))
   c.op(repo,'findById','id: Long',po);c.op(repo,'save','po: '+po,po);c.op(repo,'deleteById','id: Long')
   # Three columns preserve the teaching example's Controller -> Port -> Service order.
   d=D(f'class-{m.key}-{mode}',f'{m.title}模块 '+('DDD' if mode=='ddd' else '结构化')+' 类图',1180,1750)
   ctrl=cls(d,m.controller,30,110,methods=['list','get','create','update','delete'],attrs=False)
   port=cls(d,m.port,425,110,methods=['list','get','create','update','delete'],attrs=False)
   svc=cls(d,service,820,110,methods=['list','get','create','update','delete']+(['validateChange'] if mode=='structured' else []),attrs=False)
   dep(d,ctrl,port,[(360,220),(425,220)],'useCase');label(d,392,200,'useCase')
   dep(d,svc,port,[(820,280),(755,280)],real=True)
   store=cls(d,m.store,820,540,methods=['loadForChange','query','insert','update','delete'],attrs=False)
   u.assoc(d,svc,store,[(985,bottom(svc)),(985,540)],role='store')
   res=cls(d,result,30,540,methods=[],attrs=True)
   if mode=='ddd':
    dm=cls(d,m.domain,425,540,methods=([m.domain,'changeProfile','assignRole','revokeRole'] if m.key=='user' else [m.domain,'change','requireDeletable','grantPermission','revokePermission']))
    dep(d,svc,dm,[(820,330),(790,330),(790,495),(590,495),(590,540)])
   else:
    data=cls(d,m.domain+'Data',425,540,methods=[],attrs=True)
    dep(d,svc,data,[(820,330),(790,330),(790,495),(590,495),(590,540)])
   # The result and copy helper sit below the input side, adapter below repository port.
   dep(d,svc,res,[(875,bottom(svc)),(875,455),(195,455),(195,540)])
   adapter_box=cls(d,adapter,820,1100,methods=['insert','update','delete'],attrs=False)
   dep(d,adapter_box,store,[(985,1100),(985,bottom(store))],real=True)
   entity=cls(d,po,425,1100,methods=[])
   factory=cls(d,'CloneFactory',30,1100,methods=['copy'],attrs=False)
   dep(d,adapter_box,entity,[(820,1190),(755,1190)])
   dep(d,adapter_box,factory,[(850,bottom(adapter_box)),(850,1410),(195,1410),(195,bottom(factory))])
   jpa=cls(d,repo,820,1480,methods=['findById','save','deleteById'],attrs=False)
   u.assoc(d,adapter_box,jpa,[(985,bottom(adapter_box)),(985,1480)],role='repository')
   dep(d,ctrl,factory,[(30,270),(15,270),(15,1160),(30,1160)])
   d.note(30,1515,725,['阅读顺序：Controller → UseCase ← Service → Store。','Store 由 JPA 适配器实现，再调用 JpaRepository。','方法图示省略参数，完整签名见对应功能顺序图。','实线为持有引用；虚线开箭头为依赖；空心三角为实现。'])
 # One complete domain model: all five existing business objects and their actual links.
 d=D('domain-model','DDD 领域对象完整类图',1120,1540)
 dept=cls(d,'Department',40,110,430,methods=['Department','change','requireDeletable'])
 user=cls(d,'User',650,110,430,methods=['User','changeProfile','assignRole','revokeRole'])
 role=cls(d,'Role',650,660,430,methods=['Role','change','grantPermission','revokePermission','requireDeletable'])
 perm=cls(d,'Permission',40,660,430,methods=['Permission','change','requireDeletable'])
 system=cls(d,'BusinessSystem',40,1130,430,methods=['BusinessSystem','change','requireDeletable'])
 dep(d,user,dept,[(650,230),(470,230)],'departmentId');label(d,560,207,'departmentId')
 dep(d,role,dept,[(650,735),(550,735),(550,360),(470,360)],'departmentId');label(d,540,560,'departmentId')
 dep(d,user,role,[(865,bottom(user)),(865,660)],'roleIds');label(d,935,610,'roleIds')
 dep(d,role,perm,[(650,870),(470,870)],'permissionIds');label(d,560,847,'permissionIds')
 dep(d,perm,system,[(255,bottom(perm)),(255,1130)],'systemId');label(d,335,1090,'systemId')
 dep(d,dept,dept,[(40,220),(15,220),(15,330),(40,330)],'parentId');label(d,170,440,'parentId 引用上级部门')
 d.note(610,1170,470,['按编号引用其他聚合。','User.roleIds 表达用户角色集合。','Role.permissionIds 表达角色权限集合。','部门角色归属限定授权范围。','各对象保存业务属性与领域行为。','关联持久化仍用原有两张中间表。'])
 # Detailed package diagrams. Each package contains named classes and typed edges.
 for mode in ['structured','ddd']:
  dd=mode=='ddd';title='DDD' if dd else '结构化'
  d=D('packages-full-'+mode,'系统完整 '+title+' UML 包图',1760,1970,'UMLPackageDiagram')
  def pack(name,x,y,body,h=160):
   rows=[line for text in body for line in c.wrap(text,326,19)]
   return d.node(name,x,y,350,max(h,85+26*len(rows)),'UMLPackage',body=rows)
  # Lane labels remain plain text; only real Java packages use UML tabs.
  for x,t in [(205,'API 输入层'),(605,'SERVICE.PORT 契约层'),(1005,'DDD 应用层与领域层' if dd else 'STRUCTURED 服务层'),(1555,'基础设施层')]:d.text(x,105,t,23,True,True)
  layer=d.node('service.ddd.application' if dd else 'service.structured',810,160,390,1120,'UMLPackage')
  api=[];ports=[];services=[]
  for i,(key,domains) in enumerate([('identity',['auth','authorization']),('user',['user','userrole']),('directory',['department','system','permission']),('role',['role','rolepermission'])]):
   y=225+i*280
   names={'identity':['AuthController','AuthorizationController','dto / vo'],'user':['UserController','UserRoleController','dto / vo'],'directory':['DepartmentController / SystemController','PermissionController / dto / vo'],'role':['RoleController','RolePermissionController','dto / vo']}[key]
   pn={'identity':['AuthUseCase / AuthorizationUseCase','LoginCommand / LoginResult'],'user':['UserUseCase / UserCommand','UserResult / UserData'],'directory':['DepartmentUseCase / SystemUseCase','PermissionUseCase / Command / Result'],'role':['RoleUseCase / RoleCommand','RoleResult / PermissionResult']}[key]
   sn={'identity':['DddAuthService' if dd else 'StructuredAuthService','DddAuthorizationService' if dd else 'StructuredAuthorizationService'],'user':['DddUserService' if dd else 'StructuredUserService'],'directory':['DddDepartmentService / DddBusinessSystemService' if dd else 'StructuredDepartmentService','DddPermissionService' if dd else 'StructuredBusinessSystemService / StructuredPermissionService'],'role':['DddRoleService' if dd else 'StructuredRoleService']}[key]
   a=pack('api.'+key,30,y,names,170);p=pack('service.port.'+key,430,y,pn,170);s=pack(('application.' if dd else 'service.structured.')+key,830,y,sn,170)
   api.append(a);ports.append(p);services.append(s)
   dep(d,a,p,[(380,y+75),(430,y+75)])
   # A package contains classes implementing the contract; dependency is used at package level.
   dep(d,s,p,[(830,y+125),(780,y+125)],'use case contract')
  # Shared contracts and five PO/repositories, security, and adapters are explicit.
  data=pack('service.port.repository',430,1420,['UserStore / RoleStore','DepartmentStore / SystemStore','PermissionStore / RbacReader'],190)
  error=pack('service.port.error',430,1690,['NotFound / Conflict / Forbidden','统一用例异常'],140)
  shared=pack('api.shared',30,1420,['ApiExceptionHandler','统一错误响应'],160)
  dep(d,shared,error,[(380,1490),(405,1490),(405,1760),(430,1760)])
  infrax=1380
  po=pack('persistence.po',infrax,225,['UserPO / RolePO / DepartmentPO','BusinessSystemPO / PermissionPO','用户角色与角色权限映射'],200)
  repo=pack('persistence.repository',infrax,585,['UserJpaRepository / RoleJpaRepository','DepartmentJpaRepository','BusinessSystemJpaRepository / PermissionJpaRepository'],200)
  adap=pack('persistence.adapter',infrax,945,['JpaUserStore / JpaRoleStore','JpaDepartmentStore / JpaBusinessSystemStore','JpaPermissionStore / JpaRbacReader'],200)
  security=pack('security',infrax,1420,['SessionFacade / RequestGuard','Session Cookie / CSRF','实时 RBAC 校验'],190)
  config=pack('config',infrax,1690,['BackendConfiguration','app.backend-strategy / Logback'],140)
  dep(d,security,ports[0],[(1380,1510),(1280,1510),(1280,130),(605,130),(605,207)],'security contract')
  dep(d,config,layer,[(1380,1760),(1240,1760),(1240,1220),(1200,1220)],'select strategy')
  dep(d,adap,repo,[(1555,945),(1555,785)])
  dep(d,repo,po,[(1555,585),(1555,425)])
  # Application package owns its module packages; common dependencies appear once.
  layer[0]['ownedElements']=[]
  for child in services:
   d.owner['ownedElements'].remove(child[0]);child[0]['_parent']=u.ref(layer[0]);layer[0]['ownedElements'].append(child[0])
  dep(d,layer,data,[(810,1240),(795,1240),(795,1360),(605,1360),(605,1402)])
  dep(d,adap,data,[(1380,1060),(1320,1060),(1320,1640),(800,1640),(800,1500),(780,1500)],'implements Store')
  if dd:
   domain=pack('service.ddd.domain',830,1420,['User / Role / Department','BusinessSystem / Permission','业务属性 / 领域行为 / ID 引用'],190)
   dep(d,layer,domain,[(1005,1280),(1005,1402)])
  else:
   support=pack('service.structured.support',830,1420,['ScopePolicy','复用校验与事务脚本支持'],190)
   dep(d,layer,support,[(1005,1280),(1005,1402)])
  d.note(30,1880,1700,['箭头指向被依赖包。包间用虚线依赖；接口实现关系在模块类图中逐一展开。','每个分组列对应一个职责层；包内列出实际类与数据契约。配置在启动时选择一套服务实现。'])

  for part in ['entry','implementation']:
   q=D('packages-'+mode+'-'+part,title+' 包图 '+('入口与用例契约' if part=='entry' else '服务与基础设施'),900,1660,'UMLPackageDiagram')
   def box(name,x,y,body,h=170):
    rows=[line for text in body for line in c.wrap(text,350,20)]
    return q.node(name,x,y,390,max(h,85+26*len(rows)),'UMLPackage',body=rows)
   if part=='entry':
    for i,(a,p) in enumerate(zip(api,ports)):
     yy=130+i*280
     aa=box(a[0]['name'],25,yy,[]); pp=box(p[0]['name'],485,yy,[])
     # Reuse the rendered package contents; these are explicit labelled package views.
     for original,xx in [(a,25),(p,485)]:
      textviews=[v for v in d.diagram['ownedViews'] if v.get('_type')=='UMLTextView' and original[1]['left'] < v.get('left',0) < original[1]['left']+350 and original[1]['top']+42 < v.get('top',0) < original[1]['top']+original[1]['height']]
      for j,v in enumerate(textviews):q.text(xx+195,yy+65+j*26,v['text'],19,True)
     dep(q,aa,pp,[(415,yy+85),(485,yy+85)])
    sh=box('api.shared',25,1310,['ApiExceptionHandler','统一错误响应'])
    er=box('service.port.error',485,1310,['NotFound / Conflict / Forbidden','统一用例异常'])
    dep(q,sh,er,[(415,1395),(485,1395)])
    q.note(25,1550,850,['Controller 依赖用例契约；DTO 与 VO 保留在 API 边界。','Command、Result、Data 作为跨层输入输出契约。'])
   else:
    appgroup=q.node('service.ddd.application' if dd else 'service.structured',10,80,420,1025,'UMLPackage')
    appgroup[0]['ownedElements']=[]
    for i,service in enumerate(services):
     yy=170+i*245
     ss=box(service[0]['name'],25,yy,[])
     q.owner['ownedElements'].remove(ss[0]);ss[0]['_parent']=u.ref(appgroup[0]);appgroup[0]['ownedElements'].append(ss[0])
     textviews=[v for v in d.diagram['ownedViews'] if v.get('_type')=='UMLTextView' and service[1]['left'] < v.get('left',0) < service[1]['left']+350 and service[1]['top']+42 < v.get('top',0) < service[1]['top']+service[1]['height']]
     for j,v in enumerate(textviews):q.text(220,yy+65+j*26,v['text'],19,True)
    po2=box('persistence.po',485,130,['UserPO / RolePO / DepartmentPO','BusinessSystemPO / PermissionPO'],190)
    jr=box('persistence.repository',485,460,['各资源 JpaRepository','findById / save / deleteById'],170)
    ja=box('persistence.adapter',485,790,['各资源 JpaStore / JpaRbacReader','实现共享 Store / RbacReader'],170)
    dep(q,jr,po2,[(680,460),(680,320)])
    dep(q,ja,jr,[(680,790),(680,630)])
    port2=box('service.port.repository',485,1135,['UserStore / RoleStore / DepartmentStore','BusinessSystemStore / PermissionStore','RbacReader'],200)
    dep(q,ja,port2,[(680,960),(680,1117)])
    domain2=box('service.ddd.domain' if dd else 'service.structured.support',25,1135,['User / Role / Department' if dd else 'ScopePolicy','BusinessSystem / Permission' if dd else '复用范围校验与事务脚本规则'],200)
    dep(q,appgroup,domain2,[(220,1105),(220,1117)])
    dep(q,appgroup,port2,[(430,1050),(450,1050),(450,1235),(485,1235)])
    q.note(25,1450,850,['服务实现用例接口，依赖共享持久化端口；适配器实现持久化端口。','DDD 应用服务使用领域对象；结构化服务按事务脚本执行规则。','PO 与 Data 使用 CloneFactory 拷贝；安全与配置位置见完整包图。'])
