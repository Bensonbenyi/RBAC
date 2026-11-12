"""Explicit coordinates for black-and-white UML. No Mermaid or automatic layout.
SVG/PNG previews and native StarUML models are generated from the same diagram data.
"""
from pathlib import Path
from html import escape
import json, math, subprocess

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'assets'/'spec'
OUT.mkdir(parents=True,exist_ok=True)
FONT="'PingFang SC','Microsoft YaHei','Noto Sans CJK SC',system-ui,sans-serif"
counter=0
def uid():
 global counter
 counter+=1
 return f'rbac-spec-v3-{counter:06d}'
def ref(o):return {'$ref':o['_id'] if isinstance(o,dict) else o}
def obj(t,parent=None,**kw):
 o={'_type':t,'_id':uid()}
 if parent:o['_parent']=ref(parent)
 o.update(kw);return o
PROJECT=obj('Project',name='RBAC 需求与详细设计 第三版')
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

# UML elements are shared by views and messages. Coordinates are explicitly authored.
CLASSES={}
OPS={}
def classifier(name,methods=(),attributes=(),interface=False,stereo=''):
 if name in CLASSES:return CLASSES[name]
 m=obj('UMLInterface' if interface else 'UMLClass',MODEL,name=name,attributes=[],operations=[],ownedElements=[])
 if stereo:m['stereotype']=stereo
 MODEL['ownedElements'].append(m);CLASSES[name]=m
 for field,typ in attributes:m['attributes'].append(obj('UMLAttribute',m,name=field,type=typ,visibility='private'))
 for name_,args,ret in methods:
  op=obj('UMLOperation',m,name=name_,visibility='public',parameters=[],isStatic=stereo=='metaclass')
  for part in args.split(';') if args else []:
   n,t=part.split(':',1);op['parameters'].append(obj('UMLParameter',op,name=n.strip(),type=t.strip(),direction='in'))
  if ret:op['parameters'].append(obj('UMLParameter',op,name='',type=ret,direction='return'))
  m['operations'].append(op);OPS[(name,name_)]=op
 return m

def klass(d,name,x,y,w):
 m=CLASSES[name];attrs=m['attributes'];ops=m['operations'];st=m.get('stereotype','')
 it=m['_type']=='UMLInterface';header=60 if it or st else 40
 h=header+max(16,len(attrs)*25+14)+max(16,len(ops)*28+14)
 v=d.view('UMLInterfaceView' if it else 'UMLClassView',m,left=x,top=y,width=w,height=h,subViews=[],showType=True,showOperationSignature=True,showVisibility=True,stereotypeDisplay='label')
 c=nc(v,m,x,y,w,header);v['subViews'].append(c);v['nameCompartment']=ref(c)
 d.rect(x,y,w,h)
 if it or st:d.text(x+w/2,y+22,'«interface»' if it else f'«{st}»',16,True,native=False)
 d.text(x+w/2,y+header-13,name,18,True,True,False)
 at=y+header;d.line([(x,at),(x+w,at)])
 ac=obj('UMLAttributeCompartmentView',v,model=ref(m),left=x,top=at,width=w,height=max(16,len(attrs)*25+14),parentStyle=True,subViews=[])
 for i,a in enumerate(attrs):
  tx=f'- {a["name"]}: {a["type"]}';d.text(x+10,at+24+i*25,tx,16,native=False)
  ac['subViews'].append(label(ac,tx,x+8,at+5+i*25,w-16,typ='UMLAttributeView',model=ref(a),horizontalAlignment=0))
 v['subViews'].append(ac);v['attributeCompartment']=ref(ac)
 oy=at+ac['height'];d.line([(x,oy),(x+w,oy)])
 oc=obj('UMLOperationCompartmentView',v,model=ref(m),left=x,top=oy,width=w,height=h-(oy-y),parentStyle=True,subViews=[])
 for i,o in enumerate(ops):
  pars=[f'{a["name"]}: {a["type"]}' for a in o['parameters'] if a['direction']!='return']
  rt=next((a['type'] for a in o['parameters'] if a['direction']=='return'),'void')
  tx=f'{"-" if o.get("visibility")=="private" else "+"} {o["name"]}({", ".join(pars)}): {rt}'
  if o['name']==name:tx=tx.rsplit(': ',1)[0]
  d.text(x+10,oy+25+i*28,tx,16,native=False)
  if o.get('isStatic'):d.line([(x+10,oy+29+i*28),(x+min(w-10,len(tx)*8),oy+29+i*28)])
  oc['subViews'].append(label(oc,tx,x+8,oy+6+i*28,w-16,typ='UMLOperationView',model=ref(o),horizontalAlignment=0))
 v['subViews'].append(oc);v['operationCompartment']=ref(oc)
 for field,ct in [('receptionCompartment','UMLReceptionCompartmentView'),('templateParameterCompartment','UMLTemplateParameterCompartmentView')]:
  c=obj(ct,v,model=ref(m),left=x,top=oy,width=w,height=0,visible=False,parentStyle=True);v['subViews'].append(c);v[field]=ref(c)
 return (m,v)

def assoc(d,a,b,pts,role='',mult='1'):
 m=d.model('UMLAssociation',name='',end1=None,end2=None)
 m['end1']=obj('UMLAssociationEnd',m,reference=ref(a[0]),name='',navigable='unspecified',multiplicity='')
 m['end2']=obj('UMLAssociationEnd',m,reference=ref(b[0]),name=role,navigable='navigable',multiplicity=mult)
 d.line(pts,'dep')
 v=d.view('UMLAssociationView',m,tail=ref(a[1]),head=ref(b[1]),points=';'.join(f'{x}:{y}' for x,y in pts),lineStyle=1)
 if role:
  x,y=pts[-1];prev=pts[-2];tx=x+12 if prev[0]>x else (x-len(role)*8-22 if prev[0]<x else x+12);d.text(tx,y-16,'-'+role,15,native=False)
 return v

def define_classes():
 classifier('LoginCommand',attributes=[('username','String'),('password','String'),('systemKey','String')])
 classifier('LoginResult',attributes=[('userId','Long'),('systemKey','String')])
 classifier('AssignRoleCommand',attributes=[('roleId','Long')])
 classifier('UserData',attributes=[('id','Long'),('username','String'),('phone','String'),('departmentId','Long'),('roleIds','Set<Long>')])
 classifier('RoleData',attributes=[('id','Long'),('departmentId','Long')])
 classifier('AuthUseCase',[('login','cmd: LoginCommand','LoginResult')],interface=True)
 classifier('UserRoleUseCase',[('assign','actorId: Long;userId: Long;cmd: AssignRoleCommand','void')],interface=True)
 classifier('AuthorizationUseCase',[('check','userId: Long;systemKey: String;code: String','boolean')],interface=True)
 classifier('AuthController',[('login','dto: LoginDTO;request: HttpServletRequest','LoginVO'),('logout','session: HttpSession','void')])
 classifier('UserRoleController',[('assign','userId: Long;dto: AssignRoleDTO;actor: CurrentUser','void')])
 classifier('UserStore',[('findById','id: Long','UserData'),('findByUsername','name: String','UserData'),('addRole','userId: Long;roleId: Long','void')],interface=True)
 classifier('RoleReader',[('findById','id: Long','RoleData')],interface=True)
 classifier('RbacReader',[('hasPermission','userId: Long;system: String;code: String','boolean')],interface=True)
 classifier('StructuredAuthService',[('login','cmd: LoginCommand','LoginResult')])
 classifier('DddAuthService',[('login','cmd: LoginCommand','LoginResult')])
 classifier('StructuredUserRoleService',[
  ('assign','actorId: Long;userId: Long;cmd: AssignRoleCommand','void'),
  ('requireSameDepartment','actor: UserData;target: UserData;role: RoleData','void'),
  ('requireUnassigned','target: UserData;roleId: Long','void')])
 classifier('DddUserRoleService',[('assign','actorId: Long;userId: Long;cmd: AssignRoleCommand','void')])
 classifier('User',[
  ('User','id: Long;dept: Long;roles: Set<Long>',''),
  ('assignRole','role: Role;actorDept: Long','void')],attributes=[('id','Long'),('departmentId','Long'),('roleIds','Set<Long>')])
 for n in ['requireSameDepartment','requireUnassigned']:OPS[('StructuredUserRoleService',n)]['visibility']='private'
 classifier('Role',[('Role','id: Long;dept: Long','')],attributes=[('id','Long'),('departmentId','Long')])
 classifier('JpaUserStore',[('findById','id: Long','UserData'),('findByUsername','name: String','UserData'),('addRole','userId: Long;roleId: Long','void')])
 classifier('CloneFactory',[('copy','target: T;source: Object','T')],stereo='metaclass')
 classifier('HttpServletRequest',[('changeSessionId','','String'),('getSession','','HttpSession')])
 classifier('HttpSession',[('setAttribute','name: String;value: Object','void'),('invalidate','','void')])
 classifier('LoginDTO',attributes=[('username','String'),('password','String'),('systems','List<String>')])
 classifier('LoginVO',attributes=[('userId','Long'),('systemKey','String')])
 classifier('AssignRoleDTO',attributes=[('roleId','Long')])


def packages():
 d=D('01-structured-packages','结构化设计 包图',1100,760,'UMLPackageDiagram')
 api=d.node('api',50,110,300,180,'UMLPackage',body=['auth / user / role','department / permission / system','dto / vo'])
 port=d.node('service.port',680,110,370,180,'UMLPackage',body=['用例接口','command / result','UserStore / RoleReader / RbacReader'])
 svc=d.node('service.structured',50,450,400,190,'UMLPackage',body=['auth / user / role','组织与权限目录管理','事务脚本执行校验和持久化编排'])
 infra=d.node('infrastructure',680,450,370,190,'UMLPackage',body=['persistence / jpa / po','security / logging','config'])
 d.edge(api,port,[(350,190),(680,190)])
 d.edge(svc,port,[(450,510),(550,510),(550,250),(680,250)])
 d.edge(infra,port,[(865,450),(865,290)])
 d.text(550,709,'虚线开箭头指向被依赖包。配置选择 structured 时装配事务脚本服务。',17,True)
 d=D('02-ddd-packages','DDD 设计 包图',1100,890,'UMLPackageDiagram')
 api=d.node('api',40,110,300,150,'UMLPackage',body=['Controller / dto / vo'])
 port=d.node('service.port',700,110,350,180,'UMLPackage',body=['用例接口与数据契约','持久化端口'])
 app=d.node('service.ddd.application',40,400,400,160,'UMLPackage',body=['事务边界与用例编排','Auth / UserRole / Role …'])
 domain=d.node('service.ddd.domain',40,680,400,130,'UMLPackage',body=['User / Role 与业务规则'])
 infra=d.node('infrastructure',700,580,350,170,'UMLPackage',body=['persistence / security','logging / config'])
 d.edge(api,port,[(340,190),(700,190)])
 d.edge(app,port,[(440,450),(550,450),(550,250),(700,250)])
 d.edge(app,domain,[(240,560),(240,680)])
 d.edge(infra,port,[(875,580),(875,290)])
 d.text(550,855,'领域层保持框架无关。配置选择 ddd 时装配应用服务，基础设施实现共享端口。',17,True)


def classes():
 d=D('03-auth-classes','共享登录契约与实现类图',1600,910)
 ctrl=klass(d,'AuthController',40,110,660)
 uc=klass(d,'AuthUseCase',940,110,620)
 st=klass(d,'StructuredAuthService',40,390,660)
 dd=klass(d,'DddAuthService',940,390,620)
 us=klass(d,'UserStore',40,650,660)
 rb=klass(d,'RbacReader',940,650,620)
 assoc(d,ctrl,uc,[(700,170),(940,170)],'authUseCase')
 d.edge(st,uc,[(700,440),(810,440),(810,210),(940,210)],True)
 d.edge(dd,uc,[(1250,390),(1250,228)],True)
 assoc(d,st,us,[(190,490),(190,650)],'userStore')
 assoc(d,dd,rb,[(1270,490),(1270,650)],'rbacReader')
 assoc(d,st,rb,[(700,470),(810,470),(810,600),(1120,600),(1120,650)],'rbacReader')
 assoc(d,dd,us,[(940,475),(860,475),(860,590),(550,590),(550,650)],'userStore')
 d=D('04-structured-classes','结构化设计 用户角色分配类图',1780,990)
 ctrl=klass(d,'UserRoleController',40,110,800)
 uc=klass(d,'UserRoleUseCase',1000,110,740)
 svc=klass(d,'StructuredUserRoleService',40,420,800)
 store=klass(d,'UserStore',1000,420,740)
 role=klass(d,'RoleReader',40,780,450)
 rbac=klass(d,'RbacReader',650,780,1090)
 assoc(d,ctrl,uc,[(840,170),(1000,170)],'userRoleUseCase')
 d.edge(svc,uc,[(840,450),(900,450),(900,210),(1000,210)],True)
 assoc(d,svc,store,[(840,540),(1000,540)],'userStore')
 assoc(d,svc,role,[(250,574),(250,780)],'roleReader')
 assoc(d,svc,rbac,[(780,574),(780,780)],'rbacReader')
 d=D('05-ddd-classes','DDD 设计 用户角色分配类图',1900,1250)
 uc=klass(d,'UserRoleUseCase',40,110,840)
 app=klass(d,'DddUserRoleService',40,390,840)
 store=klass(d,'UserStore',40,700,840)
 reader=klass(d,'RoleReader',40,1030,840)
 user=klass(d,'User',1040,110,820)
 role=klass(d,'Role',1040,620,820)
 rbac=klass(d,'RbacReader',1040,1030,820)
 d.edge(app,uc,[(460,390),(460,228)],True)
 d.edge(app,user,[(880,450),(960,450),(960,230),(1040,230)])
 d.edge(app,role,[(880,470),(970,470),(970,700),(1040,700)])
 d.edge(user,role,[(1450,309),(1450,620)])
 assoc(d,app,store,[(260,488),(260,700)],'userStore')
 assoc(d,app,reader,[(100,488),(100,620),(20,620),(20,980),(260,980),(260,1030)],'roleReader')
 assoc(d,app,rbac,[(880,480),(930,480),(930,1090),(1040,1090)],'rbacReader')

class Seq:
 def __init__(self,stem,title,participants,h=1200,created=None):
  self.owner=obj('UMLInteraction',MODEL,name=title,ownedElements=[],participants=[],messages=[],fragments=[])
  MODEL['ownedElements'].append(self.owner)
  self.d=D(stem,title,len(participants)*310+60,h,'UMLSequenceDiagram',self.owner)
  self.l=[];self.calls=[];self.n=0
  for i,(name,typ) in enumerate(participants):
   x=185+i*310;classifier(typ);top=(created or {}).get(i,132)-32
   attr=obj('UMLAttribute',self.owner,name=name,type=ref(CLASSES[typ]));self.owner['ownedElements'].append(attr)
   part=obj('UMLLifeline',self.owner,name=name,represent=ref(attr));self.owner['participants'].append(part)
   v=self.d.view('UMLSeqLifelineView',part,left=x-140,top=top,width=280,height=h-top-40,subViews=[],stereotypeDisplay='label')
   c=nc(v,part,x-140,top,280,65)
   lp=obj('UMLLinePartView',v,model=ref(part),left=x,top=top+65,width=1,height=h-top-105,parentStyle=False,lineColor='#000000')
   v['subViews']=[c,lp];v['nameCompartment']=ref(c);v['linePart']=ref(lp)
   self.d.rect(x-140,top,280,65)
   if typ=='CloneFactory':self.d.text(x,top+22,'«metaclass»',16,True,native=False)
   self.d.text(x,top+48, f'{name}: {typ}' if name else f': {typ}',17,True,native=False)
   self.d.line([(x,top+65),(x,h-40)],dash=True)
   self.l.append((part,v,lp,x,typ))
 def message(self,s,t,y,name,method=None,end=None,reply=False,create=False):
  self.n+=1;a,b=self.l[s],self.l[t];x,z=a[3],b[3]
  pts=[(x,y),(z-140 if create else z,y)] if s!=t else [(x+6,y),(x+70,y),(x+70,y+28),(x+12,y+28)]
  self.d.line(pts,'reply' if reply or create else ('self' if s==t else 'call'),dash=reply or create)
  tx=(x+z)/2 if s!=t else x+85
  self.d.text(tx,y-13 if s!=t else y+15,f'{self.n}: {name}',17,mid=s!=t,native=False,bg=True)
  m=obj('UMLMessage',self.owner,name=name,source=ref(a[0]),target=ref(b[0]),messageSort='createMessage' if create else ('reply' if reply else 'synchCall'),sequenceNumber=str(self.n))
  if method:
   op=OPS.get((b[4],method));assert op,(b[4],method);m['signature']=ref(op)
  self.owner['messages'].append(m)
  v=self.d.view('UMLSeqMessageView',m,tail=ref(a[2]),head=ref(b[1] if create else b[2]),points=';'.join(f'{px}:{py}' for px,py in pts),subViews=[],showSequenceNumber=True)
  for key,txt,vis in [('nameLabel',name,True),('stereotypeLabel','',False),('propertyLabel','',False)]:
   lab=label(v,txt,min(x,z)+10,y-30,max(220,abs(x-z)-20),typ='EdgeLabelView',model=ref(m),hostEdge=ref(v),edgePosition=1,distance=18,alpha=math.pi/2,visible=vis)
   v['subViews'].append(lab);v[key]=ref(lab)
  top=y+28 if s==t else y
  act=obj('UMLActivationView',v,model=ref(m),left=z+(6 if s==t else -6),top=top,width=12,height=max(24,(end or top+28)-top),visible=not (reply or create),parentStyle=False,fillColor='#ffffff',lineColor='#000000')
  v['subViews'].append(act);v['activation']=ref(act)
  if not (reply or create):
   svg=f'<rect x="{act["left"]}" y="{act["top"]}" width="12" height="{act["height"]}" fill="white" stroke="black"/>'
   self.d.svg.insert(len(self.l)*5,svg)
  self.calls.append((m,b[4],method))
 def fragment(self,y,h,kind,guard):
  d=self.d;m=obj('UMLCombinedFragment',self.owner,interactionOperator=kind,operands=[])
  op=obj('UMLInteractionOperand',m,name='',guard=guard);m['operands'].append(op);self.owner['fragments'].append(m)
  x=35;w=d.w-70
  d.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="none" stroke="black"/>')
  d.line([(x,y+30),(x+63,y+30),(x+78,y+15),(x+78,y)])
  d.text(x+12,y+22,kind,17,bold=True,native=False);d.text(x+95,y+25,f'[{guard}]',17,native=False,bg=True)
  v=d.view('UMLCombinedFragmentView',m,left=x,top=y,width=w,height=h,subViews=[])
  for key,txt,vis in [('nameLabel','',False),('frameTypeLabel',kind,True)]:
   l=label(v,txt,x+6,y+4,70,visible=vis);v['subViews'].append(l);v[key]=ref(l)
  comp=obj('UMLInteractionOperandCompartmentView',v,model=ref(m),left=x,top=y+30,width=w,height=h-30,subViews=[],parentStyle=True)
  ov=obj('UMLInteractionOperandView',comp,model=ref(op),left=x,top=y+30,width=w,height=h-30,subViews=[],parentStyle=True)
  l=label(ov,f'[{guard}]',x+90,y+33,w-100);ov['subViews'].append(l);ov['guardLabel']=ref(l)
  comp['subViews'].append(ov);v['subViews'].append(comp);v['operandCompartment']=ref(comp)


def sequences():
 s=Seq('06-login-sequence','登录成功 顺序图', [('ctrl','AuthController'),('','CloneFactory'),('service','AuthUseCase'),('users','UserStore'),('rbac','RbacReader'),('request','HttpServletRequest'),('session','HttpSession')],1220)
 s.message(0,1,235,'cmd = copy(command, dto)','copy',end=265)
 s.message(0,2,330,'result = login(cmd)','login',end=790)
 s.message(2,3,410,'user = findByUsername(cmd.username)','findByUsername',end=460)
 s.message(3,2,460,'user',reply=True)
 s.d.note(820,500,570,['凭据匹配由选中的登录服务执行。','账号或凭据错误：抛出 UnauthorizedException。'])
 s.message(2,4,640,'allowed = hasPermission(user.id, cmd.systemKey, "login")','hasPermission',end=690)
 s.message(4,2,690,'allowed',reply=True)
 s.d.note(1020,725,730,['allowed 为 false 时抛出 ForbiddenException。','失败交互及 HTTP 映射见登录活动图和接口表。'])
 s.message(2,0,790,'result',reply=True)
 s.message(0,5,870,'session = getSession()','getSession',end=900)
 s.message(0,5,950,'changeSessionId()','changeSessionId',end=980)
 s.message(0,6,1030,'setAttribute("identity", result)','setAttribute',end=1060)
 s.message(0,1,1130,'vo = copy(loginVO, result)','copy',end=1160)
 s=Seq('07-structured-assign','结构化设计 分配角色顺序图',[('ctrl','UserRoleController'),('service','StructuredUserRoleService'),('rbac','RbacReader'),('users','UserStore'),('roles','RoleReader')],1500)
 s.message(0,1,235,'assign(actorId, userId, cmd)','assign',end=1410)
 s.message(1,2,320,'allowed = hasPermission(actorId, "rbac", "user.role.assign")','hasPermission',end=360)
 s.fragment(390,115,'break','!allowed')
 s.message(1,0,465,'ForbiddenException',reply=True)
 s.message(1,3,565,'actor = findById(actorId)','findById',end=595)
 s.message(1,3,650,'target = findById(userId)','findById',end=680)
 s.message(1,4,735,'role = findById(cmd.roleId)','findById',end=765)
 s.fragment(800,115,'break','actor == null || target == null || role == null')
 s.message(1,0,875,'NotFoundException',reply=True)
 s.message(1,1,980,'requireSameDepartment(actor, target, role)','requireSameDepartment',end=1050)
 s.message(1,1,1110,'requireUnassigned(target, cmd.roleId)','requireUnassigned',end=1180)
 s.message(1,3,1260,'addRole(userId, cmd.roleId)','addRole',end=1320)
 s.message(1,0,1410,'void',reply=True)
 s=Seq('08-ddd-assign','DDD 设计 分配角色顺序图',[('ctrl','UserRoleController'),('app','DddUserRoleService'),('rbac','RbacReader'),('users','UserStore'),('roles','RoleReader'),('user','User'),('role','Role')],1580,created={5:955,6:1055})
 s.message(0,1,235,'assign(actorId, userId, cmd)','assign',end=1490)
 s.message(1,2,320,'allowed = hasPermission(actorId, "rbac", "user.role.assign")','hasPermission',end=350)
 s.fragment(385,105,'break','!allowed');s.message(1,0,455,'ForbiddenException',reply=True)
 s.message(1,3,550,'actor = findById(actorId)','findById',end=580)
 s.message(1,3,640,'target = findById(userId)','findById',end=670)
 s.message(1,4,730,'data = findById(cmd.roleId)','findById',end=760)
 s.fragment(790,105,'break','actor == null || target == null || data == null');s.message(1,0,860,'NotFoundException',reply=True)
 s.message(1,5,955,'User(target.id, target.departmentId, target.roleIds)','User',create=True)
 s.message(1,6,1055,'Role(data.id, data.departmentId)','Role',create=True)
 s.message(1,5,1160,'assignRole(role, actor.departmentId)','assignRole',end=1220)
 s.d.note(1570,1250,560,['assignRole 校验同部门与重复分配。','失败抛出 Forbidden / ConflictException。'])
 s.message(1,3,1380,'addRole(userId, cmd.roleId)','addRole',end=1420)
 s.message(1,0,1490,'void',reply=True)
 # Detail of the live RBAC query, common to both implementations.
 classifier('AuthorizationController',[('check','systemKey: String;code: String','boolean')])
 s=Seq('09-authorization','当前权限查询 顺序图',[('api','AuthorizationController'),('service','AuthorizationUseCase'),('rbac','RbacReader')],650)
 s.message(0,1,235,'allowed = check(userId, systemKey, code)','check',end=530)
 s.message(1,2,335,'allowed = hasPermission(userId, systemKey, code)','hasPermission',end=430)
 s.message(2,1,430,'allowed',reply=True);s.message(1,0,530,'allowed',reply=True)

# Reuse standard activity model helpers, with corrections to guard placement.
class Activity:
 def __init__(self,stem,title,h):
  self.owner=obj('UMLActivity',MODEL,name=title,ownedElements=[],nodes=[],edges=[])
  MODEL['ownedElements'].append(self.owner)
  self.d=D(stem,title,1060,h,'UMLActivityDiagram',self.owner,'两套实现采用同一业务规则')
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
 a=Activity('10-login-activity','登录活动图',1360)
 a.node('start','UMLInitialNode',300,115,20,20)
 a.node('request','UMLAction',300,200,440,65,'接收用户名、password、单个目标系统')
 a.node('valid','UMLDecisionNode',300,315,220,90,'参数有效？')
 a.node('load','UMLAction',300,450,440,85,'按用户名读取用户')
 a.node('identity','UMLDecisionNode',300,585,220,90,'手机号匹配？')
 a.node('permission','UMLAction',300,720,440,85,'按用户—角色—权限查询\n目标系统的 login 权限')
 a.node('allowed','UMLDecisionNode',300,855,220,90,'拥有登录权限？')
 a.node('session','UMLAction',300,995,440,85,'更新 Session 标识并保存身份\nSession 只保存 userId、systemKey')
 a.node('response','UMLAction',300,1140,440,85,'返回结果并设置会话 Cookie')
 a.node('end','UMLActivityFinalNode',300,1270,30,30)
 order=[a.start,a.request,a.valid,a.load,a.identity,a.permission,a.allowed,a.session,a.response,a.end]
 for p,q in zip(order,order[1:]):a.edge(p,q,'是' if p in [a.valid,a.identity,a.allowed] else '')
 a.fail(a.valid,315,'返回 400：参数错误');a.fail(a.identity,585,'返回 401：账号或凭据错误');a.fail(a.allowed,855,'返回 403：无系统登录权限')
 a.d.text(530,1325,'password 的内容为手机号；日志不记录凭据、完整手机号或 Session ID。',17,True)
 a=Activity('11-assign-activity','分配角色活动图',1480)
 a.node('start','UMLInitialNode',300,115,20,20)
 items=[('request',200,'UMLAction','读取已登录操作者\n校验目标 userId 与 roleId'),('perm',335,'UMLDecisionNode','有分配权限？'),('load',470,'UMLAction','读取操作者、目标用户与角色'),('exists',605,'UMLDecisionNode','记录均存在？'),('depart',740,'UMLDecisionNode','同一非空部门？'),('duplicate',875,'UMLDecisionNode','尚未分配？'),('write',1010,'UMLAction','在事务内写入 user_role\n由选中的服务实现完成'),('result',1155,'UMLAction','事务提交后返回成功')]
 order=[a.start]
 for key,y,t,txt in items:order.append(a.node(key,t,300,y,220 if t=='UMLDecisionNode' else 440,90 if t=='UMLDecisionNode' else 85,txt))
 order.append(a.node('end','UMLActivityFinalNode',300,1280,30,30))
 for p,q in zip(order,order[1:]):a.edge(p,q,'是' if p[0]['_type']=='UMLDecisionNode' else '')
 for dec,y,s in [(a.perm,335,'403：无 user.role.assign'),(a.exists,605,'404：用户或角色不存在'),(a.depart,740,'403：超出部门范围'),(a.duplicate,875,'409：角色已分配')]:a.fail(dec,y,s)
 a.d.note(80,1350,900,['操作者 ID 取自 Session，不接受前端伪造；本图前提是已登录且通过请求格式校验。','权限、记录、部门、重复校验和写入在同一事务内；数据库保留联合主键防止重复。'])


def finish():
 for d in DIAGRAMS:d.save()
 def walk(v):
  if isinstance(v,dict):
   yield v
   for a in v.values():yield from walk(a)
  elif isinstance(v,list):
   for a in v:yield from walk(a)
 nodes=list(walk(PROJECT));ids=[v['_id'] for v in nodes if '_id' in v];assert len(ids)==len(set(ids))
 for v in nodes:
  if '$ref' in v:assert v['$ref'] in ids,v
 (OUT/'rbac-spec.mdj').write_text(json.dumps(PROJECT,ensure_ascii=False,indent=2))
 (OUT/'model-check.json').write_text(json.dumps({'diagrams':len(DIAGRAMS),'elements':len(ids),'references':'resolved','operationSignatures':len(OPS)},indent=2))
 print(f'{len(DIAGRAMS)} diagrams, {len(ids)} elements, {len(OPS)} operations; references resolved.')

if __name__=='__main__':
 define_classes();packages();classes();sequences();activities();finish()
