"""Portrait Chen and relational figures, positioned manually."""
from pathlib import Path
import draw_models as m
from draw_spec import D
m.OUT=Path(__file__).resolve().parents[1]/'assets/v33'
class Figure(m.Drawing):
 def line(self,points,dash=False,svg=True):
  if svg:D.line(self,points,dash=dash)
  super().line(points,dash=dash,svg=False)

def chen():
 d=Figure(900,1230,'chen-v33');d.text(450,40,'统一权限管理系统 陈氏 ER 图',26,'middle',True)
 dept=d.entity(220,190,'部门');user=d.entity(680,190,'用户');role=d.entity(220,650,'角色');permission=d.entity(680,650,'权限');system=d.entity(680,1020,'业务系统')
 for entity,attrs in [(dept,[(95,90,'部门编号',True),(320,90,'部门名称',False)]),(user,[(550,80,'用户编号',True),(760,80,'用户名',False),(800,310,'姓名',False),(570,310,'手机号',False)]),(role,[(100,790,'角色编号',True),(330,790,'角色名称',False)]),(permission,[(550,530,'权限编号',True),(800,530,'权限编码',False),(800,780,'权限名称',False)]),(system,[(540,1150,'系统编号',True),(730,1150,'系统标识',False),(800,920,'系统名称',False)])]:
  for x,y,label,key in attrs:d.attr(entity,x,y,label,key)
 belongs=d.diamond(450,190,'属于');d.join(dept,belongs);d.join(belongs,user,total=True);d.text(350,170,'1',20,'middle');d.text(555,170,'N',20,'middle')
 owns=d.diamond(220,490,'设有');d.join(dept,owns);d.join(owns,role,total=True);d.text(190,330,'1',20,'middle');d.text(190,550,'N',20,'middle')
 assignment=d.diamond(450,420,'分配');d.line([(680,222),(680,420),(526,420)]);d.join(assignment,role);d.text(705,355,'M',20,'middle');d.text(340,545,'N',20,'middle')
 grants=d.diamond(450,650,'拥有');d.join(role,grants);d.join(grants,permission);d.text(345,625,'M',20,'middle');d.text(555,625,'N',20,'middle')
 defines=d.diamond(680,840,'定义');d.join(permission,defines,total=True);d.join(defines,system);d.text(650,755,'N',20,'middle');d.text(650,945,'1',20,'middle')
 # Recursive department relationship placed in the left margin below the root.
 sub=d.diamond(100,420,'下设');d.line([(137,190),(20,190),(20,420),(24,420)]);d.line([(100,374),(100,285),(220,285),(220,222)])
 d.text(60,270,'1 父',18,'middle');d.text(155,285,'N 子',18,'middle')
 inherited=d.diamond(130,990,'继承');d.line([(137,650),(20,650),(20,990),(54,990)]);d.line([(130,944),(130,890),(220,890),(220,682)])
 d.text(65,880,'M 高级',17,'middle');d.text(270,880,'N 基础',17,'middle')
 d.text(450,1200,'键属性加下划线；双线为全参与；用户与角色均属于一个部门。',17,'middle');d.save('chen')

def table(d, x, y, name, title, fields, unique=''):
    width = 360
    height = 62 + 25 * len(fields) + (27 if unique else 0)
    d.rect(x, y, width, height)
    d.text(x + 10, y + 26, name, 20, bold=True)
    d.text(x + width - 47, y + 26, title, 16, 'middle')
    d.line([(x, y + 35), (x + width, y + 35)])
    columns = (x + 10, x + 148, x + 275)
    for column, label in zip(columns, ('字段', 'MySQL 类型', '约束')):
        d.text(column, y + 55, label, 16, bold=True)
    for i, (field, data_type, constraint) in enumerate(fields):
        row = y + 62 + i * 25
        d.line([(x, row), (x + width, row)])
        for column, value in zip(columns, (field, data_type, constraint)):
            d.text(column, row + 18, value, 16)
    if unique:
        line = y + height - 27
        d.line([(x, line), (x + width, line)])
        d.text(x + 10, line + 19, f'唯一（{unique}）', 16)


def physical():
    d = Figure(900, 1220, 'relational-v33')
    d.text(450, 40, '统一权限管理系统 数据库表关系图', 26, 'middle', True)

    # Foreign-key routes run between the table edges. The two inheritance
    # legs refer to distinct columns of the same relationship table.
    d.relation([(110, 85), (110, 51), (12, 51), (12, 192), (52, 192)], 'optional', 'many')
    d.relation([(221, 222), (221, 313)], 'one', 'many')
    d.relation([(412, 170), (450, 170), (450, 387), (488, 387)], 'one', 'many')
    d.relation([(675, 313), (675, 247)], 'one', 'many')
    d.relation([(675, 527), (675, 594)], 'one', 'many', True)
    d.relation([(412, 426), (448, 426), (448, 620), (488, 620)], 'one', 'many', True)
    d.relation([(149, 477), (149, 594)], 'one', 'many', True)
    d.relation([(311, 477), (311, 594)], 'one', 'many', True)
    d.text(166, 538, '高级角色', 16)
    d.text(327, 538, '基础角色', 16)
    d.relation([(52, 393), (12, 393), (12, 807), (52, 807)], 'one', 'many', True)
    d.relation([(412, 810), (488, 810)], 'many', 'one', True)
    d.relation([(675, 1020), (675, 954)], 'one', 'many')

    table(d, 52, 85, 'department', '部门', [
        ('id', 'BIGINT', 'PK NN'), ('name', 'VARCHAR(100)', 'NN'), ('parent_id', 'BIGINT', 'FK'),
    ])
    table(d, 488, 85, 'login_session', '登录会话', [
        ('id', 'CHAR(36)', 'PK NN'), ('user_id', 'BIGINT', 'FK NN'),
        ('expires_at', 'TIMESTAMP(6)', 'NN'), ('revoked_at', 'TIMESTAMP(6)', '可空'),
    ])
    table(d, 52, 313, 'role', '角色', [
        ('id', 'BIGINT', 'PK NN'), ('name', 'VARCHAR(100)', 'NN'),
        ('department_id', 'BIGINT', 'FK NN'),
    ], unique='department_id, name')
    table(d, 488, 313, 'app_user', '用户账号', [
        ('id', 'BIGINT', 'PK NN'), ('username', 'VARCHAR(64)', 'NN'),
        ('name', 'VARCHAR(100)', 'NN'), ('phone', 'VARCHAR(20)', 'NN'),
        ('department_id', 'BIGINT', 'FK NN'),
    ], unique='username')
    table(d, 52, 594, 'role_inheritance', '角色继承', [
        ('senior_role_id', 'BIGINT', 'PK FK NN'), ('junior_role_id', 'BIGINT', 'PK FK NN'),
    ])
    table(d, 488, 594, 'user_role', '用户角色', [
        ('user_id', 'BIGINT', 'PK FK NN'), ('role_id', 'BIGINT', 'PK FK NN'),
    ])
    table(d, 52, 765, 'role_permission', '角色权限', [
        ('role_id', 'BIGINT', 'PK FK NN'), ('permission_id', 'BIGINT', 'PK FK NN'),
    ])
    table(d, 488, 765, 'permission', '操作权限', [
        ('id', 'BIGINT', 'PK NN'), ('system_id', 'BIGINT', 'FK NN'),
        ('code', 'VARCHAR(100)', 'NN'), ('name', 'VARCHAR(100)', 'NN'),
    ], unique='system_id, code')
    table(d, 488, 1020, 'business_system', '业务系统', [
        ('id', 'BIGINT', 'PK NN'), ('system_key', 'VARCHAR(64)', 'NN'),
        ('name', 'VARCHAR(100)', 'NN'),
    ], unique='system_key')

    d.text(52, 978, '图例', 19, bold=True)
    d.text(52, 1015, 'PK 主键   FK 外键   NN 非空', 17)
    d.text(52, 1050, '实线：外键是子表主键的一部分。', 17)
    d.text(52, 1085, '虚线：外键未纳入子表主键。', 17)
    d.text(52, 1120, '同表多个 PK 字段组成联合主键。', 17)
    d.text(52, 1155, '八张业务表和一张技术会话表。', 17)
    d.save('schema')


if __name__=='__main__':chen();physical()
