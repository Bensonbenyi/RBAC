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
