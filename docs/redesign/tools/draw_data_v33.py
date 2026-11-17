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
 d.text(450,1200,'键属性加下划线；双线为全参与；用户与角色均属于一个部门。',17,'middle');d.save('chen')

def table(d,x,y,name,title,fields,width=380,footer=''):
 h=90+30*len(fields)+(36 if footer else 0);d.rect(x,y,width,h);d.text(x+12,y+26,title,21,bold=True);d.text(x+12,y+52,name,18)
 d.line([(x,y+64),(x+width,y+64)]);d.text(x+10,y+85,'字段',16,bold=True);d.text(x+175,y+85,'类型',16,bold=True);d.text(x+288,y+85,'约束',16,bold=True)
 for i,(field,typ,constraint) in enumerate(fields):
  yy=y+90+i*30;d.line([(x,yy),(x+width,yy)]);d.text(x+10,yy+22,field,17);d.text(x+175,yy+22,typ,15);d.text(x+288,yy+22,constraint,14)
 if footer:d.line([(x,y+90+30*len(fields)),(x+width,y+90+30*len(fields))]);d.text(x+10,y+h-12,footer,15)
 return h

def physical():
 d=Figure(900,1500,'relational-v33');d.text(450,40,'七张业务表 数据库关系图',26,'middle',True)
 fields={
 'department': [('id','BIGINT','PK NN'),('name','VARCHAR','NN'),('parent_id','BIGINT','FK')],
 'app_user':[('id','BIGINT','PK NN'),('username','VARCHAR','NN'),('name','VARCHAR','NN'),('phone','VARCHAR','NN'),('department_id','BIGINT','FK NN')],
 'role':[('id','BIGINT','PK NN'),('name','VARCHAR','NN'),('department_id','BIGINT','FK NN')],
 'user_role':[('user_id','BIGINT','PK FK NN'),('role_id','BIGINT','PK FK NN')],
 'role_permission':[('role_id','BIGINT','PK FK NN'),('permission_id','BIGINT','PK FK NN')],
 'permission':[('id','BIGINT','PK NN'),('system_id','BIGINT','FK NN'),('code','VARCHAR','NN'),('name','VARCHAR','NN')],
 'business_system':[('id','BIGINT','PK NN'),('system_key','VARCHAR','NN'),('name','VARCHAR','NN')]
 }
 # Relationship routes occupy gaps; table positions remain fixed.
 d.relation([(410,220),(490,220)],'one','many');d.text(450,195,'属于',16,'middle')
 d.relation([(210,300),(210,460)],'one','many');d.text(240,405,'部门',16)
 d.relation([(680,396),(680,460)],'one','many',True)
 d.relation([(410,550),(490,550)],'one','many',True);d.text(450,525,'角色',16,'middle')
 d.relation([(210,676),(210,790)],'one','many',True)
 d.relation([(410,880),(490,880)],'many','one',True);d.text(450,855,'权限',16,'middle')
 d.relation([(680,1036),(680,1170)],'many','one');d.text(710,1110,'系统',16)
 d.relation([(100,120),(100,80),(25,80),(25,260),(30,260)],'optional','many')
 table(d,30,120,'department','部门',fields['department'])
 table(d,490,120,'app_user','用户账号',fields['app_user'],'',) if False else None
 table(d,490,120,'app_user','用户账号',fields['app_user'],footer='唯一：username')
 table(d,30,460,'role','角色',fields['role'],footer='唯一：department_id + name')
 table(d,490,460,'user_role','用户角色',fields['user_role'])
 table(d,30,790,'role_permission','角色权限',fields['role_permission'])
 table(d,490,790,'permission','操作权限',fields['permission'],footer='唯一：system_id + code')
 table(d,490,1170,'business_system','业务系统',fields['business_system'],footer='唯一：system_key')
 d.text(40,1220,'PK 主键  FK 外键  NN 非空',17)
 d.text(40,1260,'联合主键防止关联重复。',17)
 d.text(40,1300,'实线：FK 属于子表主键。',17)
 d.text(40,1340,'虚线：FK 不属于子表主键。',17)
 d.text(40,1435,'MySQL 8.4 InnoDB；七张业务表之外单列登录会话技术结构。',18)
 d.save('schema')
 d=Figure(900,820,'session-table-v33');d.text(450,40,'登录会话 技术表与用户关系',26,'middle',True)
 table(d,260,110,'app_user','用户账号',[('id','BIGINT','PK NN')])
 d.relation([(450,230),(450,360)],'one','many');d.text(480,310,'user_id',18)
 table(d,100,360,'login_session','技术登录会话',[('id','CHAR(36)','PK NN'),('user_id','BIGINT','FK NN'),('system_key','VARCHAR','NN'),('expires_at','TIMESTAMP','NN'),('revoked_at','TIMESTAMP','可空')],700)
 d.text(100,690,'用户删除时同事务清理会话。令牌只携带随机会话编号。',19)
 d.text(100,735,'JWT 与会话的用户、目标系统和有效期共同校验。',19)
 d.save('session-table')
if __name__=='__main__':chen();physical()
