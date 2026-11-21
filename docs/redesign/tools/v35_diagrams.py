"""RBAC1 的人工坐标 UML 类图与逐操作交互。"""
import draw_spec as u
from draw_complete import Sequence
from draw_detail_v331 import cls,ref,dep,note

def hierarchy_sequence(op,mode):
 prefix='Structured' if mode=='structured' else 'Ddd';label='结构化' if mode=='structured' else 'DDD'
 write=op.function in ['inherit','uninherit']
 parts=[('api','RoleController'),('service',prefix+'RoleService'),('access','AccessPolicy'),('store','ManagementStore')]
 if op.function=='inherit':parts.append(('rule','RoleHierarchy'))
 if op.function=='effectivePermissions':parts.append(('effective','EffectiveAccessStore'))
 s=Sequence(op.key.lower()+'-'+mode,op.key+' '+label+' '+op.title,parts,height=1510 if write else 1060)
 s.msg(0,1,290,op.function+'(actor, id'+(', juniorId)' if write else ')'),end=1330 if op.function=='inherit' else 920 if write else 815)
 s.msg(1,2,400,'require(actor, role.manage)',end=435)
 y=500
 if op.function=='inherit':s.msg(1,3,y,'lockHierarchy(actor.departmentId)',end=y+40);y+=105
 s.msg(1,3,y,'role(id)'+('；role(juniorId)' if write else ''),end=y+40);y+=105
 s.msg(1,2,y,'scope(actor, 每个目标角色.departmentId)',end=y+40);y+=105
 if op.function=='inherit':
  s.msg(1,3,y,'edges = inheritanceEdges(actor.departmentId)',end=y+40);y+=105
  s.msg(1,4,y,'requireAcyclic(edges, id, juniorId)',end=y+75)
  y+=160
  s.frame(y,100,'break','自环或间接环')
  s.msg(1,0,y+65,'«exception» ROLE_CYCLE；回滚',reply=True);y+=145
  s.msg(1,3,y,'inheritRole(id, juniorId)',end=y+40);y+=105
 elif op.function=='uninherit':
  s.msg(1,3,y,'removeInheritance(id, juniorId)',end=y+40);y+=105
 else:
  direct=op.function=='inheritedRoles'
  s.msg(1,3 if direct else 4,y,('inheritedRoles' if direct else 'effectiveRolePermissions')+'(id)',end=y+40);y+=105
 s.msg(1,0,y,'返回结果；写入时提交事务',reply=True)
 s.note((1510 if write else 1060)-105,'继承仅限同部门。只在新增边时锁定部门，并读取已提交的边。解除继承直接删除指定边。')

def classes():
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  d=u.D('class-hierarchy-'+mode,'RBAC1 角色继承 '+('结构化' if mode=='structured' else 'DDD')+' 类图',1000,1360)
  methods=['inheritedRoles','effectivePermissions','inherit','uninherit']
  a=cls(d,'RoleController',40,120,420,methods)
  port=cls(d,'RoleUseCase',540,120,420,methods,interface=True)
  svc=cls(d,prefix+'RoleService',40,480,420,methods)
  store=cls(d,'ManagementStore',540,480,420,['lockHierarchy','inheritanceEdges','inheritedRoles','inheritRole','removeInheritance'],interface=True)
  ref(d,a,port,[(460,200),(540,200)],'service')
  dep(d,svc,port,[(250,480),(250,390),(750,390),(750,port[1]['top']+port[1]['height'])],True)
  ref(d,svc,store,[(460,570),(540,570)],'store')
  graph=cls(d,'RoleHierarchy',40,910,420,['requireAcyclic'],stereo='domain rule',static_methods=['requireAcyclic'])
  effective=cls(d,'EffectiveAccessStore',540,910,420,['effectiveRolePermissions'],interface=True)
  dep(d,svc,graph,[(250,svc[1]['top']+svc[1]['height']),(250,910)])
  ref(d,svc,effective,[(460,625),(500,625),(500,845),(750,845),(750,910)])
  note(d,40,1190,920,['高级角色继承基础角色，允许多级、多路径；同部门无环。','结构化与 DDD 共用无状态防环规则；有效权限由单独的查询端口提供。'])
