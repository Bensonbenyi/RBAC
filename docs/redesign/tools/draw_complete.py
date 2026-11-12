"""Portrait UML plates with explicit coordinates and native StarUML elements.
The use-case inventory and diagram operations share the same catalog.
"""
import re, json
import draw_spec as u
from flow_catalog import MODULES, WRITES, READS, AUTH, entries
D,ref,obj=u.D,u.ref,u.obj
MOD={m.key:m for m in MODULES}

def op(cls,name,args='',ret='void',static=False):
 c=u.classifier(cls)
 if (cls,name) in u.OPS:return u.OPS[(cls,name)]
 o=obj('UMLOperation',c,name=name,visibility='public',parameters=[],isStatic=static)
 for arg in args.split(';') if args else []:
  n,t=arg.split(':',1);o['parameters'].append(obj('UMLParameter',o,name=n,type=t,direction='in'))
 if ret:o['parameters'].append(obj('UMLParameter',o,name='',type=ret,direction='return'))
 c['operations'].append(o);u.OPS[(cls,name)]=o;return o

def tw(s,size=20):return sum(size if ord(c)>255 else size*.54 for c in s)
def wrap(s,width,size=20):
 result=[]
 for paragraph in s.split('\n'):
  while tw(paragraph,size)>width:
   n=1
   while n<len(paragraph) and tw(paragraph[:n+1],size)<=width:n+=1
   cuts=[i+1 for i,c in enumerate(paragraph[:n]) if c in ' ,;']
   if cuts and cuts[-1]>n*.4:n=cuts[-1]
   
   if len(paragraph[n:]) <= 2 and n > 8:n -= 8
   result.append(paragraph[:n].rstrip());paragraph=paragraph[n:].lstrip()
  result.append(paragraph)
 return result

def lines(d,s,x,y,width,size=20,mid=False,gap=25):
 ss=wrap(s,width,size)
 for i,t in enumerate(ss):d.text(x,y+i*gap,t,size,mid,native=False)
 return len(ss)*gap

class Sequence:
 def __init__(self,stem,title,parts,height=1000,created=None):
  height+=35
  created={i:y+35 for i,y in (created or {}).items()}
  self.owner=obj('UMLInteraction',u.MODEL,name=title,ownedElements=[],participants=[],messages=[],fragments=[])
  u.MODEL['ownedElements'].append(self.owner)
  self.d=D(stem,title,900,height,'UMLSequenceDiagram',self.owner)
  self.parts=[];self.n=0;self.created=created or {}
  xs={2:[220,680],3:[120,450,780],4:[105,335,565,795]}[len(parts)]
  for i,((name,typ),x) in enumerate(zip(parts,xs)):
   u.classifier(typ);top=self.created.get(i,100)
   attr=obj('UMLAttribute',self.owner,name=name,type=ref(u.CLASSES[typ]));self.owner['ownedElements'].append(attr)
   m=obj('UMLLifeline',self.owner,name=name,represent=ref(attr));self.owner['participants'].append(m)
   v=self.d.view('UMLSeqLifelineView',m,left=x-95,top=top,width=190,height=height-top-35,subViews=[],stereotypeDisplay='label')
   c=u.nc(v,m,x-95,top,190,125)
   lp=obj('UMLLinePartView',v,model=ref(m),left=x,top=top+125,width=1,height=height-top-160,parentStyle=False,lineColor='#000000')
   v['subViews']=[c,lp];v['nameCompartment']=ref(c);v['linePart']=ref(lp)
   self.d.rect(x-95,top,190,125)
   self.d.text(x,top+22,name+':',20,True,native=False)
   words=re.findall('[A-Z][a-z0-9]*|[A-Z]+(?=[A-Z]|$)',typ) or [typ]
   header=[];s=''
   for word in words:
    if tw(s+word,20)>175:header.append(s);s=word
    else:s+=word
   if s:header.append(s)
   for j,t in enumerate(header):self.d.text(x,top+54+j*28,t,20,True,native=False)
   self.d.line([(x,top+125),(x,height-35)],dash=True)
   self.parts.append((m,v,lp,x,typ))
 def msg(self,a,b,y,text,method=None,reply=False,end=None,create=False):
  y+=35
  if end is not None:end+=35
  self.n+=1;p,q=self.parts[a],self.parts[b];x,z=p[3],q[3]
  if end and not reply and not create:self.d.rect(z-5,y,10,end-y)
  pts=[(x,y),(z-95 if create else z,y)] if a!=b else [(x+5,y),(x+65,y),(x+65,y+32),(x+10,y+32)]
  self.d.line(pts,'reply' if reply or create else 'self' if a==b else 'call',dash=reply or create)
  label=f'{self.n}: {text}'
  width=min(740,abs(z-x)+180) if a!=b else 330
  tx=max(width/2+15,min(900-width/2-15,(x+z)/2)) if a!=b else x+90
  ss=wrap(label,width,20)
  for i,t in enumerate(ss):self.d.text(tx,y-15-(len(ss)-i-1)*30,t,20,a!=b,native=False,bg=True)
  m=obj('UMLMessage',self.owner,name=text,source=ref(p[0]),target=ref(q[0]),messageSort='createMessage' if create else 'reply' if reply else 'synchCall',sequenceNumber=str(self.n))
  if method:
   assert (q[4],method) in u.OPS,(q[4],method)
   m['signature']=ref(u.OPS[(q[4],method)])
  self.owner['messages'].append(m)
  v=self.d.view('UMLSeqMessageView',m,tail=ref(p[2]),head=ref(q[1] if create else q[2]),points=';'.join(f'{xx}:{yy}' for xx,yy in pts),subViews=[],showSequenceNumber=True)
  for key,txt,visible in [('nameLabel',text,True),('stereotypeLabel','',False),('propertyLabel','',False)]:
   lab=u.label(v,txt,tx-width/2,y-25*len(ss)-10,width,24*len(ss),typ='EdgeLabelView',model=ref(m),hostEdge=ref(v),edgePosition=1,distance=18,alpha=1.57,visible=visible)
   lab['font']='PingFang SC;20;0';lab['wordWrap']=True;v['subViews'].append(lab);v[key]=ref(lab)
  activation=obj('UMLActivationView',v,model=ref(m),left=z-5,top=y,width=10,height=max(25,(end or y+25)-y),visible=not(reply or create),parentStyle=False,fillColor='#ffffff',lineColor='#000000')
  v['subViews'].append(activation);v['activation']=ref(activation)
 def frame(self,y,h,kind,guard):
  # Reuse the semantic CombinedFragment implementation with fixed local width.
  y+=35
  proxy=object.__new__(u.Seq);proxy.d=self.d;proxy.owner=self.owner
  u.Seq.fragment(proxy,y,h,kind,guard)
 def note(self,y,text):
  y+=35
  ss=wrap(text,800,20);h=len(ss)*25+28
  self.d.rect(35,y,830,h)
  for i,t in enumerate(ss):self.d.text(50,y+29+i*25,t,20,native=False)
  self.d.view('UMLNoteView',left=35,top=y,width=830,height=h,text=text,wordWrap=True,font='PingFang SC;20;0')


def definitions():
 u.define_classes()
 # Complete existing entity state and normalize constructor signatures.
 fields={'User':[('username','String'),('name','String'),('phone','String')], 'Role':[('name','String'),('permissionIds','Set<Long>')], 'Department':[('id','Long'),('name','String'),('parentId','Long')], 'BusinessSystem':[('id','Long'),('systemKey','String'),('name','String')], 'Permission':[('id','Long'),('systemId','Long'),('code','String'),('name','String')]}
 for name,attributes in fields.items():
  c=u.classifier(name)
  for field,typ in attributes:
   if not any(a['name']==field for a in c['attributes']):c['attributes'].append(obj('UMLAttribute',c,name=field,type=typ,visibility='private'))
 for name in ['User','Role']:
  constructor=u.OPS[(name,name)];constructor['parameters']=[obj('UMLParameter',constructor,name='data',type=name+'Data',direction='in')]

 for m in MODULES:
  for n in [m.port,m.store]:u.classifier(n,interface=True)
  u.classifier(m.controller)
  for mode in ['Structured','Ddd']:u.classifier(mode+m.domain+'Service')
  u.classifier(m.domain)
  for action in ['create','update','delete','list','get']:
   args='actorId: Long;cmd: '+m.domain+'Command' if action in ['create','update','delete'] else 'actorId: Long;id: Long' if action in ['get','delete'] else 'actorId: Long;query: PageQuery'
   ret=m.domain+'Result' if action in ['create','get'] else 'PageResult' if action=='list' else 'void'
   for cls in [m.port,'Structured'+m.domain+'Service','Ddd'+m.domain+'Service']:op(cls,action,args,ret)
   op(m.controller,action,'request: '+m.domain+'DTO','HttpResponse')
  dto_names={'user':('CreateUserDTO','UpdateUserDTO'),'department':('CreateDepartmentDTO','RenameDepartmentDTO'),'system':('CreateSystemDTO','RenameSystemDTO'),'permission':('CreatePermissionDTO','RenamePermissionDTO'),'role':('CreateRoleDTO','RenameRoleDTO')}
  for action in ['create','update','delete']:
   o=u.OPS[(m.controller,action)]
   params=[('dto',dto_names[m.key][0])] if action=='create' else [('id','Long'),('dto',dto_names[m.key][1])] if action=='update' else [('id','Long')]
   o['parameters']=[obj('UMLParameter',o,name=n,type=t,direction='in') for n,t in params]+[obj('UMLParameter',o,name='',type='HttpResponse',direction='return')]
  for name,args,ret in [('loadForChange','actorId: Long;cmd: ChangeCommand','ChangeData'),('insert','data: '+m.domain+'Data','Long'),('update','data: '+m.domain+'Data','void'),('delete','id: Long','void'),('query','actorId: Long;query: Query','QueryResult')]:op(m.store,name,args,ret)
  op(m.domain,m.domain,'data: '+m.domain+'Data','')
  for name,args in [('change','cmd: UpdateCommand'),('requireDeletable','references: ReferenceData')]:op(m.domain,name,args,m.domain if name=='create' else 'void',name=='create')
 for cls in ['StructuredAuthService','DddAuthService']:op(cls,'me','userId: Long','UserResult')
 for name in ['updateMe','assign','revoke']:
  for cls in ['UserUseCase','StructuredUserService','DddUserService']:op(cls,name,'actorId: Long;cmd: UserChangeCommand')
 for name in ['grant','revokePermission']:
  for cls in ['RoleUseCase','StructuredRoleService','DddRoleService']:op(cls,name,'actorId: Long;cmd: RoleChangeCommand')
 for name in ['roles','permissions']:
  for m in [MOD['user']] if name=='roles' else [MOD['role']]:
   for cls in [m.port,'Structured'+m.domain+'Service','Ddd'+m.domain+'Service']:op(cls,name,'actorId: Long;id: Long','QueryResult')
 for cls,names in [('UserStore',['removeRoles','removeRole']),('RoleStore',['grantPermission','removePermission'])]:
  for name in names:op(cls,name,'userId: Long' if name=='removeRoles' else 'cmd: RelationCommand')
 for name in ['changeProfile','revokeRole']:op('User',name,'cmd: UserChangeCommand')
 for name in ['grantPermission','revokePermission']:op('Role',name,'permissionId: Long')
 op('ScopePolicy','requireScope','data: ChangeData;actorId: Long')
 for cls in ['Structured'+m.domain+'Service' for m in MODULES]:op(cls,'validateChange','data: ChangeData;cmd: ChangeCommand')
 op('AuthController','me','actor: CurrentUser','UserVO');op('AuthController','csrf','','CsrfVO')
 op('AuthUseCase','me','userId: Long','UserResult')
 op('CsrfTokenRepository','loadOrGenerate','request: HttpServletRequest','CsrfToken')
 op('SessionFacade','establish','result: LoginResult');op('SessionFacade','current','','Identity');op('SessionFacade','clear','','void')
 op('RequestGuard','check','request: HttpRequest','CurrentUser')
 op('UserStore','requireCurrent','identity: Identity','UserData')
 op('RbacReader','requireLogin','identity: Identity','void')
 op('RbacReader','requireOperation','actor: CurrentUser;code: String','void')
 op('AuthorizationController','check','actor: CurrentUser;code: String','PermissionVO')
 op('JpaUserStore','query','actorId: Long;query: Query','QueryResult')
 op('UserJpaRepository','findScoped','departmentId: Long;query: Query','UserPO')
 op('UserJpaRepository','deleteRoles','userId: Long');op('UserJpaRepository','deleteUser','userId: Long')
 op('BackendConfiguration','select','strategy: String','UseCaseBeans')


def compact_class(d,name,x,y,w=380,methods=None,attrs=True,short=False):
 m=u.CLASSES[name];ops=[o for o in m['operations'] if methods is None or o['name'] in methods]
 rows=[]
 if attrs:
  for a in m.get('attributes',[]):rows.append(('attr',a,'- '+a['name']+': '+str(a['type'])))
 for o in ops:
  args=[p['name']+': '+p['type'] for p in o['parameters'] if p['direction']!='return'];ret=next((p['type'] for p in o['parameters'] if p['direction']=='return'),'void')
  rows.append(('op',o,'+ '+o['name']+'('+', '.join(args)+')'+('' if o['name']==name else ': '+ret)))
 header=66 if m['_type']=='UMLInterface' else 44
 if short: rows=[(kind,el,'+ '+el['name']+'()' if kind=='op' else t) for kind,el,t in rows]
 wrapped=[(kind,el,wrap(t,w-22,20)) for kind,el,t in rows]
 attrh=sum(len(ss)*28+8 for kind,_,ss in wrapped if kind=='attr') or 14
 oph=sum(len(ss)*28+8 for kind,_,ss in wrapped if kind=='op') or 14
 h=header+attrh+oph
 v=d.view('UMLInterfaceView' if m['_type']=='UMLInterface' else 'UMLClassView',m,left=x,top=y,width=w,height=h,subViews=[],showOperationSignature=not short,showVisibility=True,showType=True)
 c=u.nc(v,m,x,y,w,header);v['subViews'].append(c);v['nameCompartment']=ref(c)
 d.rect(x,y,w,h)
 if header==66:d.text(x+w/2,y+22,'«interface»',17,True,native=False)
 d.text(x+w/2,y+header-12,name,19,True,True,False)
 d.line([(x,y+header),(x+w,y+header)])
 pos=y+header
 for kind,field,ct,sz in [('attr','attributeCompartment','UMLAttributeCompartmentView',attrh),('op','operationCompartment','UMLOperationCompartmentView',oph)]:
  cp=obj(ct,v,model=ref(m),left=x,top=pos,width=w,height=sz,subViews=[],parentStyle=True)
  yp=pos+26
  for k,el,ss in wrapped:
   if k!=kind:continue
   for j,t in enumerate(ss):d.text(x+10,yp+j*28,t,20,native=False)
   lab=u.label(cp,'\n'.join(ss),x+8,yp-21,w-16,len(ss)*28+8,typ='UMLAttributeView' if kind=='attr' else 'UMLOperationView',model=ref(el),horizontalAlignment=0)
   lab['font']='PingFang SC;20;0';lab['wordWrap']=True;cp['subViews'].append(lab);yp+=len(ss)*28+8
  v['subViews'].append(cp);v[field]=ref(cp);pos+=sz
  if kind=='attr':d.line([(x,pos),(x+w,pos)])
 for field,ct in [('receptionCompartment','UMLReceptionCompartmentView'),('templateParameterCompartment','UMLTemplateParameterCompartmentView')]:
  cp=obj(ct,v,model=ref(m),left=x,top=pos,width=w,height=0,visible=False,parentStyle=True);v['subViews'].append(cp);v[field]=ref(cp)
 return m,v


def module_classes():
 for m in MODULES:
  for mode in ['structured','ddd']:
   prefix='Structured' if mode=='structured' else 'Ddd'
   d=D(f'class-{m.key}-{mode}',f'{m.title}模块 {"结构化" if mode=="structured" else "DDD"}类图',900,1240)
   ctrl=compact_class(d,m.controller,25,95,390,methods=['create','update','delete'],attrs=False)
   port=compact_class(d,m.port,485,95,390,methods=['create','update','delete'],attrs=False)
   svc=compact_class(d,prefix+m.domain+'Service',25,520,390,methods=['create','update','delete']+(['validateChange'] if mode=='structured' else []),attrs=False)
   store=compact_class(d,m.store,485,520,390,methods=['loadForChange','insert','update','delete'],attrs=False)
   d.edge(ctrl,port,[(415,240),(485,240)])
   pb=port[1]['top']+port[1]['height']
   d.edge(svc,port,[(415,610),(452,610),(452,pb+45),(680,pb+45),(680,pb)],True)
   d.edge(svc,store,[(415,725),(485,725)])
   if mode=='ddd':
    dm=compact_class(d,m.domain,25,925,650,methods=[m.domain,'change','requireDeletable'],attrs=False)
    d.edge(svc,dm,[(220,svc[1]['top']+svc[1]['height']),(220,925)])
    d.text(450,1210,'应用服务编排事务，领域对象维护规则；聚合间按编号引用。',18,True)
   else:
    d.note(40,1000,820,['事务脚本负责校验、分支与持久化编排。',f'数据范围：{m.scope}。',f'删除引用检查：{m.refs}。','查询方法使用相同用例端口；详见查询顺序图。'])
 # All role behavior in a separate coherent diagram rather than hiding grant methods.
 for mode in ['structured','ddd']:
  d=D('class-relations-'+mode,'授权关系 '+('结构化' if mode=='structured' else 'DDD')+'类图',900,1190)
  a=compact_class(d,'UserUseCase',25,100,390,methods=['assign','revoke'],attrs=False)
  b=compact_class(d,'RoleUseCase',485,100,390,methods=['grant','revokePermission'],attrs=False)
  prefix='Structured' if mode=='structured' else 'Ddd'
  aa=compact_class(d,prefix+'UserService',25,420,390,methods=['assign','revoke'],attrs=False)
  bb=compact_class(d,prefix+'RoleService',485,420,390,methods=['grant','revokePermission'],attrs=False)
  d.edge(aa,a,[(220,420),(220,a[1]['top']+a[1]['height'])],True)
  d.edge(bb,b,[(680,420),(680,b[1]['top']+b[1]['height'])],True)
  c=compact_class(d,'User' if mode=='ddd' else 'UserStore',25,760,390,methods=['assignRole','revokeRole'] if mode=='ddd' else ['addRole','removeRole'],attrs=False)
  dd=compact_class(d,'Role' if mode=='ddd' else 'RoleStore',485,760,390,methods=['grantPermission','revokePermission'] if mode=='ddd' else ['grantPermission','removePermission'],attrs=False)
  d.edge(aa,c,[(220,aa[1]['top']+aa[1]['height']),(220,760)])
  d.edge(bb,dd,[(680,bb[1]['top']+bb[1]['height']),(680,760)])
  d.note(35,1045,830,['UserService 校验 user.role.assign；RoleService 校验 role.manage。','部门限定对象范围；实际操作资格由 RBAC 权限决定。'])


def auth_classes():
 d=D('class-auth','认证与实时鉴权 类图',900,1120)
 ctrl=compact_class(d,'AuthController',25,100,390,methods=['login','me','logout'],attrs=False)
 port=compact_class(d,'AuthUseCase',485,100,390,methods=['login','me'],attrs=False)
 st=compact_class(d,'StructuredAuthService',25,430,390,methods=['login','me'],attrs=False)
 dd=compact_class(d,'DddAuthService',485,430,390,methods=['login','me'],attrs=False)
 users=compact_class(d,'UserStore',25,730,390,methods=['findByUsername','findById'],attrs=False)
 rbac=compact_class(d,'RbacReader',485,730,390,methods=['hasPermission'],attrs=False)
 d.edge(ctrl,port,[(415,230),(485,230)])
 d.edge(st,port,[(415,490),(450,490),(450,370),(680,370),(680,port[1]['top']+port[1]['height'])],True)
 d.edge(dd,port,[(680,430),(680,port[1]['top']+port[1]['height'])],True)
 d.edge(st,users,[(220,st[1]['top']+st[1]['height']),(220,730)])
 d.edge(dd,rbac,[(680,dd[1]['top']+dd[1]['height']),(680,730)])
 d.note(40,960,820,['两套认证服务均依赖 UserStore 和 RbacReader。','会话由 API 与共享安全基础设施管理。','鉴权通过 AuthorizationUseCase 与 RbacReader 实时查询。'])


def write_flows():
 for f in WRITES:
  m=MOD[f['module']];action=f['action'];slug=f['slug']
  for mode in ['structured','ddd']:
   svc=('Structured' if mode=='structured' else 'Ddd')+m.domain+'Service'
   ctrl='AuthController' if action=='updateMe' else 'UserRoleController' if action in ['assign','revoke'] else 'RolePermissionController' if action in ['grant','revokePermission'] else m.controller
   u.classifier(ctrl)
   parts=[('api',ctrl),('service',svc),('store',m.store)]
   if mode=='ddd':parts.append(('entity',m.domain))
   s=Sequence(f'flow-{slug}-{mode}',f'{f["title"]} {"结构化" if mode=="structured" else "DDD"}顺序图',parts,1250,created={3:460} if mode=='ddd' else None)
   s.msg(0,1,250,f'{action}(actorId, cmd)',action,end=1100)
   s.msg(1,2,355,'data = loadForChange(actorId, cmd)','loadForChange',end=400)
   s.msg(2,1,410,'data（目标、归属、关系与引用）',reply=True)
   # Rule rejection is explicitly modeled as a terminating break fragment.
   if mode=='structured':
    s.msg(1,1,505,'validateChange(data, cmd)','validateChange',end=540)
   else:
    behavior={'create':'create','update':'change','updateMe':'changeProfile','delete':'requireDeletable','assign':'assignRole','revoke':'revokeRole','grant':'grantPermission','revokePermission':'revokePermission'}[action]
    args={'create':'cmd','update':'cmd','updateMe':'cmd','delete':'data.references','assign':'role, actorDept','revoke':'cmd','grant':'permissionId','revokePermission':'permissionId'}[action]
    s.msg(1,3,505,m.domain+'(data)',m.domain,create=True)
    if action!='create':s.msg(1,3,635,behavior+'('+args+')',behavior,end=675)
   s.frame(715,100,'break','校验失败')
   s.msg(1,0,795,'抛出业务异常；4xx，事务回滚',reply=True)
   if action=='delete' and m.key=='user':
    s.msg(1,2,905,'removeRoles(cmd.userId)','removeRoles',end=935)
    s.msg(1,2,1000,'delete(cmd.id)','delete',end=1035)
   else:
    method={'create':'insert','update':'update','updateMe':'update','delete':'delete','assign':'addRole','revoke':'removeRole','grant':'grantPermission','revokePermission':'removePermission'}[action]
    args={'create':'newData','update':'changedData','updateMe':'changedData','delete':'cmd.id','assign':'userId, roleId','revoke':'cmd','grant':'cmd','revokePermission':'cmd'}[action]
    s.msg(1,2,940,method+'('+args+')',method,end=1000)
   s.msg(1,0,1100,'事务提交后返回结果',reply=True)
   s.note(1150,'事务覆盖加载至提交。'+f['rule']+'。')


def reads():
 for slug,title,key,endpoints,methods,rule in READS:
  m=MOD[key];ctrl='UserRoleController' if methods=='roles' else 'RolePermissionController' if methods=='permissions' else m.controller
  s=Sequence('flow-'+slug,title+' 顺序图',[('api',ctrl),('service',m.port),('store',m.store)],830)
  method=methods.split(' / ')[0]
  s.msg(0,1,265,methods+('(actorId, query)' if methods=='list' else '(actorId, id)'),method,end=675)
  s.msg(1,2,400,'result = query(actorId, query)','query',end=530)
  s.msg(2,1,550,'按范围过滤的查询数据',reply=True)
  s.msg(1,0,675,'Result；API 拷贝为 VO，返回 200',reply=True)
  s.note(715,rule+'。端口绑定启动配置选中的实现；失败按统一异常映射返回。')


def shared():
 s=Sequence('flow-preflight','受保护请求 校验顺序图',[('api','RequestGuard'),('session','SessionFacade'),('users','UserStore'),('rbac','RbacReader')],1100)
 s.msg(0,1,260,'identity = current()','current',end=300)
 s.msg(1,0,315,'identity；缺失则 401',reply=True)
 s.msg(0,2,420,'requireCurrent(identity)','requireCurrent',end=465)
 s.msg(2,0,480,'当前用户；已删除则 401',reply=True)
 s.msg(0,3,585,'requireLogin(identity)','requireLogin',end=630)
 s.note(675,'写请求在进入 Controller 前校验 CSRF；失败返回 403。管理 API 要求目标系统为 rbac。')
 s.msg(0,3,840,'requireOperation(actor, code)','requireOperation',end=890)
 s.msg(3,0,915,'通过；无操作权限则 403',reply=True)
 s.note(965,'通过后进入具体用例；服务在事务内复核操作权限和资源范围。当前鉴权只检查 login，随后返回 allowed。')
 s=Sequence('flow-csrf','取得 CSRF 凭据 顺序图',[('api','AuthController'),('csrf','CsrfTokenRepository')],610)
 s.msg(0,1,265,'token = loadOrGenerate(request)','loadOrGenerate',end=380)
 s.msg(1,0,415,'token、headerName',reply=True)
 s.note(475,'允许匿名获取；框架关联当前会话。写请求携带指定请求头，登录与退出后重新获取。')
 s=Sequence('flow-login','账号与登录资格 顺序图',[('api','AuthController'),('service','AuthUseCase'),('users','UserStore'),('rbac','RbacReader')],1030)
 s.msg(0,1,260,'login(cmd)','login',end=870)
 s.msg(1,2,375,'user = findByUsername(cmd.username)','findByUsername',end=415)
 s.msg(2,1,435,'user 或空值',reply=True)
 s.note(485,'服务比较 password 与 phone；账号不存在或不匹配时清理旧身份并返回统一 401。')
 s.msg(1,3,665,'hasPermission(userId, systemKey, "login")','hasPermission',end=710)
 s.msg(3,1,740,'allowed；false 则 403',reply=True)
 s.msg(1,0,870,'LoginResult；进入会话建立流程',reply=True)
 s.note(920,'输入由 Controller 校验并经 CloneFactory 转为 Command。登录失败清理旧身份，重取 CSRF。')
 s=Sequence('flow-session','登录成功 建立会话顺序图',[('api','AuthController'),('request','HttpServletRequest'),('session','HttpSession')],850)
 s.msg(0,1,260,'session = getSession()','getSession',end=300)
 s.msg(1,0,320,'session',reply=True)
 s.msg(0,1,435,'changeSessionId()','changeSessionId',end=480)
 s.msg(0,2,600,'setAttribute("identity", result)','setAttribute',end=645)
 s.note(715,'Session 业务身份仅含 userId、systemKey；返回 200 与 Cookie。框架维护 CSRF，客户端再次取得凭据。')
 s=Sequence('flow-me','查看当前用户 顺序图',[('api','AuthController'),('service','AuthUseCase'),('users','UserStore')],720)
 s.msg(0,1,265,'me(session.userId)','me',end=580)
 s.msg(1,2,395,'findById(userId)','findById',end=450)
 s.msg(2,1,475,'当前用户数据',reply=True)
 s.msg(1,0,580,'UserResult；API 转为 UserVO',reply=True)
 s.note(620,'会话与 login 检查采用公共校验流程；响应排除手机号及凭据，账号删除返回 401。')
 s=Sequence('flow-logout','退出会话 顺序图',[('api','AuthController'),('session','SessionFacade'),('http','HttpSession')],800)
 s.msg(0,1,265,'clear()','clear',end=610)
 s.frame(335,160,'opt','Session 存在')
 s.msg(1,2,440,'invalidate()','invalidate',end=475)
 s.msg(1,0,610,'清理 Cookie；204',reply=True)
 s.note(665,'退出仅要求有效 CSRF，允许无登录身份重复退出。已有会话销毁后，受保护请求返回 401。')
 s=Sequence('flow-authorize','当前权限判断 顺序图',[('api','AuthorizationController'),('service','AuthorizationUseCase'),('rbac','RbacReader')],790)
 s.msg(0,1,270,'check(userId, systemKey, code)','check',end=625)
 s.msg(1,2,415,'hasPermission(userId, systemKey, code)','hasPermission',end=490)
 s.msg(2,1,515,'当前 RBAC 关系的 EXISTS 结果',reply=True)
 s.msg(1,0,625,'allowed；200',reply=True)
 s.note(675,'userId 和 systemKey 取自 Session；编码不存在也返回 false。数据库失败返回 503，调用方拒绝受控操作。')
 s=Sequence('flow-copy','API 边界对象拷贝 顺序图',[('api','UserController'),('factory','CloneFactory'),('service','UserUseCase')],810)
 s.msg(0,1,260,'copy(command, dto)','copy',end=310)
 s.msg(1,0,335,'白名单 Command',reply=True)
 s.msg(0,2,460,'create(actorId, cmd)','create',end=525)
 s.msg(2,0,550,'Result',reply=True)
 s.msg(0,1,665,'copy(vo, result)','copy',end=705)
 s.note(740,'actorId 来自会话；主键、部门和授权关系由专用操作设置。')
 s=Sequence('flow-adapter','持久化读取与对象拷贝 顺序图',[('store','JpaUserStore'),('repo','UserJpaRepository'),('factory','CloneFactory')],800)
 s.msg(0,1,265,'findScoped(departmentId, query)','findScoped',end=355)
 s.msg(1,0,385,'UserPO',reply=True)
 s.msg(0,2,520,'copy(userData, userPO)','copy',end=580)
 s.msg(2,0,615,'UserData',reply=True)
 s.note(675,'PO 仅在 infrastructure 内使用；适配器以共享 Data 返回。日志记录操作与耗时，排除凭据。')


def package_plates():
 def box(d,name,x,y,w,h,body):
  val=d.node(name,x,y,w,h,'UMLPackage')
  for i,t in enumerate(body):d.text(x+w/2,y+72+i*29,t,20,True)
  return val
 d=D('01-structured-packages','结构化设计 包图',900,970,'UMLPackageDiagram')
 api=box(d,'api',35,120,370,170,['Controller / DTO / VO','输入校验与身份读取'])
 port=box(d,'service.port',495,120,370,190,['用例接口 / Command / Result','资源 Store 与 RbacReader','共享数据契约'])
 svc=box(d,'service.structured',35,520,370,180,['按资源组织的事务脚本','业务校验与持久化编排'])
 infra=box(d,'infrastructure',495,520,370,180,['JPA / PO / 端口适配器','security / logging / config'])
 d.edge(api,port,[(405,205),(495,205)])
 d.edge(svc,port,[(405,590),(450,590),(450,260),(495,260)])
 d.edge(infra,port,[(680,502),(680,310)])
 d.note(40,810,820,['API 与事务脚本均依赖共享契约，基础设施实现数据端口。','结构化服务在事务内执行操作权限、对象范围及业务规则校验。','选择 structured 时装配本套服务。'])
 d=D('02-ddd-packages','DDD 设计 包图',900,1160,'UMLPackageDiagram')
 api=box(d,'api',35,120,370,150,['Controller / DTO / VO'])
 port=box(d,'service.port',505,120,360,190,['用例接口与输入输出','共享数据与持久化端口'])
 app=box(d,'service.ddd.application',35,460,400,160,['事务边界与用例编排','权限及跨对象范围校验'])
 domain=box(d,'service.ddd.domain',35,850,400,150,['实体状态与领域行为','对象不变量及授权规则'])
 infra=box(d,'infrastructure',505,650,360,170,['persistence / security','logging / config'])
 d.edge(api,port,[(405,200),(505,200)])
 d.edge(app,port,[(435,530),(470,530),(470,260),(505,260)])
 d.edge(app,domain,[(235,620),(235,832)])
 d.edge(infra,port,[(685,632),(685,310)])
 d.note(40,1040,820,['应用服务使用共享端口加载数据，以值构造领域对象并调用其行为。','领域层保持框架无关；选择 ddd 时装配应用服务。'])


def architecture():
 # Same class contracts, selected beans; a package dependency diagram is more
 # accurate than inventing infrastructure nodes for a single-process deployment.
 d=D('architecture-runtime','运行装配与调用结构',900,1090,'UMLPackageDiagram')
 api=d.node('api',70,120,760,130,'UMLPackage',body=['Controller / DTO / VO / Session 与 CSRF 边界'])
 port=d.node('service.port',70,345,760,130,'UMLPackage',body=['用例接口 / Command / Result / 数据端口'])
 st=d.node('service.structured',40,590,390,150,'UMLPackage',body=['事务脚本','structured 时装配'])
 dd=d.node('service.ddd',470,590,390,150,'UMLPackage',body=['application / domain','ddd 时装配'])
 inf=d.node('infrastructure',70,895,760,130,'UMLPackage',body=['JPA 适配器 / 配置 / Logback / MySQL 连接'])
 d.edge(api,port,[(450,250),(450,327)])
 d.edge(st,port,[(240,572),(240,475)])
 d.edge(dd,port,[(660,572),(660,475)])
 d.edge(inf,port,[(70,950),(15,950),(15,410),(70,410)])
 d.note(140,780,620,['app.backend-strategy = structured | ddd','每个用例端口恰有一个 Bean；非法值启动失败。'])


def generate():
 definitions();package_plates();u.activities();module_classes();auth_classes();write_flows();reads();shared();architecture()
 from draw_supplement import generate as supplement
 import sys
 supplement(sys.modules[__name__])
 from draw_revision import generate as revision
 revision(sys.modules[__name__])
 # Current diagrams replace earlier wide plates while retaining their source files
 # only in Git history. The document now references the portrait catalog.
 u.finish()
 coverage=[]
 for slug,title,eps,figs in entries():
  for ep in eps:coverage.append(dict(endpoint=ep,flow=slug,title=title,figures=figs))
 assert len({r['endpoint'] for r in coverage})==len(coverage)
 stems={d.stem for d in u.DIAGRAMS}
 assert all(set(r['figures'])<=stems for r in coverage)
 (u.OUT/'coverage.json').write_text(json.dumps(coverage,ensure_ascii=False,indent=2))
 print(len(coverage),'HTTP endpoints mapped to',len(stems),'UML diagrams')
if __name__=='__main__':generate()
