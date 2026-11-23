"""手工坐标时序图，同时生成 SVG 与 StarUML 图元。"""
import re
import draw_spec as u

D,ref,obj=u.D,u.ref,u.obj

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

class Sequence:
 def __init__(self,stem,title,parts,height=1000,created=None,compact_headers=False):
  height+=35
  created={i:y+35 for i,y in (created or {}).items()}
  self.owner=obj('UMLInteraction',u.MODEL,name=title,ownedElements=[],participants=[],messages=[],fragments=[])
  u.MODEL['ownedElements'].append(self.owner)
  self.d=D(stem,title,900,height,'UMLSequenceDiagram',self.owner)
  self.parts=[];self.n=0;self.created=created or {};self.activations=[];self.execution_views=[];self.hw={5:75,6:64}.get(len(parts),95)
  xs={2:[220,680],3:[120,450,780],4:[105,335,565,795],5:[90,270,450,630,810],6:[76,226,376,526,676,826]}[len(parts)]
  for i,((name,typ),x) in enumerate(zip(parts,xs)):
   u.classifier(typ);top=self.created.get(i,76)
   attr=obj('UMLAttribute',self.owner,name=name,type=ref(u.CLASSES[typ]));self.owner['ownedElements'].append(attr)
   m=obj('UMLLifeline',self.owner,name=name,represent=ref(attr));self.owner['participants'].append(m)
   v=self.d.view('UMLSeqLifelineView',m,left=x-self.hw,top=top,width=2*self.hw,height=height-top-35,subViews=[],stereotypeDisplay='label')
   c=u.nc(v,m,x-self.hw,top,2*self.hw,96)
   lp=obj('UMLLinePartView',v,model=ref(m),left=x,top=top+96,width=1,height=height-top-131,parentStyle=False,lineColor='#000000')
   v['subViews']=[c,lp];v['nameCompartment']=ref(c);v['linePart']=ref(lp)
   self.d.rect(x-self.hw,top,2*self.hw,96)
   if compact_headers:
    label=f'{name}: {typ}'
    size=min(20,int((2*self.hw-12)/tw(label,1)))
    self.d.text(x,top+55,label,size,True,native=False)
   elif typ=='CloneFactory':
    m['stereotype']='MetaClass';self.d.text(x,top+22,'«MetaClass»',18,True,native=False)
   else:self.d.text(x,top+22,name+':',20,True,native=False)
   if not compact_headers:
    words=re.findall('[A-Z][a-z0-9]*|[A-Z]+(?=[A-Z]|$)',typ) or [typ]
    header=[];s=''
    for word in words:
     if tw(s+word,20)>2*self.hw-15:header.append(s);s=word
     else:s+=word
    if s:header.append(s)
    for j,t in enumerate(header):self.d.text(x,top+47+j*23,t,20,True,native=False)
   self.d.line([(x,top+96),(x,height-35)],dash=True)
   self.parts.append((m,v,lp,x,typ))
 def msg(self,a,b,y,text,reply=False,end=None,create=False):
  y+=35
  if end is not None:end+=35
  if not reply:self.n+=1
  p,q=self.parts[a],self.parts[b];x,z=p[3],q[3]
  if reply:
   for execution in reversed(self.execution_views):
    if execution['participant']==a and not execution['closed']:
     execution['closed']=True
     if execution['start']<y:
      execution['view']['height']=y-execution['start']
      old=self.d.svg[execution['svg']]
      self.d.svg[execution['svg']]=re.sub(r'height="[\d.]+"',f'height="{y-execution["start"]}"',old)
      for i,(part,start,stop) in enumerate(self.activations):
       if part==a and start==execution['start']:self.activations[i]=(part,start,y)
     break
  if not reply and not create:
   end=end or y+38
   self.activations.append((b,y,end))
  def active(i):return any(j==i and start<=y<=stop for j,start,stop in self.activations)
  if a!=b:
   direction=1 if z>x else -1
   x+=direction*5 if active(a) else 0
   z-=direction*5 if active(b) else 0
  execution_svg=None
  if end and not reply and not create:
   execution_svg=len(self.d.svg);self.d.rect(q[3]-5,y,10,end-y)
  pts=[(x,y),(z-self.hw if create else z,y)] if a!=b else [(x+5,y),(x+65,y),(x+65,y+32),(x+10,y+32)]
  self.d.line(pts,'reply' if reply or create else 'self' if a==b else 'call',dash=reply or create)
  label=text if reply else f'{self.n}: {text}'
  width=min(740,abs(z-x)+180) if a!=b else 330
  tx=max(width/2+15,min(900-width/2-15,(x+z)/2)) if a!=b else x+90
  ss=wrap(label,width,20)
  for i,t in enumerate(ss):self.d.text(tx,y-15-(len(ss)-i-1)*30,t,20,a!=b,native=False,bg=True)
  m=obj('UMLMessage',self.owner,name=text,source=ref(p[0]),target=ref(q[0]),messageSort='createMessage' if create else 'reply' if reply else 'synchCall',sequenceNumber='' if reply else str(self.n))
  self.owner['messages'].append(m)
  v=self.d.view('UMLSeqMessageView',m,tail=ref(p[2]),head=ref(q[1] if create else q[2]),points=';'.join(f'{xx}:{yy}' for xx,yy in pts),subViews=[],showSequenceNumber=not reply)
  for key,txt,visible in [('nameLabel',text,True),('stereotypeLabel','',False),('propertyLabel','',False)]:
   lab=u.label(v,txt,tx-width/2,y-25*len(ss)-10,width,24*len(ss),typ='EdgeLabelView',model=ref(m),hostEdge=ref(v),edgePosition=1,distance=18,alpha=1.57,visible=visible)
   lab['font']='PingFang SC;20;0';lab['wordWrap']=True;v['subViews'].append(lab);v[key]=ref(lab)
  activation=obj('UMLActivationView',v,model=ref(m),left=q[3]-5,top=y,width=10,height=max(25,(end or y+25)-y),visible=not(reply or create),parentStyle=False,fillColor='#ffffff',lineColor='#000000')
  v['subViews'].append(activation);v['activation']=ref(activation)
  if execution_svg is not None:self.execution_views.append({'participant':b,'start':y,'view':activation,'svg':execution_svg,'closed':False})
 def frame(self,y,h,kind,guard):
  u.interaction_frame(self.d,self.owner,y+35,h,kind,guard)
 def note(self,y,text):
  y+=35
  ss=wrap(text,800,20);h=len(ss)*25+28
  self.d.rect(35,y,830,h)
  for i,t in enumerate(ss):self.d.text(50,y+29+i*25,t,20,native=False)
  self.d.view('UMLNoteView',left=35,top=y,width=830,height=h,text=text,wordWrap=True,font='PingFang SC;20;0')
