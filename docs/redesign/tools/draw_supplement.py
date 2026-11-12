"""Explicitly placed UML supplements for domain state, packages and adapters."""
import draw_spec as u

def generate(c):
 D=c.D;K=c.compact_class;op=c.op
 # The shared resource ports retain existing contracts. Infrastructure realizes them.
 for m in c.MODULES:
  adapter='Jpa'+m.domain+'Store';repo=m.domain+'JpaRepository'
  u.classifier(adapter);u.classifier(repo,interface=True)
  for method,args,ret in [('insert','data: '+m.domain+'Data','Long'),('update','data: '+m.domain+'Data','void'),('delete','id: Long','void')]:op(adapter,method,args,ret)
  op(repo,'save','po: '+m.domain+'PO',m.domain+'PO');op(repo,'findById','id: Long',m.domain+'PO')
 op('JpaUserStore','removeRoles','userId: Long')
 op('UserJpaRepository','deleteById','userId: Long')
 # Infrastructure class view with real realization and association edges.
 d=D('class-persistence','共享持久化适配器 类图',900,1410)
 store=K(d,'UserStore',25,100,400,methods=['insert','update','delete','removeRoles'],attrs=False)
 adapter=K(d,'JpaUserStore',25,610,400,methods=['insert','update','delete','removeRoles'],attrs=False)
 repo=K(d,'UserJpaRepository',475,610,400,methods=['findById','save','deleteRoles','deleteById'],attrs=False)
 factory=K(d,'CloneFactory',475,100,400,methods=['copy'],attrs=False)
 d.edge(adapter,store,[(225,610),(225,store[1]['top']+store[1]['height'])],True)
 u.assoc(d,adapter,repo,[(425,750),(475,750)],role='repository')
 d.edge(adapter,factory,[(425,660),(450,660),(450,420),(675,420),(675,factory[1]['top']+factory[1]['height'])])
 d.note(35,1160,830,['适配器实现 Store；Repository 持有 JPA PO，服务只接收共享 Data。','CloneFactory 完成 PO 与 Data 的边界转换。','其余四个资源使用对应 Store、Jpa Store 与 JpaRepository。','删除用户的两条端口操作参与同一个外层服务事务。'])
 # Expand package organization rather than squeezing filenames into layer overviews.
 for mode in ['structured','ddd']:
  title='结构化' if mode=='structured' else 'DDD'
  d=D('packages-full-'+mode,'系统完整 '+title+' UML Package Diagram',900,1420,'UMLPackageDiagram')
  def pack(name,x,y,w,h,children):
   p=d.node(name,x,y,w,h,'UMLPackage');model=p[0];model['ownedElements']=[]
   for i,child in enumerate(children):
    node=u.obj('UMLPackage',model,name=child,ownedElements=[]);model['ownedElements'].append(node)
    # Editable child package views use one column and their own semantic owner.
    yy=y+65+i*52
    v=d.view('UMLPackageView',node,left=x+20,top=yy,width=w-40,height=38,subViews=[])
    comp=u.nc(v,node,x+20,yy,w-40,38);v['subViews']=[comp];v['nameCompartment']=u.ref(comp)
    d.rect(x+20,yy,w-40,38);d.text(x+w/2,yy+26,child,20,True,native=False)
   return p
  api=pack('api',30,110,380,400,['auth / authorization','user / userrole','department / system','permission / rolepermission','role / shared.error','dto / vo'])
  port=pack('service.port',490,110,380,400,['auth / authorization','user / userrole','department / system','permission / role','repository','command / query / result'])
  if mode=='structured':
   svc=pack('service.structured',30,650,380,400,['auth / authorization','user / userrole','department / system','permission / role','support.scope','support.validation'])
  else:
   svc=pack('service.ddd',30,650,380,400,['application.auth','application.user / role','application.directory','domain.user / role','domain.department','domain.system / permission'])
  infra=pack('infrastructure',490,650,380,400,['persistence.po','persistence.repository','persistence.adapter','security.session / csrf','config.strategy','logging'])
  d.edge(api,port,[(410,310),(490,310)])
  d.edge(svc,port,[(410,800),(450,800),(450,450),(490,450)])
  d.edge(infra,port,[(680,650),(680,510)])
  d.note(35,1170,830,['包依赖指向契约；配置装配选定实现。','DDD 的 application 依赖 domain；domain 保持框架无关。' if mode=='ddd' else '事务脚本负责用例步骤，support 只放复用校验。','API 使用共享用例接口；JPA 适配器实现 repository 数据端口。','此图展开所有业务模块，运行装配总览见运行架构图。'])
 # Explicit artifact locations, kept as real package elements in the model.
 d=D('packages-files','源文件与配置文件组织',900,1390,'UMLPackageDiagram')
 for i,(name,body) in enumerate([
 ('api',['<resource>Controller.java','dto/*.java / vo/*.java','shared/error/*.java']),
 ('service.port',['<resource>UseCase.java','command / query / result/*.java','repository/*Store.java']),
 ('service.structured',['<module>/Structured*Service.java','support/ScopePolicy.java']),
 ('service.ddd',['application/Ddd*Service.java','domain/<module>/*.java']),
 ('infrastructure',['persistence/po / repository / adapter','security / config / logging']),
 ('src/main/resources',['application.yml / logback-spring.xml','db/schema.sql / db/data.sql'])]):
  x=35 if i%2==0 else 475;y=120+(i//2)*380
  d.node(name,x,y,390,270,'UMLPackage',body=body)
 d.note(35,1280,830,['Java 包根按项目 POM 的 groupId 确定；图中路径从包根起。','资源文件负责配置与初始化；构建依赖与注解处理器由 pom.xml 声明。'])
 # Save and delete adapter interactions match teacher diagram categories.
 for action in ['insert','update']:
  s=c.Sequence('adapter-'+action,'JPA 用户适配器 '+('新建' if action=='insert' else '更新')+'顺序图',[('adapter','JpaUserStore'),('repo','UserJpaRepository'),('factory','CloneFactory')],1000)
  if action=='update':
   s.msg(0,1,270,'existing = findById(data.id)','findById',end=320)
   s.msg(1,0,345,'UserPO；不存在抛出 404',reply=True)
  else:s.note(255,'insert(data) 接收服务已校验的数据，准备一个新的 UserPO。')
  s.msg(0,2,470,'po = copy(target, data)','copy',end=530)
  s.msg(2,0,560,'仅拷贝允许变更字段',reply=True)
  s.msg(0,1,680,'saved = save(po)','save',end=745)
  s.msg(1,0,775,'saved；返回编号或完成更新',reply=True)
  s.note(845,'调用参与外层服务事务；提交时唯一约束冲突映射 409，整体回滚。更新只拷贝 name、phone，固定字段由映射排除。')
 s=c.Sequence('adapter-delete','JPA 用户关联清理与删除 顺序图',[('service','UserUseCase'),('adapter','JpaUserStore'),('repo','UserJpaRepository')],1010)
 s.msg(0,1,270,'removeRoles(userId)','removeRoles',end=460)
 s.msg(1,2,370,'deleteRoles(userId)','deleteRoles',end=415)
 s.msg(1,0,475,'角色关联清理完成',reply=True)
 s.msg(0,1,600,'delete(userId)','delete',end=805)
 s.msg(1,2,700,'deleteById(userId)','deleteById',end=750)
 s.msg(1,0,820,'用户删除完成',reply=True)
 s.note(880,'同一事务依次删除 user_role 和 app_user；任一步或提交失败全部回滚。适配器记录操作和耗时，异常统一映射。')
