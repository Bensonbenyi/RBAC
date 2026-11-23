"""Author the current requirements/detail specification and the separate outline design."""
from pathlib import Path
import re,json
from v33_catalog import OPS,MODULES,REQS
from v33_detail_content import FIELD_TABLES, CASES, case_table, calls_table, operation_input, architecture_details
from v35_content import deployment_results, verification
R=Path(__file__).resolve().parents[1];VERSION=(R/'VERSION').read_text().strip()
HEADER='''= {title}
OOAD 课程项目组
v{version}, 2026-11-23
:doctype: book
:lang: zh-CN
:toc:
:toc-title: 目录
:toclevels: 3
:sectnums:
:sectnumlevels: 3
:xrefstyle: short
:imagesdir: assets
:figure-caption: 图
:table-caption: 表
:stylesheet: spec.css
:stylesdir: tooling
'''
def table(headers, rows, cols=None, style=''):
 cols = cols or ','.join(['1'] * len(headers))
 attrs = (style + ',' if style else '') + f'cols="{cols}",options="header"'
 heading = ' '.join('|' + x for x in headers)
 body = ''.join(' '.join('|' + str(x).replace('|', '\\|') for x in row) + '\n' for row in rows)
 return '\n[' + attrs + ']\n|===\n' + heading + '\n' + body + '|===\n'

def fig(stem,title,anchor=None):
 link=f',link={{imagesdir}}/v33/{stem}.svg' if stem.startswith('packages-') or stem=='packages' else ''
 return f'\n[.diagram.plate]\n--\n[[{anchor or "fig-"+stem}]]\n.{title}\nimage::v33/{stem}.svg[{title},width=100%{link}]\n--\n'
def refs():return table(['参考资料','使用范围'],[
 ('GB/T 8567—2006《计算机软件文档编制规范》','概要设计的目的、结构、接口与数据设计组织；章节对应关系见本文。'),
 ('https://www.omg.org/spec/UML/2.5.1[OMG UML 2.5.1]','类、包、状态、部署、活动、用例和时序图语义。'),
 ('https://docs.staruml.io/[StarUML 官方文档]','模型元素和可编辑图稿。'),
 ('https://www.csc.lsu.edu/~chen/pdf/erd-5-pages.pdf[Chen ER 建模论文]','实体、联系、属性、键和参与约束。'),
 ('《5. 面向对象设计》课程课件','按用户提供的 144 页课件核对类图、分层与 DDD 职责。'),
 ('《面向对象分析与设计课程成绩计算办法》V4','第二轮验收的 RBAC1、双实现、Swarm 部署和日志要求。'),
 ('需求与详细设计说明书 v2','字体、字号、用例表配色、接口调用说明和分页形式。'),
 ('https://gitee.com/mingqcn/course-project-mgt[课程管理系统] 990cea2','四层组织、双实现、统一身份接口和权限码。'),
 ('https://gitee.com/mingqcn/spring-fundamentals[Spring 教学示例] 38fd817','CloneFactory 0.0.2 编译期拷贝工具。'),
 ('本项目 v3.2.2 与 2026-11-17 确认决定','当前修订基线；性能指标按本次实测建立。')],'3,5')
CONSTRAINTS=[
 ('BR-01','用户通过角色获得权限；用户角色、角色权限均为多对多关系。'),
 ('BR-02','每个用户和角色属于一个部门；管理员管理本部门直接成员。'),
 ('BR-03','角色分配要求操作者、目标用户、目标角色属于同一部门。'),
 ('BR-04','角色可包含两个业务系统的权限；部门不直接获得系统权限。'),
 ('BR-05','用户名全局唯一、部门内角色名唯一、系统内权限码唯一、系统标识唯一。'),
 ('BR-06','用户名、部门、系统标识、权限所属系统和权限码创建后固定；普通编辑使用字段白名单。'),
 ('BR-07','登录使用用户名和手机号，password 字段承载手机号；systems 接受一组不重复的目标系统。'),
 ('BR-08','访问目标系统须有有效会话和该系统的 login 权限。退出只需有效会话及令牌所含的目标系统。'),
 ('BR-09','JWT 保存身份和目标系统，会话保存在 MySQL；权限按当前授权关系读取。'),
 ('BR-10','用户、部门、业务系统和权限定义通过 SQL 维护。删除角色前先处理关联。'),
 ('BR-11','结构化与 DDD 共用输入输出、存储端口和数据库约束；切换配置保持外部行为一致。'),
 ('BR-13','界面码与 API 码分别授予；界面展示和接口执行分别检查，统一复用 RBAC 表。'),
 ('BR-14','RBAC1 角色继承限同部门，支持多级多路径，禁止环；权限按可达角色去重取并集。'),
 ('BR-12','本版功能为单项维护和授权，范围按已确认的精简设计执行。')]
TABLES=[('role_inheritance','角色继承','senior_role_id PK/FK；junior_role_id PK/FK','同部门无环有向图；高级角色继承基础角色'),('department','部门','id PK；name；parent_id FK 自关联','部门 0..1 父节点，0..* 子节点'),('app_user','用户账号','id PK；username 唯一；name；phone；department_id FK 非空','用户属于一个部门'),('role','角色','id PK；name；department_id FK 非空','部门内 name 唯一'),('business_system','业务系统','id PK；system_key 唯一；name','登记 rbac 和 course-project-management'),('permission','操作权限','id PK；system_id FK；code；name','system_id 与 code 联合唯一'),('user_role','用户角色','user_id PK/FK；role_id PK/FK','两列组成联合主键'),('role_permission','角色权限','role_id PK/FK；permission_id PK/FK','两列组成联合主键'),('login_session','技术会话','id PK；user_id FK；expires_at；revoked_at','会话有效性与撤销验证')]
ERRORS=[('400','INVALID_ARGUMENT / BAD_REQUEST','字段、格式、分页或路径编号无效。'),('401','UNAUTHORIZED','账号凭据、签名、会话或有效期不通过。'),('403','FORBIDDEN','目标系统登录许可、操作权限或部门边界不通过。'),('404','NOT_FOUND','有操作权限后查找的对象不存在。'),('405','METHOD_NOT_ALLOWED','HTTP 方法不符合接口定义；响应携带 Allow。'),('409','CONFLICT / ROLE_CYCLE','唯一约束、重复分配、引用冲突或角色继承成环。'),('500','INTERNAL_ERROR','未预期错误，写事务回滚。'),('503','SERVICE_UNAVAILABLE','数据库不可用；课程端使用 IDENTITY_SERVICE_UNAVAILABLE 表达身份依赖故障。')]
COURSE=[('listTerms','GET /terms','login','学期列表'),('createTerm','POST /terms','TC','新建学期'),('updateTerm','PATCH /terms/{termId}','TE','修改非当前学期'),('deleteTerm','DELETE /terms/{termId}','TD','删除非当前且无引用学期'),('listAdminCourses','GET /admin/courses','CV','从 1 开始分页的课程列表'),('getCourse','GET /courses/{courseId}','CV','课程详情，保留老师的对象规则'),('reviewCourse','POST /courses/{courseId}/review','CR','审核待审核课程')]

def measured_rows():
 rows=[]
 for d in json.loads((R/'assets/v33/performance-baseline.json').read_text())['rows']:
  rows.append((d['strategy'],str(d['concurrency']),d['trial'] if not d['trial'].startswith('c') else '1',str(d['requestsPerSecond']),str(d['p95Ms']),str(d['errors'])))
 return rows

def detail():
 p=[HEADER.format(title='统一权限管理系统需求与详细设计说明书',version=VERSION)]
 def add(s):p.append(s+'\n')
 add('''== 引言

=== 编写目的

本文规定当前基线的需求、数据结构、各功能交互、HTTP 契约和双实现设计，供课程评审、开发与验收使用。独立《概要设计说明书》说明系统骨架及 ATAM 评估，本文保存字段字典和各功能详细设计。

本次版本为 '''+VERSION+'''，基于 v3.2.2 和 2026-11-17 确认决定修订。代码与文档共用功能编号和配置名称，验证结果见实施报告。

=== 背景与术语

平台统一维护用户、部门、角色、业务系统与权限。用户通过角色获得权限；部门限定账号和角色归属及管理员的数据范围。平台自身后台和老师的课程管理系统均登记为接入系统。
''')
 add(table(['术语','定义'],[('用户','员工与登录账号的统一实体，属于一个部门。'),('角色','属于一个部门的权限集合。'),('权限','在一个业务系统内具有唯一编码的操作许可。'),('Bearer JWT','客户端在 Authorization 请求头提交的签名凭据，不携带角色或权限。'),('登录会话','服务器保存在 MySQL 的技术记录，关联用户、到期与撤销时间。'),('结构化实现','业务 Service 在事务内编排规则和存储调用。'),('DDD 实现','应用服务编排用例，领域对象维护用户角色分配等业务规则。'),('Service Port','两套业务实现共用的用例接口、数据契约和存储端口。'),('CloneFactory','老师提供的编译期拷贝工具，生成 DTO 到 Command 的转换代码。')],'2,6'))
 add('=== 参考资料\n'+refs())
 add('''== 任务概述

=== 功能与非功能需求

平台提供登录和鉴权、本部门用户查询与角色分配、部门树查询、角色与授权管理、业务系统与权限目录查询，并接入课程系统的学期维护与课程审核。
''')
 add(table(['需求编号','需求名称'],REQS,'2,6'))
 add('=== 业务约束\n'+table(['编号','约束'],CONSTRAINTS,'1,7'))
 add('''=== 初始化与演示数据

初次部署在空库中显式初始化两个业务系统及其 login 权限。演示数据建立教学部与教务部、admin、reviewer、viewer 及 other 四个合成账号；管理员与普通用户使用相同实体，通过角色权限区分。

Flyway 建表后，在空演示库手动导入 demo.sql。账号资料和部门变更通过 SQL 维护。课程库完成迁移后执行教师资料关联脚本。规模演示另行导入 10,000 个匿名账号及 111 个来源组织节点，统一挂到独立根部门下。

=== 需求分析模型

当前用例见 <<AN-UC-01>>。角色分配活动见 <<AN-ACT-02>>，概念实体及关系见 <<AN-CC-01>>。会话状态模型及系统数据流分别以 <<AN-ST-01>>、<<AN-DFD-00>> 和 <<AN-DFD-01>> 标识，供概要设计的转换和追溯引用。
''')
 for stem,title,id in [('activity-login','AN-ACT-01 登录活动','AN-ACT-01'),('activity-assign','AN-ACT-02 角色分配活动','AN-ACT-02'),('session-state','AN-ST-01 会话状态','AN-ST-01'),('dfd-0','AN-DFD-00 顶层数据流','AN-DFD-00'),('dfd-1','AN-DFD-01 第一层数据流','AN-DFD-01')]:add(fig(stem,title,id))
 add('''== 数据描述

=== 概念与关系模型

五类业务实体为部门、用户、角色、权限和业务系统。用户角色、角色权限和角色继承分别对应三张关联表。登录会话是技术表，通过 user_id 关联账号。
''')
 add(fig('chen','AN-CC-01 陈氏 ER 图','AN-CC-01'))
 add(fig('schema','数据库表关系图'))
 add('=== RBAC1 继承约束\n\n高级角色通过 role_inheritance 指向基础角色，获得其直接与间接权限。角色层次与部门层次独立；所有继承边限定同部门。新增边前从基础角色逐层读取继承关系。能走回高级角色时返回 409 ROLE_CYCLE。有效权限查询复用相同的遍历，多路径通过集合去重。移除一条边时其他路径继续有效。\n')
 add('''=== 索引、事务与删除

数据库采用 MySQL 8.4 InnoDB，文本使用区分大小写的排序规则。用户名和系统标识建立唯一索引，角色名及权限编码采用所属范围内的联合唯一索引。两组授权和角色继承关联表使用联合主键，并为反向关联列建立索引。

写用例在 Service 事务边界内执行。角色存在引用时返回冲突。账号、部门、业务系统和权限定义通过 SQL 维护，外键约束仍生效。

关联查询从用户直接角色出发，递归展开 role_inheritance，再通过角色权限读取去重并集。每个鉴权请求检查已提交的授权关系；已进入事务的请求按数据库事务可见性完成。新增授权及继承用 POST，组合主键拒绝重复关系并返回 409。删除关联重复调用仍返回 204。课程演示按单人操作安排。

=== 数据字典
''')
 for title,rows in FIELD_TABLES:
  add('==== '+title+'\n'+table(['字段','类型','可空','约束','含义'],rows,'22,21,8,23,26'))
 add('=== 系统用例图')
 add(fig('usecases','AN-UC-01 系统用例图','AN-UC-01'))
 add('''== 总体架构

=== 分层与职责

系统总体分层见 <<fig-architecture>>，包依赖见 <<fig-packages>>。API 通过用例端口调用启动配置选中的服务；所有应用数据访问由 Infrastructure 中的 Spring Data JPA 仓储处理，基础 CRUD 和分页由仓储生成，关联查询和定向更新使用 JPQL。认证为共享装配。
''')
 add(fig('architecture','总体分层架构'));add(fig('packages','系统包依赖概览'))
 add(architecture_details(fig,table))
 add('''== 功能需求与详细设计

=== 功能描述

以下每项列出需求、调用说明与详细交互。写请求先校验输入，角色名称 DTO 通过 CloneFactory 转为用例输入；操作者身份由 BearerFilter 和 CurrentIdentity 提供。DDD 的 User 校验可分配角色的部门，Role 校验基础角色与继承环路。应用服务保存授权关系。
''')
 for case in CASES:
  key,title,req,keys,package,actor,description,trigger,pre,steps,errors,post,activity,module=case
  add('==== '+title)
  add(case_table(case))
  operations=[next(o for o in OPS if o.key==key) for key in keys]
  add(calls_table(operations))
  if activity:add(fig(activity,title+' 活动图',anchor='fig-function-'+activity))
  for op in operations:
   add('===== '+op.key+' '+op.title)
   add(op.rule+'\n\n输入：'+operation_input(op)+'\n\n成功结果：'+op.result+'。权限：'+op.permission+'。')
   if op.key=='AUTH-02':
    add('令牌、会话和登录许可的校验见 <<fig-flow-preflight>>。')
   if op.module in ['Auth','Department','System','Permission'] or op.function=='options':add(fig(op.key.lower()+'-shared',op.key+' 详细交互'))
   else:
    for mode,label in [('structured','结构化'),('ddd','DDD')]:
     add(fig(op.key.lower()+'-'+mode,op.key+' '+label+' 详细交互'))
     if mode=='ddd' and op.key=='USER-07':add(fig(op.key.lower()+'-ddd-domain',op.key+' DDD 聚合修改与保存'))
   if op.key=='AUTH-01':add(fig('flow-login-session','建立登录会话与签发凭据'))
  if key=='HIERARCHY-001':
   for mode,label in [('structured','结构化'),('ddd','DDD')]:add(fig('class-hierarchy-'+mode,'RBAC1 '+label+' 角色继承类图'))
  if module=='Auth':
   add(fig('class-auth-shared','共享认证与实时鉴权 类图'))
   add(fig('class-identity-directory','统一身份目录类图'))
  elif module:
   add('===== '+MODULES[module][0]+'模块类图')
   modes=[('shared','共享')] if module in ['Department','System','Permission'] else [('structured','结构化'),('ddd','DDD')]
   for mode,label in modes:add(fig('class-'+mode+'-'+module.lower(),MODULES[module][0]+' '+label+' 模块类图'))
 add('==== 课程管理系统接入\n\n需求 REQ-F-07。课程前端使用老师原版 web，调用 POST /course-api/login。老师后端先校验本地工号或学号，再用账号、手机号和目标系统调用平台 /auth/login。老师的业务规则保留，适配层完成验签、实时身份与 API 权限查询。')
 add('登录后，GET /course-api/me 返回本地师生资料和当前界面权限。课程库 teacher 和 student 的 user_id 对应平台用户编号，工号或学号沿用平台用户名。演示数据将总公司的 190 名账号分配为教师，其余 9,810 名账号分配为学生，角色均归属各自部门。导入按实际用户编号关联两个库，在独立演示班绑定样例教师和学生。')
 add(case_table(('COURSE-001','课程系统接入','REQ-F-07',[],'课程管理系统','教师、学生及课程管理员','使用统一身份进入对应工作台，查看自己的课程并按授权执行管理操作。','用户进入课程演示入口。','课程名册存在对应工号或学号，平台授予目标系统 login 和入口所需角色。',[
 ('读取当前身份与实时 API 权限。','课程后端按 operationId 调用平台。'),('检查本地教师或学生的课程归属。','平台授予全局许可，课程服务判断任课、选课和课程状态。'),('返回老师的业务结果。','前端按独立 UI 码启用管理菜单和按钮。')],[('无效登录或已退出','401。'),('无本次操作权限','403。'),('入口与课程名册不匹配','本地名册无对应工号或学号时返回 404。'),('统一身份服务不可用','503，停止业务操作。')],'当前授权影响后续课程请求。',None,None)))
 add(table(['图中处理 / 实际方法','调用说明 URL','功能'],[(operation,route,behavior+'；权限 '+permission) for operation,route,permission,behavior in COURSE],'3,5,5'))
 for i,(operation,route,permission,behavior) in enumerate(COURSE,1):
  add('===== COURSE-'+str(i).zfill(2)+' '+operation)
  add(behavior+'。调用 '+route+'，权限码 '+permission+'。')
  add(fig('course-'+str(i).zfill(2),operation+' 接入详细交互'))
 add('===== COURSE-08 getCurrentUser 支撑接口\n\n调用 GET /me，operationId 为 getCurrentUser，平台映射到课程 login 权限。老师服务复用本次请求已核验的统一身份，并读取本地资料，返回 userId、teacherId、studentId、number、name、roles、permissions。permissions 保留当前系统界面码，供页面初始化和权限刷新。')
 add(fig('course-08','getCurrentUser 本地资料与界面权限交互'))
 add('===== COURSE-09 课程登录\n\nPOST /course-api/login 接收 role、account、password，成功进入对应工作台。')
 add(fig('course-09','老师原版前端与统一身份的登录交互'))
 add('===== COURSE-10 课程网页直连统一身份\n\n构建时以 VITE_IDENTITY_BASE_URL 指定平台入口。管理菜单进入时调用 GET /auth/permissions/{permission}。教师目录中的新增表单调用 GET /auth/departments 和 GET /auth/departments/{departmentId}/users，平台检查 TEC。跨域时，Spring 先按配置来源处理 OPTIONS 预检，允许 Authorization、Content-Type 和 X-Target-System，随后的 GET 仍校验 JWT、会话和权限。')
 add(fig('course-10','课程网页跨域查询权限和组织目录'))
 add(table(['课程系统调用','调用说明 URL','功能'],[
  ('TeacherController +\nlistTeachers','GET /course-api/teachers','按 TV 分页查询教师。'),
  ('TeacherController +\ncreateTeacher','POST /course-api/teachers','按 TEC 从统一身份目录选人并在课程库创建教师记录。')],'4,5,5'))
 add('===== COURSE-11 师生查看我的课程\n\n教师的 GET /course-api/teacher/courses 按任课或负责关系查询。学生的 GET /course-api/me/courses 按 userId 定位课程库中的学生，再按学期查询已选且开放的课程。师生都可查看自己的演示课和里程碑。')
 add(fig('course-11','学生课程查询时序'))
 add(table(['图中处理 / 实际方法','调用说明 URL','功能'],[
  ('StudentCourseController +\nlistStudentCourses','GET /course-api/me/courses','返回指定学期的已选开放课程。'),
  ('TeacherCourseController +\nlistTeacherCourses','GET /course-api/teacher/courses','返回所属教学课程。'),
  ('CourseMilestoneController +\nlistMilestones','GET /course-api +\n/courses/{courseId}/milestones','返回所属课程的里程碑，学生读取时还需课程已开放。')],'5,6,4'))
 add('''[.course-note]
课程列表 page 从 1 开始，courseStatus 为 1 申请、2 待审核、3 已开课。审核输入 decision 为 approved 或 rejected，驳回必须填写 comment。接入补丁固定到 990cea2，沿用老师的课程实体和业务规则。
''')
 add('''=== 接口与输入设计

==== 公共约定

平台列表 page 从 0 开始，size 默认为 20、最大 100，page 最大 1000000。平台实体编号在 JSON 中为十进制字符串，避免前端精度损失。老师课程接口保留原分页及编号格式。

路径编号要求正整数。登录参数 username 和 password 非空，长度上限分别为 128 和 256。平台账号的用户名最多 64 个字符，演示账号以 11 位手机号作为密码。管理接口的名称长度为 1 至 100。系统标识使用小写字母、数字和连字符，权限码支持大小写字母、数字、点、冒号、下划线和连字符。

角色新建、改名和新增关联使用专用 DTO。未声明字段返回 400。授予权限提交 permissionId，继承角色提交 juniorId。

==== 认证与异常

JWT 使用 RS256，携带 iss、sub、aud、jti、iat 和 exp，默认有效期 30 分钟。BearerFilter 校验签名、到期时间、aud、数据库会话、当前账号及 login 权限。默认使用 aud 首项。X-Target-System 可选择 aud 中另一个已获准系统。

/auth/me 返回 subject、no、name、departmentId、roles、permissions，permissions 只含当前系统的界面码；/auth/users/{userId} 返回 userId、no、name。角色名称用于显示，管理资格由权限集合确定。界面权限检查返回 allowed，API 许可检查返回 operationId 和 allowed。统一身份目录按层级和关键词分页查询。部门编码由 D 加现有部门编号组成。课程目录通过 TEC 授权。
''')
 add(table(['HTTP','业务码','含义'],ERRORS,'1,3,6'))
 add('==== 独立界面权限与禁用规则\n\n权限实体同时保存界面码和 API 码，两者经同一用户角色、角色权限关系分别授予。数据库沿用八张业务表。GET /auth/me 的 permissions 及 GET /auth/permissions/{permission} 仅返回或判断已登记的界面码。GET /auth/api-permissions/{operationId} 在 AuthService 中映射到 API 码；平台使用 user.list、role.grant 等资源加操作编号，课程保留老师的 operationId。未知操作返回 404 API_OPERATION_NOT_FOUND。')
 add(table(['界面','菜单码','按钮码','后端 API 码'],[
 ('课程管理','MCO','MCOR 审核','CV 查询/详情；CR 审核'),
 ('学期维护','MTR','MTRC 新建；MTRE 修改；MTRD 删除','login 查询；TC 新建；TE 修改；TD 删除'),
 ('用户','ui.users','ui.user.roles','user.view / user.role.assign'),
 ('角色','ui.roles','ui.role.create/edit/delete/permissions/inheritance','role.manage'),
 ('部门','ui.departments','目录只读','department.manage'),
 ('业务系统','ui.systems','目录只读','system.manage'),
 ('权限目录','ui.permissions','目录只读','permission.manage')],'2,2,5,4'))
 add('同一管理分组中，拥有任一菜单码时显示全部菜单项；无对应码的菜单使用原生 button disabled。没有管理菜单码时收起管理分组。业务条件满足的操作按钮持续显示，无对应按钮码时 disabled，保存、授予和撤销同样检查。查询与翻页属于菜单基础能力。桌面与移动端共用组件。界面码有效且 API 码缺失时，控件可用，后端返回 403；只有 API 码时控件禁用。窗口重新获得焦点、手动刷新及 403 后重新读取界面码，路由检查菜单权限。')
 add('===== 授权表单候选项\n\nGET /api/roles/options 由 user.role.assign 授权并限定本部门；GET /api/systems/options 允许 role.manage 或 permission.manage；GET /api/permissions/options?systemId=... 要求 role.manage。三个接口共用 SelectionService，只读分页，不扩大目录写权限。前端逐页读取，避免目录超过 100 项时遗漏。')
 add(fig('class-selection','共享授权候选查询 类图'))
 add(fig('flow-ui-api','界面码与 API 码独立检查 交互图'))
 add('===== 数据库升级\n\nFlyway V2 在现有 permission 与 role_permission 中补齐旧版既有界面的独立码和对应演示授权，不增表。迁移只执行一次；之后撤销 UI 码与 API 码各自生效。Flyway V5 移除旧管理码及会话的 system_key。V6 清理系统和权限目录的五个停用按钮权限。空演示库手动导入 demo.sql。')
 add('==== 输入对象与字段白名单\n'+table(['输入对象','接收字段','固定或推导字段'],[
 ('LoginDTO','username、password、systems','逐一检查各目标系统的登录许可。'),
 ('ManagementDTOs.NameDTO','name','用于新建角色及重命名角色。'),
 ('ManagementDTOs.AssignRoleDTO','roleId','目标用户来自路径，操作者来自认证上下文。'),
 ('ManagementDTOs.GrantPermissionDTO','permissionId','目标角色来自路径。'),
 ('ManagementDTOs.InheritRoleDTO','juniorId','高级角色来自路径。')],'3,4,4'))
 add('==== 平台 HTTP 接口总表\n'+table(['编号','方法与路径','操作许可','成功响应'],[(o.key,o.method+' '+o.route,o.permission,o.result) for o in OPS],'2,5,3,3'))

 add('''== 性能需求

=== 测量方法

2026-11-17，辅助节点通过公网 HTTP 对两套实现执行同一负载：权限查询与本部门用户列表各占 50%。每轮预热 100 次，再计时 1,000 次；并发为 1、4、8，8 并发各测三轮。登录在计时前完成。数据、连接池、容器资源与 DEBUG 分层日志配置相同。

列表采用有界分页，数据库连接池上限为 5。历史基线数据库含 2 个部门、5 个用户、3 个角色、2 个系统、17 项权限、4 条用户角色和 26 条角色权限关联。v3.4.0 增加独立界面码，该组结果保留为 v3.3.0 部署实测记录；本轮验证见整改报告。测试页容量为 20，本部门列表返回 4 个用户。实时授权查询使用关联表主键及反向索引。

=== 精度与一致性

手机号与系统标识按字符串处理，权限比较区分大小写。角色权限按集合取并集。JWT 不缓存权限；撤销提交后的新请求读取当前关系。写失败回滚，重复分配由数据库联合主键裁决。

=== v3.3.0 历史基线
''')
 add(table(['业务实现','并发','轮次','吞吐 req/s','P95 ms','错误数'],measured_rows(),'3,1,1,2,2,1'))
 add('''两套实现共完成 10,000 次计时请求，全部返回 200，错误率为 0。8 并发三轮 P95 为结构化 36.67 至 52.61 ms、DDD 36.90 至 55.83 ms。结果受 JVM 预热、共享节点和网络波动影响，作为上述数据规模与部署环境的回归基线。

旧版连接复用调整前，1,000 次请求出现 2 次 504（0.2%），网关记录上游连接超时。Nginx 启用 keepalive 后，表中全部请求成功；原始结果及配置见原实施报告。
''')
 deployment=deployment_results()
 if deployment:
  count=deployment['database']
  add(f'''=== v{VERSION} 现行部署复测

权限库含 {count['platformUsers']:,} 个账号、{count['departments']} 个部门及两个业务系统。客户端通过公网 HTTP 查询当前界面许可及管理员本部门用户页，请求各占一半。每种实现预热 100 次，以 8 并发计时 1,000 次。用户页只命中本部门账号，因此本轮结果对应分页授权负载。
''')
  add(table(['业务实现','计时请求','吞吐 req/s','P95 ms','错误数'],[
   (run['strategy'],run['requests'],run['requestsPerSecond'],run['p95Ms'],run['errors'])
   for run in deployment['performance']],'3,2,2,2,1'))

 add('''== 运行需求

=== 配置与启动
''')
 add(table(['配置项','用途'],[('BACKEND_STRATEGY','structured 或 ddd，默认 structured，启动时装配。'),('DB_URL / DB_USER / DB_PASSWORD','RBAC 数据库连接，凭据由环境或 Secret 注入。'),('JWT_PRIVATE_KEY / JWT_PUBLIC_KEY','持久化 RSA 签名密钥和校验公钥的 Resource 路径。'),('app.jwt.lifetime','令牌及数据库会话的有效期，默认 30 分钟。'),('logging.config / LOG_PATH','Logback 及滚动日志目录。')],'3,5'))
 add('''源码构建使用 Java 21、Spring Boot 4.1.2 和 CloneFactory 0.0.2；前端使用 Vue 3.5、TypeScript 5.7 与 Vite 6。Maven Wrapper 固定版本并使用 HTTPS 镜像。构建与检查入口为 tooling/test.sh。

=== Swarm 部署与恢复

部署复用现有三节点 Swarm，第四台作为辅助节点。管理节点运行两个 JVM 和 Nginx，第一个工作节点运行 MySQL，第二个工作节点保留原应用。数据库及日志使用独立卷，密钥和数据库密码使用 Docker Secret。

演示入口采用 IP 加 HTTP。数据库不发布公网端口，服务通过加密 Overlay 通信。数据需要恢复时，由组员按部署说明手动执行。单管理节点和单数据库实例的故障范围在概要设计中列为架构风险。

=== 图文与构建产物

当前图稿由手工坐标生成 SVG、PNG 和 StarUML 源文件，入口为 draw_v33.py 与 draw_data_v33.py。文字入口为 write_v33.py；历史生成器不用于当前交付。导出时核对版本、图引用、页面尺寸和目录页码。

== 其他需求与验收

=== 验收场景
''')
 add(table(['编号','场景','通过条件'],[('ACC-01','两种业务策略','同一契约套件在 structured、ddd 下通过。'),('ACC-02','RBAC1 与部门边界','多级、多路径继承去重；跨部门返回 403，成环返回 409；重复继承返回 409。'),('ACC-03','输入与数据库','非法、未知字段拒绝；唯一和引用冲突返回 409。'),('ACC-04','会话及撤权','退出、到期和权限撤销对后续请求生效。'),('ACC-05','课程接入','真实学期 CRUD、课程列表和审核；撤权后原令牌被拒绝。'),('ACC-06','日志','浏览器功能请求可对应 API、Service 和存储层日志，凭据不进入日志。'),('ACC-07','恢复和性能','记录实际恢复时长及两种配置的相同负载结果。'),('ACC-08','文档与图','两份 PDF 均为统一 A4 纵向，源文件可编辑，编号和接口对应。')],'2,3,5'))
 add(verification(table))
 (R/'统一权限管理系统需求与详细设计说明书.adoc').write_text('\n'.join(p).rstrip()+'\n')

def outline():
 p=[HEADER.format(title='统一权限管理系统概要设计说明书',version=VERSION)]
 def add(s):p.append(s+'\n')
 add('''== 引言与总体设计

=== 编写目的与背景

本文说明统一权限管理系统的总体结构、模块协作、接口、全局数据、部署与质量属性评估，用于大作业二评审及代码实现。需求与详细设计合订本保存当前基线的功能详述和字段字典。

本次设计承接 v3.2.2 并按 2026-11-17 确认决定修订。平台自身和老师课程管理系统作为两个接入系统，统一使用 RBAC 授权。

=== 术语与参考资料

用户属于一个部门，角色属于一个部门，用户通过角色取得权限。Service Port 是两套业务实现共用的用例与存储契约；JWT 是签名身份凭据；数据库会话保存撤销和到期信息。
''')
 add(refs())
 add('=== 模板对应与需求概述\n'+table(['模板内容','本文章节'],[('引言、任务概述与约束','第 1 章'),('总体设计与模块划分','第 2、3 章'),('设计转换、接口及数据','第 4、5、6 章'),('运行、部署及其他需求','第 7、8 章'),('需求追溯','第 9 章')],'3,5'))
 add(table(['编号','当前需求'],REQS,'2,6'))
 add('''=== 设计原则与约束

RBAC 表达全部操作许可，部门限定账号、角色及管理范围。结构化与 DDD 采用一个分层架构下的两种业务实现，共用 API、数据契约及 Infrastructure。接口按资源划分，敏感字段采用白名单。

当前业务为单项维护、授权和两个系统接入。部署保留已有服务器服务。性能以当前资源实测建立基线，组号由提交打包参数补齐。

== 总体架构设计与评估

=== 架构设计

==== 架构风格与总体结构

系统采用浏览器客户端与分层单体后端。该规模下用例、事务和授权关系集中在一个数据库内，便于核对行为及教学展示。总体结构见 <<OV-ARCH-01>>；课程管理系统作为独立应用通过 HTTP 接入。
''')
 add(fig('architecture','OV-ARCH-01 系统总体分层结构','OV-ARCH-01'))
 decisions=[('AD-01','分层单体，共享 API 和存储端口','REQ-N-04；REQ-N-05','可修改性、可重复构建'),('AD-02','结构化和 DDD 由启动配置选择','REQ-N-04','可修改性；保持外部契约一致'),('AD-03','用户角色与角色权限关系实时读取','REQ-F-01 至 REQ-F-06；REQ-N-01','安全；每请求增加数据库读取'),('AD-04','JWT 身份加 MySQL 登录会话','REQ-F-01；REQ-N-01；REQ-N-03','退出失效与重启后的会话验证'),('AD-05','MySQL 单实例、事务和约束、有界连接池','REQ-N-02；REQ-N-03','一致性、资源控制与性能'),('AD-06','现有 Swarm 上按资源约束放置容器','REQ-N-03；REQ-N-05','应用重启与部署隔离'),('AD-07','课程适配层固定老师契约和权限码','REQ-F-07；REQ-N-01','接入正确性与接口可修改性')]
 add('==== 关键架构决策\n'+table(['决策','内容','需求依据','质量目标'],decisions,'1,4,3,3'))
 add('''=== 架构故事

以下四个场景分别对应效用树叶节点。安全使用确定的拒绝要求，可修改性使用配置切换与相同契约验证；性能和恢复时长先测量，结果随基线报告归档。
''')
 stories=[
 ('S1 安全 U-S1','REQ-N-01','管理员撤销课程审核权限后，教师继续使用原登录令牌提交审核。平台依据已提交的 RBAC 关系拒绝该次操作，课程数据保持原值。',[('刺激源','具有有效课程令牌的教师'),('刺激','管理员撤销 CR 后发起新审核请求'),('环境','正常运行，撤销事务已经提交'),('制品','身份鉴权、授权关系和课程审核入口'),('响应','查询当前权限，返回拒绝并保留课程原状态'),('响应度量','撤权提交后的测试请求全部返回 403；越权成功数为 0')]),
 ('S2 性能 U-P1','REQ-N-02','在相同用户与授权数据上逐级增加并发权限查询，平台通过连接池及索引执行查询，分别记录两套实现的响应分布和资源使用。',[('刺激源','负载发生器'),('刺激','相同请求组合逐级增加并发'),('环境','与部署说明相同的资源限制及日志级别'),('制品','Bearer 认证、授权查询和数据库连接池'),('响应','完成查询，记录吞吐、延迟、错误和内存'),('响应度量','按负载记录 P50、P95、吞吐量及错误率；实测结果建立回归基线')]),
 ('S3 可用性 U-A1','REQ-N-03','应用进程退出后，Swarm 重启应用。未过期且未撤销的令牌通过原签名密钥和数据库会话继续验证；依赖中断时拒绝受控操作。',[('刺激源','Swarm 任务退出或服务重启'),('刺激','应用进程退出或身份依赖暂时不可用'),('环境','数据库卷和签名密钥仍可用'),('制品','Swarm 任务、签名配置、数据库会话'),('响应','进程恢复后验证原会话；依赖故障返回 503'),('响应度量','记录首次失败请求至首个成功请求的秒数；原有效会话恢复成功，故障期间越权成功数为 0')]),
 ('S4 可修改性 U-M1','REQ-N-04','开发者把业务配置从 structured 改为 ddd 并重启应用。管理前端、外部课程系统及数据库结构保持相同契约，重复执行既有验收。',[('刺激源','开发者'),('刺激','更换业务策略配置'),('环境','相同镜像、相同数据库和接口'),('制品','用例端口、装配配置及两套业务服务'),('响应','仅装配选定实现，重复执行相同契约'),('响应度量','API、数据库迁移及前端修改数为 0；两套配置的共同契约用例全部通过')])]
 for title,req,text,rows in stories:add('==== '+title+'\n\n'+text+' 对应 '+req+'。\n'+table(['ATAM 要素','内容'],rows,'2,7'))
 add('=== 轻量级 ATAM 评估\n\n==== 质量属性效用树\n\n图 <<OV-UTILITY-01>> 从效用展开到四个场景，叶节点标记重要性与难度。\n'+fig('utility','OV-UTILITY-01 质量属性效用树','OV-UTILITY-01'))
 add('==== 架构方法与高优先级场景\n'+table(['叶节点','架构方法','效果及验证'],[('U-S1','AD-03 实时 RBAC；AD-07 课程端独立检查','旧令牌不携带旧权限；使用真实撤权请求验证。'),('U-P1','AD-05 有界分页、连接池及关联索引','控制内存和数据库排队，使用相同负载记录结果。'),('U-A1','AD-04 外置会话；AD-06 重启策略','进程恢复后重用持久化会话；记录实际恢复时长。'),('U-M1','AD-01、AD-02 共享端口和配置装配','用共同契约套件比较两种配置。')],'2,4,5'))
 findings=[('风险 R-01','AD-05 使用单实例 MySQL；实例失效会中断认证和业务读写；降低节点故障期间的可用性。'),('风险 R-02','AD-06 两个 JVM 同置管理节点；节点失效同时影响入口和应用；应用进程重启不能覆盖节点损失。'),('风险 R-03','AD-03 同步实时鉴权；数据库或身份网络变慢会增加课程调用等待；影响 P95 与吞吐。'),('非风险 N-01','AD-04 在数据库保存撤销记录，权限保存在当前 RBAC 关系；应用重启和旧令牌不会恢复已撤销权限。'),('非风险 N-02','AD-01、AD-02 两套实现共用契约和存储约束，配置切换可用同一套测试验证。'),('敏感点 SENS-01','数据库连接池上限和 SQL 索引显著影响请求排队及 P95。'),('敏感点 SENS-02','签名密钥持久化与数据库卷直接决定应用重启后的令牌及会话可验证性。'),('权衡 T-01','实时查权限提高撤权及时性，同时增加数据库访问量。'),('权衡 T-02','有限服务器上使用单副本降低内存占用，同时保留入口和数据库单点。')]
 add('==== 评估发现\n'+table(['类型与编号','决策、论证及质量影响'],findings,'2,7'))
 add('==== 改进结论\n'+table(['改进','落实情况'],[('持久化会话和签名密钥，统一检查目标系统、会话及实时权限','已纳入 AD-03、AD-04 与实现。'),('限定连接池、分页和容器内存，采集相同条件的性能基线','已纳入 AD-05、AD-06；数值结果见实施报告。'),('保留应用健康检查、Swarm 重启策略和手动备份恢复步骤','写入部署操作说明与可用性架构故事。')],'5,4'))
 add('''== 模块设计与逻辑架构

=== 包图与分层

逻辑划分见 <<OV-PKG-01>>。本图到包和组件粒度，模块内部类见第 4 章与详细设计文档。
'''+fig('packages','OV-PKG-01 系统包依赖','OV-PKG-01'))
 add('=== 模块职责与功能分配\n'+table(['模块','职责和核心输入输出','需求'],[('API 输入层','接收 HTTP 路径与请求数据，校验输入，返回结果或统一错误','REQ-F-01 至 REQ-F-07'),('认证与身份','验证用户名手机号、管理技术会话、返回身份及许可','REQ-F-01；REQ-N-01'),('用户管理','接收操作者、用户及角色编号，查询本部门账号及维护其角色','REQ-F-02'),('角色授权','接收角色和权限标识，维护本部门角色、权限及无环继承','REQ-F-03'),('目录管理','查询部门树、业务系统及权限目录','REQ-F-04 至 REQ-F-06'),('Service Port','为两套业务实现定义相同用例及数据类别','REQ-N-04'),('Infrastructure','持久化、签名与验签、分层日志和环境装配','REQ-N-01 至 REQ-N-05'),('课程适配','读取统一身份、查询 API 许可、保留课程业务检查','REQ-F-07')],'2,5,3'))
 add('=== 模块间接口契约\n'+table(['接口编号','接口名称与职责','输入 / 输出大类','协议'],[('INT-01','API 到用例端口','操作者、命令或查询 / 资源结果、分页或错误','进程内 Java 调用'),('INT-02','业务到 ManagementStore','对象与关联标识 / 当前数据或持久化结果','进程内存储端口'),('INT-03','认证到 SessionStore','会话身份和有效期 / 当前会话或撤销结果','进程内存储端口'),('INT-04','课程到身份平台','Bearer、operationId 或用户标识 / 身份或 allowed','HTTP/JSON')],'2,4,5,2'))
 add('''== 设计模型

=== 核心设计类图

分析中的用户、角色和权限关系转换为 API 边界、用例控制与领域实体。结构化实现把业务编排放在 Service；DDD 实现把资料修改、角色分配、权限集合变化交给领域对象。核心类及关系见 <<OV-CLASS-01>>、<<OV-CLASS-02>> 和 <<OV-DOMAIN-01>>，不列数据库外键字段。
''')
 add(fig('core-structured','OV-CLASS-01 结构化核心设计类','OV-CLASS-01'));add(fig('core-ddd','OV-CLASS-02 DDD 核心设计类','OV-CLASS-02'));add(fig('domain-relations','OV-DOMAIN-01 DDD 领域对象关系','OV-DOMAIN-01'))
 from v35_content import grasp
 add(grasp(table))
 add(fig('class-hierarchy-structured','RBAC1 结构化继承设计'))
 add(fig('class-hierarchy-ddd','RBAC1 DDD 继承设计'))
 add('=== 关键类状态设计\n\n登录会话具有有效、已撤销、已过期三种状态。有效状态要求当前时间早于到期且未记录撤销时间；退出触发撤销，达到到期时间触发过期。<<OV-STATE-01>> 转换自需求模型 AN-ST-01。\n'+fig('session-state','OV-STATE-01 登录会话状态','OV-STATE-01'))
 add('=== 系统级数据流图\n\n<<OV-DFD-00>> 给出管理用户、课程管理系统与平台之间的数据交换。<<OV-DFD-01>> 沿用这八条边界数据流，展开认证会话、目录查询、角色授权与接入鉴权。\n'+fig('dfd-0','OV-DFD-00 顶层数据流','OV-DFD-00')+fig('dfd-1','OV-DFD-01 第一层数据流','OV-DFD-01'))
 add(table(['加工','数据流到模块的映射','全局数据'],[('P1 认证会话','登录、退出、本人资料请求映射 AuthService','用户与授权关系、登录会话'),('P2 目录查询','目录请求映射用户、部门、系统、权限用例模块','用户、部门、系统和权限'),('P3 角色授权','授权请求映射角色用例和用户角色用例','角色及授权、继承关联'),('P4 身份鉴权','外部查询映射认证、AccessPolicy 与身份适配','用户、角色、权限、登录会话')],'2,5,4'))
 add('=== 关键运行时设计\n\n三张时序图使用模块为生命线，分别对应 S1/U-S1、S2/U-P1 和 S3/U-A1。图中展示跨模块消息及响应，详细功能交互见合订本对应功能。')
 for stem,title in [('security','S1 / U-S1 撤权后的调用'),('performance','S2 / U-P1 并发鉴权'),('availability','S3 / U-A1 应用恢复与会话')]:add(fig('story-'+stem,title))
 add('''== 接口设计

=== 外部接口

用户界面为桌面浏览器 Web 页面，提供列表、表单及授权选择；两种后台策略使用同一套界面。浏览器使用鼠标、键盘和屏幕输入输出。

客户端通过浏览器与服务器通信。

第三方软件接口为老师课程管理系统，使用 HTTP/JSON 和 Authorization Bearer。课程前端请求课程后端登录，课程后端向平台申请目标系统令牌。后续业务请求由课程后端验签，并向平台查询当前身份和操作许可。对应 REQ-F-07、AD-07。
''')
 add('界面权限与 API 权限共用 RBAC 关系并分别授权。界面使用独立菜单和按钮码，接口按 operationId 对应的 API 码及对象规则决定是否执行。课程页面初始化调用 GET /course-api/me，按平台 userId 读取本地教师或学生资料。课程前端通过 VITE_IDENTITY_BASE_URL 直连平台读取菜单许可和教师候选。跨域预检按配置来源放行，业务请求继续执行 JWT 与 RBAC 检查。')
 add(table(['外部接口编号','路径','核心输入','核心输出'],[('EXT-01','POST /auth/login','用户名、手机号和目标系统','Bearer 凭据及有效期'),('EXT-02','GET /auth/me','Bearer','当前身份、角色、当前系统界面码'),('EXT-03','GET /auth/permissions/{permission}','Bearer 与独立界面码','allowed'),('EXT-04','GET /auth/api-permissions/{operationId}','Bearer 与操作编号','operationId、allowed'),('EXT-05','GET /auth/users/{userId}','Bearer 与用户标识','用户标识、工号和姓名'),('EXT-06','POST /auth/logout','Bearer','撤销结果'),('EXT-07','GET /auth/departments','Bearer、parentId、keyword、分页','部门及展开提示'),('EXT-08','GET /auth/departments/{departmentId}/users','Bearer、departmentId、keyword、分页','身份候选人')],'2,5,3,3'))
 add('=== 内部系统级接口\n\n前后端使用 HTTP/JSON；平台管理请求要求 rbac audience 与相应操作权限。以下按模块列出路由和数据类别，具体字段见详细设计文档。')
 add(table(['接口编号','路由范围','核心输入 / 输出','需求'],[('API-U','/api/users 及用户角色子资源','用户和角色编号 / 用户分页及已分配角色','REQ-F-02'),('API-R','/api/roles 及角色权限子资源','角色名称与权限标识 / 角色、权限或分页结果','REQ-F-03'),('API-D','/api/departments','分页参数 / 部门树节点','REQ-F-04'),('API-S','/api/systems','分页 / 系统目录','REQ-F-05'),('API-P','/api/systems/{systemId}/permissions','系统编号 / 权限目录','REQ-F-06'),('API-C','/course-api/login、/course-api/teacher/courses、/course-api/me/courses、/course-api/teachers 及其余接入业务路由','课程工号、学号、查询条件或管理输入 / 师生工作台与管理结果','REQ-F-07')],'2,5,4,2'))
 add('''== 数据设计与存储策略

=== 全局逻辑结构

实体关系见 <<OV-ER-01>>。部门构成树，用户和角色各属于一个部门。用户与角色、角色与权限均为多对多。业务系统拥有权限。登录会话关联用户，适用的目标系统保存在已签发令牌的 aud 中。
'''+fig('chen','OV-ER-01 高层陈氏 ER 图','OV-ER-01'))
 add(fig('schema','数据库表关系图'))
 add('=== 概要级表结构\n'+table(['表','职责','主要字段及主外键'],[(x[0],x[1],x[2]) for x in TABLES],'2,2,6'))
 add('''=== 存储与物理策略

MySQL InnoDB 保存业务数据和登录会话，利用事务、外键及唯一约束维护关系一致性。平台与课程系统使用不同数据库和账号。当前采用单实例，不设置读写分离、分片或额外缓存；实体编号由数据库自增生成，登录会话使用随机 UUID。

课程库师生名册以 user_id 引用平台用户编号，工号或学号对应平台用户名。演示导入在一个 MySQL 事务中写两库，以部门归属创建角色并绑定已有平台用户，课程库按名册唯一键核对。

授权从数据库当前关系查询，避免缓存失效改变撤权语义。用户、角色列表按部门及分页读取；关联索引用于当前权限查询。数据库连接池有界，性能场景在相同数据与配置下测量。

备份保存两个数据库及迁移版本，签名密钥独立保管。恢复后核对记录及授权关系。引用关系和技术会话均在备份范围内。

== 部署设计

=== 部署图

物理部署见 <<OV-DEPLOY-01>>。三台服务器维持现有 Swarm 角色，第四台用于辅助；原有 MongoDB、Java 应用及监控容器保留。
'''+fig('deployment','OV-DEPLOY-01 Docker Swarm 部署','OV-DEPLOY-01'))
 add('=== 拓扑与资源说明\n'+table(['节点','资源与地址','当前项目部署'],[('Manager','2 vCPU，约 1.8 GiB 可见内存；113.44.144.119','入口与前端，RBAC、课程应用'),('Worker 1','2 vCPU，约 1.8 GiB；119.3.221.88','MySQL 与独立数据卷'),('Worker 2','2 vCPU，约 792 MiB；120.46.176.79','保留已有服务，本轮不新增 JVM'),('辅助节点','2 vCPU，约 792 MiB；1.92.106.198','备份及验收辅助')],'2,5,4'))
 add('''浏览器经公网 HTTP 访问入口，服务与 MySQL 经加密 Overlay 通信，数据库不发布公网端口。密钥和密码通过 Docker Secret 提供。项目使用独立网络、数据卷和服务名；资源限制与健康检查控制应用进程故障后的重启。

入口、两个 JVM 与数据库均为单副本。可用性设计覆盖应用健康检查和持久化会话，单管理节点及数据库节点故障列于 ATAM 风险。

== 非功能需求实现方案
''')
 add(table(['需求','架构及模块落实','验证证据'],[('REQ-N-01 安全','AD-03、AD-04、AD-07；字段白名单、单部门边界、实时 RBAC、会话撤销','S1；共同契约及真实课程撤权测试'),('REQ-N-02 性能','AD-05；分页、连接池与关系索引，记录同条件两种策略结果','S2；性能基线报告'),('REQ-N-03 可用性','AD-04、AD-06；持久化会话、密钥、数据库卷与 Swarm 重启','S3；健康检查和会话复用'),('REQ-N-04 可修改性','AD-01、AD-02；共享用例端口与独立领域模型','S4；两种配置的相同测试'),('REQ-N-05 可重复部署','固定教师提交、依赖版本、容器构建及独立部署资源','构建日志、镜像及部署记录')],'3,6,4'))
 add('=== 架构验证结果\n\n下表保留 v3.3.0 的部署实测基线。本版的代码回归和线上联调见附录的交付记录。\n'+table(['场景','实测结果与证据'],[
 ('S1 安全','真实课程联调 19 项检查通过；撤销 CR 后原令牌审核返回 403，恢复授权后成功。'),
 ('S2 性能','1、4、8 并发混合查询共 10,000 次，无错误；8 并发三轮 P95：结构化 36.67 至 52.61 ms，DDD 36.90 至 55.83 ms。'),
 ('S3 可用性','应用重启首次失败至恢复 34.477 s；身份依赖中断至课程恢复 39.365 s，故障期间课程端返回 503。原令牌恢复可用。'),
 ('S3 数据恢复','两个数据库备份导入隔离临时 MySQL；按主键排序的数据导出 SHA-256 一致，辅助节点副本摘要一致。'),
 ('S4 可修改性','结构化与 DDD 的共同契约各 11 项通过；同一镜像、接口及数据库完成策略切换。')],'2,7'))
 add('入口、应用和数据库单副本对应 R-01、R-02；上述恢复结果覆盖进程及依赖恢复。首次性能测量的一轮出现 2 次网关 504，启用上游连接复用后完成全部正式测量。')
 add('== 附录 需求可追溯性矩阵\n\n需求编号对应当前合订本第 2 章。所有决策、模块、接口和数据策略均在下表关联；硬件接口不适用原因保留在第 5 章。')
 rows=[]
 for module,(title,req,data) in MODULES.items():
  rows.append((req,title,'AD-01、AD-02、AD-03；S1',module+' 用例、对应控制器','EXT-01 至 EXT-08' if module=='Auth' else {'User':'API-U','Role':'API-R','Department':'API-D','System':'API-S','Permission':'API-P'}[module]+'；INT-01、INT-02',data))
 rows += [('REQ-F-07','课程接入','AD-07；S1','课程适配模块','EXT-01 至 EXT-08；API-C；INT-04','用户、角色、权限及关联'),('REQ-N-01','安全','AD-03、AD-04、AD-07；S1','认证、AccessPolicy、课程适配','EXT-01 至 EXT-08；全部 API','用户、会话、两组授权关联'),('REQ-N-02','性能','AD-05；S2','授权查询、Spring Data JPA','INT-02；EXT-03、EXT-04','关联索引、业务数据'),('REQ-N-03','可用性','AD-04、AD-05、AD-06；S3','会话模块、部署和备份','INT-03；全部 API','会话、全部业务表及数据卷'),('REQ-N-04','可修改性','AD-01、AD-02；S4','两套业务实现、共享端口','INT-01、INT-02','共享表结构'),('REQ-N-05','可重复部署','AD-06、AD-07','配置、镜像及教师适配','INT-04；部署入口','独立数据库与迁移版本')]
 add(table(['需求编号','需求名称','故事 / 决策','模块 / 类','内部 / 外部接口','全局数据实体'],rows,'2,2,3,3,3,3'))
 add('=== 需求分析模型引用清单\n'+table(['图编号','模型','转换与使用'],[('AN-UC-01','系统用例图','第 3 章职责分配与功能追溯'),('AN-ACT-01','登录活动','认证模块和运行时消息'),('AN-ACT-02','角色分配活动','用户、角色及边界规则'),('AN-CC-01','概念实体关系','核心设计类图与全局 ER'),('AN-ST-01','会话状态','OV-STATE-01'),('AN-DFD-00 / AN-DFD-01','顶层与第一层分析数据流','OV-DFD-00 / OV-DFD-01，对照模块与存储映射表')],'3,4,5'))
 add(verification(table))
 (R/'统一权限管理系统概要设计说明书.adoc').write_text('\n'.join(p).rstrip()+'\n')
if __name__=='__main__':detail();outline()
