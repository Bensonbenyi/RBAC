"""Explicit coordinates for black-and-white UML. No Mermaid or automatic layout.
SVG/PNG previews and native StarUML models are generated from the same diagram data.
"""
from pathlib import Path
from html import escape
import json, math, subprocess

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'assets'/'v33'
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
  h=24+len(lines)*29;self.rect(x,y,w,h)
  for i,s in enumerate(lines):self.text(x+15,y+29+29*i,s,20,native=False)
  v=self.view('UMLNoteView',left=x,top=y,width=w,height=h,text='\n'.join(lines),wordWrap=True,font='PingFang SC;20;0')
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
  m['operations'].append(op)
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

def interaction_frame(d,owner,y,h,kind,guard):
 m=obj('UMLCombinedFragment',owner,interactionOperator=kind,operands=[])
 op=obj('UMLInteractionOperand',m,name='',guard=guard);m['operands'].append(op);owner['fragments'].append(m)
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
 operation_count=sum(v.get('_type')=='UMLOperation' for v in nodes)
 (OUT/'model-check.json').write_text(json.dumps({'diagrams':len(DIAGRAMS),'elements':len(ids),'references':'resolved','operationSignatures':operation_count},indent=2))
 print(f'{len(DIAGRAMS)} diagrams, {len(ids)} elements, {operation_count} operations; references resolved.')
