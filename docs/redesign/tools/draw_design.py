"""Explicit coordinates for black-and-white UML. No Mermaid or automatic layout.
SVG/PNG previews and native StarUML models are generated from the same diagram data.
"""
from pathlib import Path
from html import escape
import json, math, subprocess

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'assets'/'design'
OUT.mkdir(parents=True,exist_ok=True)
FONT="'PingFang SC','Microsoft YaHei','Noto Sans CJK SC',system-ui,sans-serif"
counter=0
def uid():
 global counter
 counter+=1
 return f'rbac-design-{counter:06d}'
def ref(o):return {'$ref':o['_id'] if isinstance(o,dict) else o}
def obj(t,parent=None,**kw):
 o={'_type':t,'_id':uid()}
 if parent:o['_parent']=ref(parent)
 o.update(kw);return o
PROJECT=obj('Project',name='RBAC 双实现设计 v1')
MODEL=obj('UMLModel',PROJECT,name='设计模型',ownedElements=[])
PROJECT['ownedElements']=[MODEL]
DIAGRAMS=[]

def label(parent,text,x,y,w,h=24,bold=False,typ='LabelView',**kw):
 return obj(typ,parent,text=text,left=x,top=y,width=w,height=h,font=f'PingFang SC;16;{int(bold)}',fontColor='#000000',parentStyle=False,**kw)
def nc(parent,model,x,y,w,h=48):
 c=obj('UMLNameCompartmentView',parent,model=ref(model),left=x,top=y,width=w,height=h,parentStyle=True,subViews=[])
 for key,text,vis in [('stereotypeLabel','',False),('nameLabel',model.get('name',''),True),('namespaceLabel','',False),('propertyLabel','',False)]:
  v=label(c,text,x+5,y+10,w-10,visible=vis,bold=key=='nameLabel',horizontalAlignment=2)
  c['subViews'].append(v);c[key]=ref(v)
 return c
class D:
 def __init__(self,stem,title,w,h,kind='UMLClassDiagram',owner=None,subtitle=''):
  self.stem,self.w,self.h=stem,w,h
  self.owner=owner or obj('UMLPackage',MODEL,name=title,ownedElements=[])
  if owner is None:MODEL['ownedElements'].append(self.owner)
  self.diagram=obj(kind,self.owner,name=title,ownedViews=[],defaultDiagram=False)
  self.owner.setdefault('ownedElements',[]).append(self.diagram)
  self.svg=[];self.boxes=[]
  self.view('RectangleView',left=0,top=0,width=w,height=h,fillColor='#ffffff',lineColor='#ffffff')
  self.text(w/2,38,title,26,mid=True,bold=True)
  if subtitle:self.text(w/2,72,subtitle,16,mid=True)
  DIAGRAMS.append(self)
 def model(self,t,**kw):
  m=obj(t,self.owner,**kw);self.owner.setdefault('ownedElements',[]).append(m);return m
 def view(self,t,model=None,**kw):
  props=dict(parentStyle=False,lineColor='#000000',fillColor='#ffffff',fontColor='#000000',font='PingFang SC;16;0',showShadow=False);props.update(kw)
  v=obj(t,self.diagram,**props)
  if model:v['model']=ref(model)
  self.diagram['ownedViews'].append(v);return v
 def text(self,x,y,s,size=18,mid=False,bold=False,native=True,bg=False):
  if bg:
   tw=sum(size if ord(c)>255 else size*.57 for c in s)+10
   self.svg.append(f'<rect x="{x-tw/2 if mid else x-5}" y="{y-size}" width="{tw}" height="{size+6}" fill="white"/>')
  self.svg.append(f'<text x="{x}" y="{y}" font-size="{size}" text-anchor="{"middle" if mid else "start"}" font-weight="{600 if bold else 400}">{escape(s)}</text>')
  if native:
   w=sum(size if ord(c)>255 else size*.58 for c in s)+12
   self.view('UMLTextView',left=x-w/2 if mid else x,top=y-size,width=w,height=size+12,text=s,font=f'PingFang SC;{size};{int(bold)}',horzAlign=2 if mid else 0,wordWrap=False)
 def rect(self,x,y,w,h,round=0,dash=False):
  self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{round}" fill="white" stroke="black" stroke-width="1.4"'+(' stroke-dasharray="7 5"' if dash else '')+'/>')
 def line(self,points,kind=None,dash=False):
  # Cubic curves round manual routing; UML sequence self-calls keep conventional elbows.
  p='M '+f'{points[0][0]},{points[0][1]}'
  if len(points)>2 and kind!='self':
   for i in range(1,len(points)-1):
    a,b,c=points[i-1],points[i],points[i+1]
    def near(p,q,d=16):
     dx,dy=q[0]-p[0],q[1]-p[1];l=math.hypot(dx,dy);r=min(d,l/3)
     return p[0]+dx/l*r,p[1]+dy/l*r
    before=near(b,a);after=near(b,c)
    p+=f' L {before[0]},{before[1]} Q {b[0]},{b[1]} {after[0]},{after[1]}'
   p+=f' L {points[-1][0]},{points[-1][1]}'
  else:p+=''.join(f' L {x},{y}' for x,y in points[1:])
  marker={'dep':'open','call':'filled','reply':'open','real':'triangle','flow':'open','self':'filled'}.get(kind)
  self.svg.append(f'<path d="{p}" fill="none" stroke="black" stroke-width="1.4"'+(' stroke-dasharray="7 5"' if dash else '')+(f' marker-end="url(#{marker})"' if marker else '')+'/>')
 def note(self,x,y,w,lines):
  h=24+len(lines)*25;self.rect(x,y,w,h)
  for i,s in enumerate(lines):self.text(x+15,y+27+25*i,s,17,native=False)
  v=self.view('UMLNoteView',left=x,top=y,width=w,height=h,text='\n'.join(lines),wordWrap=True)
 def node(self,name,x,y,w,h,typ='UMLClass',ops=None,body=None):
  m=self.model(typ,name=name)
  v=self.view({'UMLClass':'UMLClassView','UMLInterface':'UMLInterfaceView','UMLPackage':'UMLPackageView'}[typ],m,left=x,top=y,width=w,height=h,subViews=[],stereotypeDisplay='label')
  comp=nc(v,m,x,y,w,50);v['subViews'].append(comp);v['nameCompartment']=ref(comp)
  self.rect(x,y,w,h)
  if typ=='UMLPackage':
   self.rect(x,y-18,min(w*.5,180),18)
   self.text(x+w/2,y+32,name,21,True,True,False)
   for i,s in enumerate(body or []):self.text(x+w/2,y+65+26*i,s,17,True,native=False)
   if body:
    for i,s in enumerate(body):self.text(x+w/2,y+65+26*i,s,17,True,native=True);self.svg.pop()
  else:
   off=0
   if typ=='UMLInterface':self.text(x+w/2,y+23,'«interface»',16,True,native=False);off=18
   self.text(x+w/2,y+31+off,name,19,True,True,False)
   hy=y+48+off;self.line([(x,hy),(x+w,hy)])
   m['operations']=[]
   opc=obj('UMLOperationCompartmentView',v,model=ref(m),left=x,top=hy,width=w,height=h-(hy-y),subViews=[],parentStyle=True)
   for i,(method,args) in enumerate(ops or []):
    operation=obj('UMLOperation',m,name=method,parameters=[])
    for arg in args.split(',') if args else []:operation['parameters'].append(obj('UMLParameter',operation,name=arg.strip(),type=''))
    m['operations'].append(operation)
    text=f'+ {method}({args})'
    self.text(x+12,hy+28+i*27,text,16,native=False)
    opc['subViews'].append(label(opc,text,x+10,hy+9+i*27,w-20,typ='UMLOperationView',model=ref(operation),horizontalAlignment=0))
   v['subViews'].append(opc);v['operationCompartment']=ref(opc)
   for field,ct in [('attributeCompartment','UMLAttributeCompartmentView'),('receptionCompartment','UMLReceptionCompartmentView'),('templateParameterCompartment','UMLTemplateParameterCompartmentView')]:
    c=obj(ct,v,model=ref(m),left=x,top=hy,width=w,height=0,visible=False,parentStyle=True)
    v['subViews'].append(c);v[field]=ref(c)
  self.boxes.append((x,y,w,h))
  return m,v
 def edge(self,a,b,points,real=False,name=''):
  t='UMLInterfaceRealization' if real else 'UMLDependency'
  m=self.model(t,source=ref(a[0]),target=ref(b[0]),name=name)
  self.line(points,'real' if real else 'dep',dash=True)
  v=self.view(t+'View',m,tail=ref(a[1]),head=ref(b[1]),points=';'.join(f'{x}:{y}' for x,y in points),lineStyle=1)
  return v
 def save(self):
  defs='<defs><marker id="open" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto" markerUnits="userSpaceOnUse"><path d="M1,1 L10,6 L1,11" fill="none" stroke="black"/></marker><marker id="filled" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto" markerUnits="userSpaceOnUse"><path d="M1,1 L10,6 L1,11 Z" fill="black"/></marker><marker id="triangle" markerWidth="16" markerHeight="16" refX="14" refY="8" orient="auto" markerUnits="userSpaceOnUse"><path d="M1,1 L14,8 L1,15 Z" fill="white" stroke="black"/></marker></defs>'
  text=f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}"><style>text{{font-family:{FONT};fill:black}}</style>{defs}<rect width="100%" height="100%" fill="white"/>'+''.join(self.svg)+'</svg>'
  (OUT/(self.stem+'.svg')).write_text(text)
  subprocess.run(['rsvg-convert','-z','1.3',str(OUT/(self.stem+'.svg')),'-o',str(OUT/(self.stem+'.png'))],check=True)

def architecture():
 d=D('01-architecture','双实现分层架构  包依赖图',1500,1140,'UMLPackageDiagram',subtitle='共享 API / Service Port / Infrastructure；配置仅装配一套 Service')
 api=d.node('api',50,130,400,185,'UMLPackage',body=['auth / user / role / permission','department / system','Controller  DTO  VO  @Valid'])
 port=d.node('service.port',920,130,530,185,'UMLPackage',body=['用例接口：AuthUseCase / UserRoleUseCase …','输入输出：Command / Result','持久化端口：UserStore / RoleReader / RbacReader'])
 structured=d.node('service.structured',50,430,400,185,'UMLPackage',body=['事务脚本  顺序校验  Repository 编排','StructuredAuthService','StructuredUserRoleService …'])
 ddd=d.node('service.ddd',920,430,530,185,'UMLPackage',body=['application：用例编排与事务','domain：User / Role 聚合、分配规则','DddAuthService / DddUserRoleService …'])
 infra=d.node('infrastructure',485,755,530,185,'UMLPackage',body=['persistence：端口适配 / JPA Entity / Repository','security：Session / Cookie / 当前身份适配','logging：SLF4J + Logback'])
 d.edge(api,port,[(455,220),(915,220)])
 d.text(685,200,'依赖用例接口',17,True)
 d.edge(structured,port,[(455,475),(665,475),(665,270),(915,270)])
 d.text(685,455,'依赖契约与持久化端口',16,True)
 d.edge(ddd,port,[(1185,425),(1185,320)])
 d.text(1300,380,'依赖契约',16,True)
 d.edge(infra,port,[(1020,850),(1480,850),(1480,340),(1370,340),(1370,320)])
 d.text(1260,830,'实现持久化端口（包依赖）',16,True)
 d.note(50,755,400,['配置：app.backend-strategy','structured 或 ddd','启动时只注册对应实现 Bean'])
 d.note(1050,900,400,['两套实现使用同一数据库、同一 API','CloneFactory 用于边界对象拷贝','各可执行层通过 Logback 记日志'])
 d.note(50,985,965,['虚线开箭头：源码依赖，箭头指向被依赖包；图中位置不表示调用顺序。','Service 通过 service.port 内的持久化接口调用 Infrastructure 适配器。','运行调用路径：Controller -> 用例接口 -> 被选中的 Service -> 持久化端口 -> JPA 适配器。'])
 return d

def class_diagrams():
 # Structured: 2-by-3, no domain aggregate objects.
 d=D('02-structured-classes','结构化设计  用户角色分配类图',1480,1050,subtitle='规则集中在事务脚本；持久化适配器只读写数据')
 ctrl=d.node('UserRoleController',40,130,400,140,ops=[('assign','userId, dto')])
 uc=d.node('UserRoleUseCase',1040,130,400,140,'UMLInterface',ops=[('assign','actorId, userId, command')])
 svc=d.node('StructuredUserRoleService',40,440,460,195,ops=[('assign','actorId, userId, command'),('checkDepartment','actor, user, role')])
 store=d.node('UserStore',1040,440,400,180,'UMLInterface',ops=[('findById','userId'),('addRole','userId, roleId'),('save','snapshot')])
 read=d.node('RbacReader / RoleReader',40,790,460,165,'UMLInterface',ops=[('hasPermission','userId, system, code'),('findRole','roleId')])
 adapt=d.node('JpaUserStore',1040,790,400,165,ops=[('findById','userId'),('addRole','userId, roleId'),('save','snapshot')])
 d.edge(ctrl,uc,[(445,200),(1035,200)])
 d.edge(svc,uc,[(505,475),(740,475),(740,265),(1035,265)],True)
 d.edge(svc,store,[(505,550),(1035,550)])
 d.edge(svc,read,[(270,640),(270,785)])
 d.edge(adapt,store,[(1240,785),(1240,625)],True)
 d.note(540,790,460,['assign 在一个事务内：','查 RBAC 权限、目标用户与角色','校验同部门、检查重复、写 user_role','DTO / Result 拷贝使用 CloneFactory'])
 d.text(740,1018,'虚线开箭头：依赖；虚线空心三角：接口实现。这里只展开角色分配用例。',17,True)
 # Two read interfaces visually grouped into one classifier is inaccurate, use single query port name instead.
 read[0]['name']='AssignmentReader'
 # Replace rendered label and semantic compartment label.
 d.svg=[s.replace('RbacReader / RoleReader','AssignmentReader') for s in d.svg]
 read[1]['subViews'][0]['subViews'][1]['text']='AssignmentReader'

 d=D('03-ddd-classes','DDD 设计  用户角色分配类图',1630,1190,subtitle='用户聚合维护角色 ID 集合；应用服务编排，领域对象执行规则')
 uc=d.node('UserRoleUseCase',40,130,440,145,'UMLInterface',ops=[('assign','actorId, userId, command')])
 app=d.node('DddUserRoleService',40,450,440,160,ops=[('assign','actorId, userId, command')])
 user=d.node('User «聚合根»',1140,130,440,185,ops=[('assignRole','roleId'),('hasRole','roleId'),('departmentId','')])
 rule=d.node('RoleAssignmentRule',1140,450,440,160,ops=[('requireSameDepartment','actor, user, role')])
 store=d.node('UserStore',40,835,440,185,'UMLInterface',ops=[('findById','userId'),('save','snapshot')])
 reader=d.node('AssignmentReader',610,835,440,185,'UMLInterface',ops=[('hasPermission','userId, system, code'),('findRole','roleId')])
 adapt=d.node('JpaUserStore',1140,835,440,185,ops=[('findById','userId'),('save','snapshot')])
 d.edge(app,uc,[(260,445),(260,280)],True)
 d.edge(app,user,[(485,490),(795,490),(795,230),(1135,230)])
 d.edge(app,rule,[(485,565),(1135,565)])
 d.edge(app,store,[(260,615),(260,830)])
 d.edge(app,reader,[(485,590),(700,700),(830,700),(830,830)])
 d.edge(adapt,store,[(1360,1025),(1360,1070),(260,1070),(260,1025)],True)
 d.note(560,115,500,['User 聚合：用户资料 + 已分配角色 ID','Role 聚合：角色资料 + 权限 ID','跨聚合只引用 ID；领域不依赖 JPA 或端口','共享 UserStore 传递快照，应用层重建聚合'])
 d.text(815,1137,'应用服务把 User 转为共享快照后保存；适配器同步 user_role，不依赖 DDD 聚合类型。',18,True)
 return d

class Activity:
 def __init__(self,stem,title,h):
  self.owner=obj('UMLActivity',MODEL,name=title,ownedElements=[],nodes=[],edges=[])
  MODEL['ownedElements'].append(self.owner)
  self.d=D(stem,title,1060,h,'UMLActivityDiagram',self.owner,'两套实现共用此业务流程；失败分支立即结束')
 def node(self,key,typ,x,y,w,h,text=''):
  m=obj(typ,self.owner,name=text);self.owner['nodes'].append(m)
  if typ=='UMLAction':
   v=self.d.view('UMLActionView',m,left=x-w/2,top=y-h/2,width=w,height=h,wordWrap=True,subViews=[])
   c=nc(v,m,x-w/2,y-h/2,w,h);v['subViews']=[c];v['nameCompartment']=ref(c)
   self.d.rect(x-w/2,y-h/2,w,h,14)
   lines=text.split('\n')
   for i,s in enumerate(lines):self.d.text(x,y-(len(lines)-1)*12+6+i*24,s,18,True,native=False)
  else:
   v=self.d.view('UMLControlNodeView',m,left=x-w/2,top=y-h/2,width=w,height=h)
   if typ=='UMLDecisionNode':
    self.d.svg.append(f'<path d="M{x-w/2},{y} L{x},{y-h/2} L{x+w/2},{y} L{x},{y+h/2} Z" fill="white" stroke="black" stroke-width="1.4"/>')
    self.d.text(x,y+6,text,17,True,native=False)
    # ControlNodeView does not display a name; add separate editable text.
    self.d.text(x,y+6,text,17,True);self.d.svg.pop()
   else:
    if typ=='UMLActivityFinalNode':self.d.svg.append(f'<circle cx="{x}" cy="{y}" r="15" fill="white" stroke="black" stroke-width="1.4"/>')
    self.d.svg.append(f'<circle cx="{x}" cy="{y}" r="10" fill="black"/>')
  val=(m,v,(x,y,w,h));setattr(self,key,val);return val
 def edge(self,a,b,guard='',side=False):
  x,y,w,h=a[2];xx,yy,ww,hh=b[2]
  if side:pts=[(x+w/2+5,y),(xx-ww/2-5,yy)]
  else:pts=[(x,y+h/2+5),(xx,yy-hh/2-5)]
  self.d.line(pts,'flow')
  m=obj('UMLControlFlow',self.owner,source=ref(a[0]),target=ref(b[0]),guard=guard);self.owner['edges'].append(m)
  self.d.view('UMLControlFlowView',m,tail=ref(a[1]),head=ref(b[1]),points=';'.join(f'{px}:{py}' for px,py in pts),lineStyle=1)
  if guard:
   if side:self.d.text((pts[0][0]+pts[-1][0])/2,y-13,f'[{guard}]',17,True)
   else:self.d.text(x+35,(pts[0][1]+pts[-1][1])/2,f'[{guard}]',17)
 def fail(self,decision,y,text):
  a=self.node('f'+str(y),'UMLAction',790,y,280,65,text)
  end=self.node('e'+str(y),'UMLActivityFinalNode',995,y,30,30)
  self.edge(decision,a,'否',True);self.edge(a,end,side=True)

def activities():
 a=Activity('04-login-activity','登录活动图',1360)
 a.node('start','UMLInitialNode',300,115,20,20)
 a.node('request','UMLAction',300,200,440,65,'接收用户名、password、单个目标系统')
 a.node('valid','UMLDecisionNode',300,315,220,90,'参数有效？')
 a.node('load','UMLAction',300,450,440,85,'CloneFactory：DTO 转 Command\n按用户名读取用户')
 a.node('identity','UMLDecisionNode',300,585,220,90,'手机号匹配？')
 a.node('permission','UMLAction',300,720,440,85,'按用户—角色—权限查询\n目标系统的 login 权限')
 a.node('allowed','UMLDecisionNode',300,855,220,90,'拥有登录权限？')
 a.node('session','UMLAction',300,995,440,85,'更新 Session 标识并保存身份\nSession 只保存 userId、systemKey')
 a.node('response','UMLAction',300,1140,440,85,'CloneFactory：Result 转 VO\n返回结果并设置会话 Cookie')
 a.node('end','UMLActivityFinalNode',300,1270,30,30)
 order=[a.start,a.request,a.valid,a.load,a.identity,a.permission,a.allowed,a.session,a.response,a.end]
 for p,q in zip(order,order[1:]):a.edge(p,q,'是' if p in [a.valid,a.identity,a.allowed] else '')
 a.fail(a.valid,315,'返回 400：参数错误');a.fail(a.identity,585,'返回 401：账号或凭据错误');a.fail(a.allowed,855,'返回 403：无系统登录权限')
 a.d.text(530,1325,'password 的内容为手机号；日志不记录凭据、完整手机号或 Session ID。',17,True)
 a=Activity('05-assign-activity','分配角色活动图',1480)
 a.node('start','UMLInitialNode',300,115,20,20)
 items=[('request',200,'UMLAction','读取已登录操作者\n校验目标 userId 与 roleId'),('perm',335,'UMLDecisionNode','有分配权限？'),('load',470,'UMLAction','读取操作者、目标用户与角色'),('exists',605,'UMLDecisionNode','记录均存在？'),('depart',740,'UMLDecisionNode','同一非空部门？'),('duplicate',875,'UMLDecisionNode','尚未分配？'),('write',1010,'UMLAction','在事务内写入 user_role\n结构化：Service；DDD：User 聚合'),('result',1155,'UMLAction','事务提交后返回成功')]
 order=[a.start]
 for key,y,t,txt in items:order.append(a.node(key,t,300,y,220 if t=='UMLDecisionNode' else 440,90 if t=='UMLDecisionNode' else 85,txt))
 order.append(a.node('end','UMLActivityFinalNode',300,1280,30,30))
 for p,q in zip(order,order[1:]):a.edge(p,q,'是' if p[0]['_type']=='UMLDecisionNode' else '')
 for dec,y,s in [(a.perm,335,'403：无 user.role.assign'),(a.exists,605,'404：用户或角色不存在'),(a.depart,740,'403：超出部门范围'),(a.duplicate,875,'409：角色已分配')]:a.fail(dec,y,s)
 a.d.note(80,1350,900,['操作者 ID 取自 Session，不接受前端伪造；本图前提是已登录且通过请求格式校验。','权限、记录、部门、重复校验和写入在同一事务内；数据库保留联合主键防止重复。'])

class Sequence:
 def __init__(self,stem,title,names,h=1450,subtitle=''):
  self.owner=obj('UMLInteraction',MODEL,name=title,ownedElements=[],participants=[],messages=[],fragments=[])
  MODEL['ownedElements'].append(self.owner)
  self.w=280*len(names)+80
  self.d=D(stem,title,self.w,h,'UMLSequenceDiagram',self.owner,subtitle)
  self.l=[]
  for i,name in enumerate(names):
   x=180+280*i
   participant=obj('UMLLifeline',self.owner,name=name,isMultiInstance=False)
   self.owner['participants'].append(participant)
   v=self.d.view('UMLSeqLifelineView',participant,left=x-125,top=110,width=250,height=h-240,subViews=[])
   c=nc(v,participant,x-125,110,250,70)
   lp=obj('UMLLinePartView',v,model=ref(participant),left=x,top=180,width=1,height=h-310,parentStyle=False,lineColor='#000000',font='PingFang SC;16;0')
   v['subViews']=[c,lp];v['nameCompartment']=ref(c);v['linePart']=ref(lp)
   self.l.append((participant,v,lp,x))
   self.d.rect(x-125,110,250,70)
   lines=name.split(': ',1)
   for j,s in enumerate(lines):self.d.text(x,139+j*25,s,17,True,native=False)
   self.d.line([(x,180),(x,h-130)],dash=True)
  self.i=0
 def msg(self,s,t,y,name,reply=False):
  self.i+=1;a,b=self.l[s],self.l[t];xx,xxx=a[3],b[3]
  if s==t:
   pts=[(xx,y),(xx+95,y),(xx+95,y+35),(xx,y+35)]
   self.d.line(pts,'self');self.d.text(xx+110,y+20,f'{self.i}. {name}',17,bg=True)
  else:
   pts=[(xx,y),(xxx,y)];self.d.line(pts,'reply' if reply else 'call',reply)
   if not reply:self.d.svg.append(f'<rect x="{xxx-5}" y="{y}" width="10" height="30" fill="white" stroke="black" stroke-width="1.2"/>')
   self.d.text((xx+xxx)/2,y-13,f'{self.i}. {name}',17,True,native=False,bg=True)
  m=obj('UMLMessage',self.owner,name=name,source=ref(a[0]),target=ref(b[0]),messageSort='reply' if reply else 'synchCall')
  self.owner['messages'].append(m)
  v=self.d.view('UMLSeqMessageView',m,tail=ref(a[2]),head=ref(b[2]),points=';'.join(f'{x}:{yy}' for x,yy in pts),subViews=[])
  for key,text,visible in [('nameLabel',name,True),('stereotypeLabel','',False),('propertyLabel','',False)]:
   l=label(v,text,min(xx,xxx)+10,y-30,max(200,abs(xx-xxx)-20),typ='EdgeLabelView',model=ref(m),hostEdge=ref(v),edgePosition=1,distance=16,alpha=math.pi/2,visible=visible)
   v['subViews'].append(l);v[key]=ref(l)
  act=obj('UMLActivationView',v,model=ref(m),left=xxx-6,top=y,width=12,height=35,visible=not reply,parentStyle=False,fillColor='#ffffff',lineColor='#000000')
  v['subViews'].append(act);v['activation']=ref(act)
  return m
 def fragment(self,y,h,operator,guard):
  m=obj('UMLCombinedFragment',self.owner,interactionOperator=operator,operands=[])
  op=obj('UMLInteractionOperand',m,name='Operand',guard=guard);m['operands'].append(op);self.owner['fragments'].append(m)
  x=35;w=self.w-70
  self.d.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="none" stroke="black" stroke-width="1.4"/>')
  self.d.text(x+12,y+24,operator,17,bold=True,native=False);self.d.text(x+90,y+24,'['+guard+']',17,native=False)
  v=self.d.view('UMLCombinedFragmentView',m,left=x,top=y,width=w,height=h,subViews=[])
  for key,text,visible in [('nameLabel','',False),('frameTypeLabel',operator,True)]:
   lab=label(v,text,x+8,y+5,70,visible=visible);v['subViews'].append(lab);v[key]=ref(lab)
  comp=obj('UMLInteractionOperandCompartmentView',v,model=ref(m),left=x,top=y+30,width=w,height=h-30,subViews=[],parentStyle=True)
  ov=obj('UMLInteractionOperandView',comp,model=ref(op),left=x,top=y+30,width=w,height=h-30,subViews=[],parentStyle=True)
  gl=label(ov,'['+guard+']',x+80,y+32,w-90);ov['subViews'].append(gl);ov['guardLabel']=ref(gl)
  comp['subViews'].append(ov);v['subViews'].append(comp);v['operandCompartment']=ref(comp)
 def footer(self,lines):self.d.note(55,self.d.h-105,self.w-110,lines)

def sequences():
 s=Sequence('06-login-sequence','共享登录时序图',['客户端','api: AuthController','service: AuthUseCase 实现','infra: UserStore','infra: RbacReader','security: HttpSession'],1500,'service 为配置选中的 StructuredAuthService 或 DddAuthService；password 内容为手机号')
 s.msg(0,1,240,'POST /auth/login')
 s.msg(1,1,300,'@Valid；CloneFactory.copy(command, dto)')
 s.msg(1,2,410,'login(command)')
 s.msg(2,3,490,'findByUsername(username)')
 s.msg(3,2,550,'用户读取结果',True)
 s.fragment(590,110,'break','用户不存在或手机号不匹配')
 s.msg(2,1,665,'认证失败；映射 401',True)
 s.msg(2,4,770,'hasPermission(userId, systemKey, "login")')
 s.msg(4,2,830,'allowed',True)
 s.fragment(870,110,'break','allowed = false')
 s.msg(2,1,945,'禁止登录；映射 403',True)
 s.msg(2,1,1050,'LoginResult(userId, systemKey)',True)
 s.msg(1,5,1120,'更换 Session ID，保存 userId、systemKey')
 s.msg(1,1,1185,'CloneFactory.copy(vo, result)')
 s.msg(1,0,1300,'200 + LoginVO；Set-Cookie',True)
 s.footer(['凭据匹配：结构化版在 Service 校验；DDD 版调用 User 的匹配行为。身份成功后仍须查 RBAC。','Session 不保存权限快照；后续敏感操作重新查权限。参数错误返回 400，不进入 Service。'])
 s=Sequence('07-structured-assign-sequence','结构化时序图  分配用户角色',['api: UserRoleController','service: StructuredUserRoleService','infra: AssignmentReader','infra: UserStore'],1620,'前提：Session 已认证、请求格式校验通过；Service 公共方法开启事务')
 s.msg(0,0,230,'CloneFactory.copy(command, dto)')
 s.msg(0,1,340,'assign(actorId, userId, command)')
 s.msg(1,2,420,'hasPermission(actorId, "rbac", "user.role.assign")')
 s.msg(2,1,480,'allowed',True)
 s.fragment(520,100,'break','没有分配权限')
 s.msg(1,0,590,'ForbiddenException -> 403',True)
 s.msg(1,3,690,'findById(actorId)；findById(userId)')
 s.msg(3,1,750,'actor、targetUser',True)
 s.msg(1,2,820,'findRole(roleId)')
 s.msg(2,1,880,'role',True)
 s.msg(1,1,945,'检查存在、同一非空部门、角色未重复')
 s.fragment(1030,100,'break','校验失败')
 s.msg(1,0,1100,'404 / 403 / 409；不写入',True)
 s.msg(1,3,1210,'addRole(userId, roleId)')
 s.msg(3,1,1270,'写入完成',True)
 s.msg(1,0,1390,'事务提交后返回成功',True)
 s.footer(['同部门是分配约束；是否能执行分配仍由角色中的 user.role.assign 权限决定。','SQL 或提交异常导致事务回滚并返回错误；适配器只执行读写，规则留在 Service。'])
 s=Sequence('08-ddd-assign-sequence','DDD 时序图  分配用户角色',['api: UserRoleController','application: DddUserRoleService','infra: AssignmentReader','infra: UserStore','domain: User','domain: RoleAssignmentRule'],1930,'前提与结构化版相同；应用服务负责事务，聚合与领域规则负责业务约束')
 s.msg(0,0,230,'CloneFactory.copy(command, dto)')
 s.msg(0,1,340,'assign(actorId, userId, command)')
 s.msg(1,2,420,'hasPermission(actorId, "rbac", "user.role.assign")')
 s.msg(2,1,480,'allowed',True)
 s.fragment(520,100,'break','没有分配权限')
 s.msg(1,0,590,'ForbiddenException -> 403',True)
 s.msg(1,3,690,'findById(actorId)；findById(userId)')
 s.msg(3,1,750,'actor、UserSnapshot（含角色 ID）',True)
 s.msg(1,2,860,'findRole(roleId)')
 s.msg(2,1,915,'角色摘要（含 departmentId）',True)
 s.fragment(950,100,'break','用户或角色不存在')
 s.msg(1,0,1020,'NotFoundException -> 404',True)
 s.msg(1,1,1080,'User.restore(id, departmentId, roleIds)')
 s.msg(1,5,1170,'requireSameDepartment(actor, user, role)')
 s.msg(1,4,1240,'assignRole(roleId)；检查重复')
 s.fragment(1300,100,'break','跨部门或重复分配')
 s.msg(1,0,1370,'403 / 409；不保存',True)
 s.msg(1,3,1480,'save(UserSnapshot)')
 s.msg(3,3,1550,'同步 user_role；CloneFactory 复制普通字段')
 s.msg(3,1,1660,'保存完成',True)
 s.msg(1,0,1770,'事务提交后返回成功',True)
 s.footer(['应用层将共享快照重建为 User，再将聚合转回快照保存；基础设施不依赖 DDD 领域类型。','应用服务检查访问权限与记录存在性；领域规则检查部门；User.assignRole 维护角色集合及防重。'])
 s=Sequence('09-authorization-sequence','共享操作鉴权时序图',['客户端','api: Controller / 安全入口','service: 被选中的用例服务','infra: RbacReader','infra: UserStore'],1390,'示例：删除本部门用户；两套实现使用同一 RBAC 授权来源')
 s.msg(0,1,230,'DELETE /users/{userId} + Session Cookie + CSRF Token')
 s.msg(1,1,290,'验证 Session、请求与 CSRF；读取 actorId')
 s.fragment(380,100,'break','Session 无效或 CSRF 校验失败')
 s.msg(1,0,450,'401 / 403',True)
 s.msg(1,2,560,'deleteUser(actorId, userId)')
 s.msg(2,3,640,'hasPermission(actorId, "rbac", "user.delete")')
 s.msg(3,2,700,'allowed',True)
 s.msg(2,4,770,'读取操作者与目标用户部门')
 s.fragment(820,110,'break','没有操作权限或超出部门范围')
 s.msg(2,1,900,'ForbiddenException -> 403',True)
 s.msg(2,4,1020,'同一事务：删除 user_role，再删除 app_user')
 s.msg(4,2,1080,'完成',True)
 s.msg(2,1,1150,'事务提交后成功',True)
 s.msg(1,0,1220,'204',True)
 s.footer(['RBAC 查询只经过 user_role、role_permission、permission；查询目标系统与权限编码。','本图省略 404 与数据库失败展开；分别返回未找到或回滚错误，不返回成功。'])

architecture();class_diagrams();activities();sequences()
# Validate IDs and references before exporting.
def walk(x):
 if isinstance(x,dict):
  yield x
  for v in x.values():yield from walk(v)
 elif isinstance(x,list):
  for v in x:yield from walk(v)
nodes=list(walk(PROJECT));ids=[n['_id'] for n in nodes if '_id' in n]
assert len(ids)==len(set(ids)), 'duplicate ids'
known=set(ids)
for n in nodes:
 if '$ref' in n:assert n['$ref'] in known,n
for d in DIAGRAMS:d.save()
(OUT/'rbac-design-v1.mdj').write_text(json.dumps(PROJECT,ensure_ascii=False,indent=2))
print(f'Generated {len(DIAGRAMS)} diagrams; {len(ids)} StarUML elements/views; all references resolve.')
