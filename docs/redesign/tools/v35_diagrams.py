"""RBAC1 的人工坐标 UML 类图与逐操作交互。"""
import draw_spec as u
from draw_complete import Sequence
from draw_detail_v331 import cls,ref,dep,note

def hierarchy_sequence(op,mode):
 prefix='Structured' if mode=='structured' else 'Ddd'
 label='结构化' if mode=='structured' else 'DDD'
 add=op.function=='inherit'
 remove=op.function=='uninherit'
 effective=op.function=='effectivePermissions'
 parts=[('api','RoleController'),('service',prefix+'RoleService'),('access','AccessPolicy'),('store','ManagementStore')]
 if effective or add:parts.append(('effective','EffectiveAccessStore'))
 if add:parts.append(('rule','Role' if mode=='ddd' else 'RoleHierarchy'))
 height=1340 if add else 950
 s=Sequence(op.key.lower()+'-'+mode,op.key+' '+label+' '+op.title,parts,height=height)
 s.msg(0,1,280,op.function+'(actor, id'+(', juniorId)' if add or remove else ')'),end=1165 if add else 740)
 s.msg(1,2,375,'require(actor, role.manage)',end=410)
 s.msg(1,3,465,'role(id)'+('、role(juniorId)' if add or remove else ''),end=505)
 s.msg(1,2,550,'scope(actor, 高级角色.departmentId)' if add and mode=='ddd' else 'scope(actor, 目标角色.departmentId)',end=590)
 y=640
 if add:
  s.msg(1,4,y,'reachableRoles([juniorId])',end=y+45)
  s.msg(1,5,y+105,'requireBaseRole(junior, reachable)' if mode=='ddd' else 'requireAcyclic(reachable, id)',end=y+150)
  s.frame(y+180,120,'break','自环或间接环')
  s.msg(1,0,y+265,'409 ROLE_CYCLE',reply=True)
  s.msg(1,3,y+345,'inheritRole(id, juniorId)',end=y+390)
  y+=495
 elif remove:
  s.msg(1,3,y,'removeInheritance(id, juniorId)',end=y+45)
  y+=105
 else:
  s.msg(1,4 if effective else 3,y,('effectiveRolePermissions' if effective else 'inheritedRoles')+'(id)',end=y+45)
  y+=105
 s.msg(1,0,y,'返回结果' if not add and not remove else ('创建关联，提交事务' if add else '移除关联，提交事务'),reply=True)
 s.note(height-95, '重复新增由组合主键拒绝。' if add else '解除指定直接边后，其他继承路径仍有效。' if remove else '有效权限包括直接权限和所有可达基础角色权限。')

def classes():
 for mode,prefix in [('structured','Structured'),('ddd','Ddd')]:
  d=u.D('class-hierarchy-'+mode,'RBAC1 角色继承 '+('结构化' if mode=='structured' else 'DDD')+' 类图',1000,1360)
  methods=['inheritedRoles','effectivePermissions','inherit','uninherit']
  a=cls(d,'RoleController',40,120,420,methods)
  port=cls(d,'RoleUseCase',540,120,420,methods,interface=True)
  svc=cls(d,prefix+'RoleService',40,480,420,methods)
  store=cls(d,'ManagementStore',540,480,420,['inheritedRoles','inheritRole','removeInheritance'],interface=True)
  ref(d,a,port,[(460,200),(540,200)],'service')
  dep(d,svc,port,[(250,480),(250,390),(750,390),(750,port[1]['top']+port[1]['height'])],True)
  ref(d,svc,store,[(460,570),(540,570)],'store')
  graph=cls(d,'Role' if mode=='ddd' else 'RoleHierarchy',40,910,420,['requireBaseRole'] if mode=='ddd' else ['requireAcyclic'])
  effective=cls(d,'EffectiveAccessStore',540,910,420,['reachableRoles','effectiveRolePermissions'],interface=True)
  dep(d,svc,graph,[(250,svc[1]['top']+svc[1]['height']),(250,910)])
  ref(d,svc,effective,[(460,625),(500,625),(500,845),(750,845),(750,910)])
  note(d,40,1190,920,['高级角色继承基础角色，允许多级、多路径；同部门无环。','环路检查与有效权限查询共用继承遍历。'])
