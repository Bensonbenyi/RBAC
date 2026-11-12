"""手工坐标绘图：不做自动布局；同步导出 SVG 与 StarUML 基本图元。"""
from pathlib import Path
from html import escape
import json
import math
import subprocess

OUT = Path(__file__).resolve().parents[1] / 'assets'
FONT = "'PingFang SC','Microsoft YaHei','Noto Sans CJK SC',system-ui,sans-serif"

class Drawing:
    def __init__(self, width, height, name):
        self.width, self.height, self.name = width, height, name
        self.svg, self.views = [], []
    def view(self, typ, **props):
        v = dict(_type=typ, _id=f'{self.name}-{len(self.views)}',
                 _parent={'$ref':self.name}, parentStyle=False, showShadow=False,
                 lineColor='#000000', fontColor='#000000', fillColor='#ffffff', font='PingFang SC;18;0')
        v.update(props); self.views.append(v)
    def text(self, x,y,s,size=18,anchor='start',bold=False,key=False,width=None):
        width = width or max(60,len(s)*size)
        self.svg.append(f'<text x="{x}" y="{y}" font-size="{size}" text-anchor="{anchor}" font-weight="{600 if bold else 400}"'+(' text-decoration="underline"' if key else '')+f'>{escape(s)}</text>')
        left = x-width/2 if anchor=='middle' else x
        self.view('UMLTextView',left=left,top=y-size,width=width,height=size+8,text=s,
                  wordWrap=False,horzAlign=2 if anchor=='middle' else 0,vertAlign=5,font=f'PingFang SC;{size};{1 if bold else 0}')
        if key:
            self.line([(x-len(s)*size/2,y+4),(x+len(s)*size/2,y+4)],svg=False)
    def rect(self,x,y,w,h):
        self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="white" stroke="black" stroke-width="1.5"/>')
        self.view('RectangleView',left=x,top=y,width=w,height=h)
    def ellipse(self,x,y,rx,ry):
        self.svg.append(f'<ellipse cx="{x}" cy="{y}" rx="{rx}" ry="{ry}" fill="white" stroke="black" stroke-width="1.5"/>')
        self.view('EllipseView',left=x-rx,top=y-ry,width=rx*2,height=ry*2)
    def line(self,points,dash=False,svg=True):
        if svg:
            self.svg.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in points)+'" fill="none" stroke="black" stroke-width="1.6"'+(' stroke-dasharray="7 5"' if dash else '')+'/>')
        self.view('FreelineEdgeView',points=';'.join(f'{x}:{y}' for x,y in points),lineStyle=1,lineMode=2 if dash else 0)
    def diamond(self,x,y,label):
        self.line([(x-76,y),(x,y-46),(x+76,y),(x,y+46),(x-76,y)])
        self.text(x,y+7,label,21,anchor='middle')
        return (x,y,76,46,'diamond')
    def entity(self,x,y,label):
        self.rect(x-83,y-32,166,64);self.text(x,y+8,label,25,anchor='middle')
        return (x,y,83,32,'rect')
    def attr(self,e,x,y,label,key=False):
        a=(x,y,max(66,len(label)*11+18),27,'ellipse')
        self.join(e,a);self.ellipse(x,y,a[2],a[3]);self.text(x,y+7,label,20,anchor='middle',key=key)
    @staticmethod
    def boundary(shape,target):
        x,y,rx,ry,kind=shape; dx,dy=target[0]-x,target[1]-y
        if kind=='ellipse': t=1/math.sqrt((dx/rx)**2+(dy/ry)**2)
        elif kind=='diamond':t=1/(abs(dx)/rx+abs(dy)/ry)
        else:t=min(rx/abs(dx) if dx else 1e9,ry/abs(dy) if dy else 1e9)
        return x+dx*t,y+dy*t
    def join(self,a,b,total=False):
        p=self.boundary(a,b);q=self.boundary(b,a)
        if total:
            dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy);nx,ny=-dy/length*3,dx/length*3
            def clipped(shape, ox, oy, vx, vy):
                cx,cy,rx,ry,kind=shape
                def inside(t):
                    xx,yy=(ox+vx*t)/rx,(oy+vy*t)/ry
                    return (abs(xx)+abs(yy)<=1) if kind=='diamond' else (abs(xx)<=1 and abs(yy)<=1)
                lo,hi=0,1
                for _ in range(50):
                    mid=(lo+hi)/2
                    if inside(mid):lo=mid
                    else:hi=mid
                return cx+ox+vx*lo,cy+oy+vy*lo
            for sign in (-1,1):
                pp=clipped(a,sign*nx,sign*ny,dx,dy)
                qq=clipped(b,sign*nx,sign*ny,-dx,-dy)
                self.line([pp,qq])
        else:self.line([p,q])
    def end(self,x,y,dx,dy,kind):
        def p(u,v=0):return x+dx*u-dy*v,y+dy*u+dx*v
        def bar(u):self.line([p(u,-8),p(u,8)])
        if kind=='one':bar(9);bar(19)
        elif kind=='optional':bar(9);cx,cy=p(27);self.ellipse(cx,cy,5,5)
        elif kind=='many':
            self.line([p(0,-9),p(19),p(0,9)]);cx,cy=p(32);self.ellipse(cx,cy,5,5)
    def relation(self,points,a,b,ident=False):
        self.line(points,dash=not ident)
        for endpoint,near,kind in [(points[0],points[1],a),(points[-1],points[-2],b)]:
            dx,dy=near[0]-endpoint[0],near[1]-endpoint[1];length=math.hypot(dx,dy)
            self.end(*endpoint,dx/length,dy/length,kind)
    def save(self,stem):
        OUT.mkdir(exist_ok=True)
        svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" viewBox="0 0 {self.width} {self.height}"><style>text{{font-family:{FONT};fill:black}}</style><rect width="100%" height="100%" fill="white"/>'+''.join(self.svg)+'</svg>'
        (OUT/f'{stem}.svg').write_text(svg)
        subprocess.run(['rsvg-convert', '-z', '1.3', str(OUT/f'{stem}.svg'), '-o', str(OUT/f'{stem}.png')], check=True)
        project={'_type':'Project','_id':self.name+'-project','name':stem,'ownedElements':[{'_type':'UMLModel','_id':self.name+'-model','_parent':{'$ref':self.name+'-project'},'name':'人工布局图元','ownedElements':[{'_type':'UMLClassDiagram','_id':self.name,'_parent':{'$ref':self.name+'-model'},'name':'可编辑基本图元','defaultDiagram':True,'ownedViews':self.views}]}]}
        (OUT/f'{stem}.mdj').write_text(json.dumps(project,ensure_ascii=False,indent=2))

def chen():
    d=Drawing(1840,1120,'chen-v3')
    d.text(920,44,'统一权限管理系统  陈氏 ER 图',30,anchor='middle',bold=True)
    dep=d.entity(260,270,'部门');usr=d.entity(900,270,'用户');role=d.entity(260,730,'角色');perm=d.entity(1250,730,'权限');sys=d.entity(1600,270,'业务系统')
    d.attr(dep,90,140,'部门编号',True);d.attr(dep,90,390,'部门名称')
    d.attr(usr,730,140,'用户编号',True);d.attr(usr,910,105,'用户名');d.attr(usr,1085,140,'姓名');d.attr(usr,1110,350,'手机号')
    d.attr(role,140,900,'角色编号',True);d.attr(role,380,900,'角色名称')
    d.attr(perm,1060,900,'权限编号',True);d.attr(perm,1250,980,'权限编码');d.attr(perm,1450,900,'权限名称')
    d.attr(sys,1430,120,'系统编号',True);d.attr(sys,1630,100,'系统标识');d.attr(sys,1690,390,'系统名称')
    belongs=d.diamond(580,270,'属于');d.join(dep,belongs);d.join(belongs,usr)
    d.text(415,305,'1',21,anchor='middle');d.text(760,245,'N',21,anchor='middle')
    owns=d.diamond(260,490,'设有');d.join(dep,owns);d.join(owns,role,total=True)
    d.text(230,380,'1',21,anchor='middle');d.text(230,620,'N',21,anchor='middle')
    assigns=d.diamond(650,500,'分配');d.join(usr,assigns);d.join(assigns,role)
    d.text(805,410,'M',21,anchor='middle');d.text(435,595,'N',21,anchor='middle')
    has=d.diamond(760,730,'拥有');d.join(role,has);d.join(has,perm)
    d.text(515,705,'M',21,anchor='middle');d.text(1000,705,'N',21,anchor='middle')
    defines=d.diamond(1510,490,'定义');d.join(sys,defines);d.join(defines,perm,total=True)
    d.text(1590,395,'1',21,anchor='middle');d.text(1400,630,'N',21,anchor='middle')
    sub=d.diamond(455,185,'下设');d.line([(343,250),(379,185)])
    # Self relationship: distinct child role on top boundary, with no gap.
    d.line([(455,139),(455,80),(260,80),(260,238)])
    d.text(385,225,'1（父）',19,anchor='middle');d.text(345,67,'N（子）',19,anchor='middle')
    d.text(920,1055,'矩形：实体　菱形：联系　椭圆：属性　下划线：键　双线：全参与　1 / M / N：最大基数',18,anchor='middle')
    d.text(920,1090,'角色必须属于一个部门；部门管理员仅可分配本部门角色。用户暂按至多一个部门。',19,anchor='middle')
    d.save('rbac-chen-v1')

def physical():
    d=Drawing(1800,1330,'schema-v3')
    d.text(900,42,'统一权限管理系统  数据库表关系图',30,anchor='middle',bold=True)
    d.text(900,76,'7 张表  MySQL 8.4 / InnoDB  角色属于一个部门',19,anchor='middle')
    tables=[
      ('department','部门',70,170,[('id','BIGINT','PK, NN'),('name','VARCHAR(100)','NN'),('parent_id','BIGINT','FK')],None),
      ('app_user','用户',680,170,[('id','BIGINT','PK, NN'),('username','VARCHAR(64)','NN'),('name','VARCHAR(100)','NN'),('phone','VARCHAR(20)','NN'),('department_id','BIGINT','FK')],'唯一：username'),
      ('user_role','用户角色关联',1290,170,[('user_id','BIGINT','PK, FK, NN'),('role_id','BIGINT','PK, FK, NN')],None),
      ('role','角色',70,660,[('id','BIGINT','PK, NN'),('name','VARCHAR(100)','NN'),('department_id','BIGINT','FK, NN')],'唯一：(department_id, name)'),
      ('role_permission','角色权限关联',680,660,[('role_id','BIGINT','PK, FK, NN'),('permission_id','BIGINT','PK, FK, NN')],None),
      ('permission','权限',1290,660,[('id','BIGINT','PK, NN'),('system_id','BIGINT','FK, NN'),('code','VARCHAR(100)','NN'),('name','VARCHAR(100)','NN')],'唯一：(system_id, code)'),
      ('business_system','业务系统',1290,1030,[('id','BIGINT','PK, NN'),('system_key','VARCHAR(64)','NN'),('name','VARCHAR(100)','NN')],'唯一：system_key'),
    ]
    # Width 440; column gap 170. No tables touch or overlap.
    boxes=[(x,y,440,92+34*len(rows)+(34 if footer else 0)) for _,_,x,y,rows,footer in tables]
    for i,(x,y,w,h) in enumerate(boxes):
        for xx,yy,ww,hh in boxes[i+1:]:
            assert not (x<xx+ww and xx<x+w and y<yy+hh and yy<y+h), 'overlapping tables'
    d.relation([(510,285),(680,285)],'optional','many')
    d.text(595,260,'部门归属',17,anchor='middle')
    d.relation([(1120,285),(1290,285)],'one','many',True)
    d.text(1205,260,'user_id',17,anchor='middle')
    d.relation([(220,364),(220,660)],'one','many')
    d.text(245,520,'department_id',18)
    d.relation([(1510,330),(1510,520),(400,520),(400,660)],'many','one',True)
    d.text(980,495,'role_id',18,anchor='middle')
    d.relation([(510,775),(680,775)],'one','many',True)
    d.text(595,750,'role_id',17,anchor='middle')
    d.relation([(1120,775),(1290,775)],'many','one',True)
    d.text(1205,750,'permission_id',16,anchor='middle')
    d.relation([(1510,922),(1510,1030)],'many','one')
    d.text(1540,980,'system_id',17)
    # Optional parent, zero-or-more children.
    d.relation([(160,170),(160,120),(35,120),(35,310),(70,310)],'optional','many')
    for key,title,x,y,rows,footer in tables:
        height=92+34*len(rows)+(34 if footer else 0)
        d.rect(x,y,440,height);d.line([(x,y+60),(x+440,y+60)])
        d.text(x+14,y+25,title,21,bold=True);d.text(x+14,y+49,key,18)
        for off,lab in [(12,'字段名'),(210,'数据类型'),(350,'约束')]:d.text(x+off,y+83,lab,16,bold=True)
        for i,(field,typ,flags) in enumerate(rows):
            yy=y+92+i*34;d.line([(x,yy),(x+440,yy)])
            d.text(x+12,yy+24,field,18,bold='PK' in flags)
            d.text(x+210,yy+24,typ,16);d.text(x+350,yy+24,flags,14)
        for off in (198,338):d.line([(x+off,y+60),(x+off,y+92+34*len(rows))])
        if footer:
            yy=y+92+34*len(rows);d.line([(x,yy),(x+440,yy)]);d.text(x+12,yy+24,footer,17)
    d.text(70,1110,'PK：主键　FK：外键　NN：非空；未标 NN 的列可空。',18)
    d.text(70,1145,'两个关联表均以两列共同组成主键；五张实体表的 id 为自增主键。',18)
    d.relation([(80,1200),(220,1200)],'one','many',True);d.text(250,1206,'一对零或多',18)
    d.line([(70,1250),(140,1250)]);d.text(160,1256,'实线：外键参与子表主键',17)
    d.line([(650,1250),(720,1250)],dash=True);d.text(740,1256,'虚线：外键不参与子表主键',17)
    d.text(70,1300,'角色分配需同时检查操作权限、操作者与目标用户的部门，以及角色所属部门。',18)
    d.save('rbac-schema-v1')

if __name__=='__main__':
    chen();physical()
