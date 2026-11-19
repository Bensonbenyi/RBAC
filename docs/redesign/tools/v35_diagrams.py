"""RBAC1 的人工坐标 UML 类图与逐操作交互。"""
import draw_spec as u
from draw_complete import Sequence
from draw_detail_v331 import cls,ref,dep,note

def hierarchy_sequence(op,mode):
 prefix='Structured' if mode=='structured' else 'Ddd';label='结构化' if mode=='structured' else 'DDD'
 write=op.function in ['inherit','uninherit']
 s=Sequence(op.key.lower()+'-'+mode,op.key+' '+label+' '+op.title,[('api','RoleController'),('service',prefix+'RoleService'),('access','AccessPolicy'),('store','ManagementStore')],height=1510 if write else 1060)
 s.msg(0,1,290,op.function+'(actor, id'+(', juniorId)' if write else ')'),end=1330 if op.function=='inherit' else 920 if write else 815)
 s.msg(1,2,400,'require(actor, role.manage)',end=435)
 y=500
 if op.function=='inherit':s.msg(1,3,y,'lockHierarchy(actor.departmentId)',end=y+40);y+=105
 s.msg(1,3,y,'role(id)'+('；role(juniorId)' if write else ''),end=y+40);y+=105
 s.msg(1,2,y,'scope(actor, 每个目标角色.departmentId)',end=y+40);y+=105
 if op.function=='inherit':
  s.msg(1,3,y,'edges = inheritanceEdges(actor.departmentId)',end=y+40);y+=105
  if mode=='ddd':
   s.d.rect(35,y+35,830,95);s.d.text(65,y+70,'ref',20,native=False);s.d.text(450,y+105,'RoleHierarchy 防环检查（见续图）',20,True,native=False)
   sub=Sequence('role-11-ddd-domain','ROLE-11 DDD 角色继承防环',[('caller','Caller'),('service','DddRoleService'),('graph','RoleHierarchy')],height=1080,created={2:430})
   sub.msg(0,1,290,'继续 inherit(actor, id, juniorId)',end=870)
   sub.msg(1,2,430,'«create» RoleHierarchy(edges)',create=True)
   sub.msg(1,2,620,'requireAcyclic(id, juniorId)',end=790)
   sub.frame(675,90,'break','基础角色可达高级角色或二者相同')
   sub.msg(2,1,730,'«exception» ROLE_CYCLE',reply=True)
   sub.msg(1,0,890,'检查完成；或抛异常回滚',reply=True)
   sub.note(970,'领域对象持有不可变边快照，沿高级到基础方向遍历，维护无环规则。')
   m=u.obj('UMLInteractionUse',s.owner,name='领域防环检查',refersTo=u.ref(sub.owner));s.owner['fragments'].append(m)
   s.d.view('UMLInteractionUseView',m,left=35,top=y+35,width=830,height=95)
  else:
   s.msg(1,1,y,'沿基础角色可达路径检查是否回到 id',end=y+75)
  y+=160
  s.frame(y,100,'break','自环或间接环')
  s.msg(1,0,y+65,'«exception» ROLE_CYCLE；回滚',reply=True);y+=145
  s.msg(1,3,y,'inheritRole(id, juniorId)',end=y+40);y+=105
 elif op.function=='uninherit':
  s.msg(1,3,y,'removeInheritance(id, juniorId)',end=y+40);y+=105
 else:
  s.msg(1,3,y,('inheritedRoles' if op.function=='inheritedRoles' else 'effectiveRolePermissions')+'(id)',end=y+40);y+=105
 s.msg(1,0,y,'返回结果；写入时提交事务',reply=True)
 s.note((1510 if write else 1060)-105,'继承仅限同部门。只在新增边时锁定部门，并读取已提交的边。解除继承直接删除指定边。')

def classes():
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  d=u.D('class-hierarchy-'+mode,'RBAC1 角色继承 '+('结构化' if mode=='structured' else 'DDD')+' 类图',1000,1360)
  methods=['inheritedRoles','effectivePermissions','inherit','uninherit']
  a=cls(d,'RoleController',40,120,420,methods)
  port=cls(d,'RoleUseCase',540,120,420,methods,interface=True)
  svc=cls(d,prefix+'RoleService',40,480,420,methods)
  store=cls(d,'ManagementStore',540,480,420,['lockHierarchy','inheritanceEdges','inheritedRoles','effectiveRolePermissions','inheritRole','removeInheritance'],interface=True)
  ref(d,a,port,[(460,200),(540,200)],'service')
  dep(d,svc,port,[(250,480),(250,390),(750,390),(750,port[1]['top']+port[1]['height'])],True)
  ref(d,svc,store,[(460,570),(540,570)],'store')
  if mode=='ddd':
   graph=cls(d,'RoleHierarchy',40,910,420,['requireAcyclic'],[('edges','List<Data.Inheritance>')])
   edge=cls(d,'Data.Inheritance',540,910,420,attrs=[('seniorRoleId','Long'),('juniorRoleId','Long')],stereo='record')
   dep(d,svc,graph,[(250,svc[1]['top']+svc[1]['height']),(250,910)])
   u.assoc(d,graph,edge,[(460,1010),(540,1010)],role='edges',mult='0..*')
  else:
   note(d,40,910,920,['事务脚本在 Service 内进行队列遍历和防环检查。','继承写操作先锁定部门，再校验归属并读取最新边。'])
  note(d,40,1190,920,['高级角色继承基础角色，允许多级、多路径；同部门无环。','查询端口保留直接关系与有效权限两种视图。'])
