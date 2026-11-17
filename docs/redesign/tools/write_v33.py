"""Author the current requirements/detail specification and the separate outline design."""
from pathlib import Path
import re,json
from v33_catalog import OPS,MODULES,REQS
R=Path(__file__).resolve().parents[1];VERSION=(R/'VERSION').read_text().strip()
HEADER='''= {title}
OOAD 课程项目组
v{version}, 2026-11-17
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

def fig(stem,title,anchor=None):return f'\n[.diagram.plate]\n--\n[[{anchor or "fig-"+stem}]]\n.{title}\nimage::v33/{stem}.svg[{title},width=100%]\n--\n'
def refs():return table(['参考资料','使用范围'],[
 ('GB/T 8567—2006《计算机软件文档编制规范》','概要设计的目的、结构、接口与数据设计组织；章节对应关系见本文。'),
 ('https://www.omg.org/spec/UML/2.5.1[OMG UML 2.5.1]','类、包、状态、部署、活动、用例和时序图语义。'),
 ('https://docs.staruml.io/[StarUML 官方文档]','模型元素和可编辑图稿。'),
 ('https://www.csc.lsu.edu/~chen/pdf/erd-5-pages.pdf[Chen ER 建模论文]','实体、联系、属性、键和参与约束。'),
 ('《5. 面向对象设计》课程课件','按用户提供的 142 页课件核对类图、分层与 DDD 职责。'),
 ('需求与详细设计说明书 v2','字体、字号、用例表配色、接口调用说明和分页形式。'),
 ('https://gitee.com/mingqcn/course-project-mgt[课程管理系统] c96efa2','四层组织、双实现、统一身份接口和权限码。'),
 ('https://gitee.com/mingqcn/spring-fundamentals[Spring 教学示例] 57c53ff','CloneFactory 0.0.2 编译期拷贝工具。'),
 ('本项目 v3.2.2 与 2026-11-17 确认决定','当前修订基线；性能指标按本次实测建立。')],'3,5')
CONSTRAINTS=[
 ('BR-01','用户通过角色获得权限；用户角色、角色权限均为多对多关系。'),
 ('BR-02','每个用户和角色属于一个部门；管理员管理本部门直接成员。'),
 ('BR-03','角色分配要求操作者、目标用户、目标角色属于同一部门。'),
 ('BR-04','角色可包含两个业务系统的权限；部门不直接获得系统权限。'),
 ('BR-05','用户名全局唯一、部门内角色名唯一、系统内权限码唯一、系统标识唯一。'),
 ('BR-06','用户名、部门、系统标识、权限所属系统和权限码创建后固定；普通编辑使用字段白名单。'),
 ('BR-07','登录使用用户名和手机号，password 字段承载手机号；systems 只接受一个目标系统。'),
 ('BR-08','成功登录必须拥有目标系统 login 权限；每个受保护请求实时检查会话及登录许可。'),
 ('BR-09','JWT 保存身份和目标系统，会话保存在 MySQL；权限按当前授权关系读取。'),
 ('BR-10','用户删除同时清理登录会话和用户角色；其他对象存在引用时先撤销关联再删除。'),
 ('BR-11','结构化与 DDD 共用输入输出、存储端口和数据库约束；切换配置保持外部行为一致。'),
 ('BR-12','本版功能为单项维护和授权，范围按已确认的精简设计执行。')]
TABLES=[('department','部门','id PK；name；parent_id FK 自关联','部门 0..1 父节点，0..* 子节点'),('app_user','用户账号','id PK；username 唯一；name；phone；department_id FK 非空','用户属于一个部门'),('role','角色','id PK；name；department_id FK 非空','部门内 name 唯一'),('business_system','业务系统','id PK；system_key 唯一；name','登记 rbac 和 course-project-management'),('permission','操作权限','id PK；system_id FK；code；name','system_id 与 code 联合唯一'),('user_role','用户角色','user_id PK/FK；role_id PK/FK','两列组成联合主键'),('role_permission','角色权限','role_id PK/FK；permission_id PK/FK','两列组成联合主键'),('login_session','技术会话','id PK；user_id FK；system_key；expires_at；revoked_at','会话有效性与撤销验证')]
ERRORS=[('400','INVALID_ARGUMENT','字段、格式、分页或路径编号无效。'),('401','UNAUTHENTICATED','账号凭据、签名、会话或有效期不通过。'),('403','FORBIDDEN','目标系统登录许可、操作权限或部门边界不通过。'),('404','NOT_FOUND','有操作权限后查找的对象不存在。'),('409','CONFLICT','唯一约束、重复分配或引用关系冲突。'),('500','INTERNAL_ERROR','未预期错误，写事务回滚。'),('503','SERVICE_UNAVAILABLE','数据库不可用；课程端使用 IDENTITY_SERVICE_UNAVAILABLE 表达身份依赖故障。')]
COURSE=[('listTerms','GET /terms','login','学期列表'),('createTerm','POST /terms','TC','新建学期'),('updateTerm','PATCH /terms/{termId}','TE','修改非当前学期'),('deleteTerm','DELETE /terms/{termId}','TD','删除非当前且无引用学期'),('listAdminCourses','GET /admin/courses','CV','从 1 开始分页的课程列表'),('getCourse','GET /courses/{courseId}','CV','课程详情，保留老师的对象规则'),('reviewCourse','POST /courses/{courseId}/review','CR','审核待审核课程')]

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
 add(table(['术语','定义'],[('用户','员工与登录账号的统一实体，属于一个部门。'),('角色','属于一个部门的权限集合。'),('权限','在一个业务系统内具有唯一编码的操作许可。'),('Bearer JWT','客户端在 Authorization 请求头提交的签名凭据，不携带角色或权限。'),('登录会话','服务器保存在 MySQL 的技术记录，关联用户、目标系统、到期与撤销时间。'),('结构化实现','业务 Service 在事务内编排规则和存储调用。'),('DDD 实现','应用服务编排用例，聚合根维护用户角色及角色权限等规则。'),('Service Port','两套业务实现共用的用例接口、数据契约和存储端口。'),('CloneFactory','老师提供的编译期拷贝工具，生成 DTO 到 Command 的转换代码。')],'2,6'))
 add('=== 参考资料\n'+refs())
 add('''== 任务概述

=== 功能与非功能需求

当前功能包括认证与鉴权、本部门用户维护、部门目录、角色和授权、系统与权限目录，以及课程系统的学期维护与课程审核接入。审计、批量、导入导出、部门调动、账号启停和 OAuth/OIDC 保持已确认的范围决定。
''')
 add(table(['需求编号','需求名称'],REQS,'2,6'))
 add('=== 业务约束\n'+table(['编号','约束'],CONSTRAINTS,'1,7'))
 add('''=== 初始化与演示数据

初次部署在空库中显式初始化两个业务系统及其 login 权限。演示数据建立教学部与教务部、admin、reviewer、viewer 及 other 四个合成账号；管理员与普通用户使用相同实体，通过角色权限区分。

初始化由 app.demo.enabled 和 app.demo.phone 控制，检测已有用户时跳过。演示手机号由环境或 Secret 注入。数据库迁移创建结构，演示初始化独立于迁移执行。

=== 需求分析模型

当前用例见 <<AN-UC-01>>。角色分配活动见 <<AN-ACT-02>>，概念实体及关系见 <<AN-CC-01>>。会话状态模型及系统数据流分别以 <<AN-ST-01>>、<<AN-DFD-00>> 和 <<AN-DFD-01>> 标识，供概要设计的转换和追溯引用。
''')
 for stem,title,id in [('usecases','AN-UC-01 当前基线用例图','AN-UC-01'),('activity-login','AN-ACT-01 登录活动','AN-ACT-01'),('activity-assign','AN-ACT-02 角色分配活动','AN-ACT-02'),('chen','AN-CC-01 概念实体关系','AN-CC-01'),('session-state','AN-ST-01 会话状态','AN-ST-01'),('dfd-0','AN-DFD-00 顶层数据流','AN-DFD-00'),('dfd-1','AN-DFD-01 第一层数据流','AN-DFD-01')]:add(fig(stem,title,id))
 add('''== 数据描述

=== 概念与关系模型

五类业务实体为部门、用户、角色、权限和业务系统。用户角色与角色权限两组联系分别落为关联表。登录会话是实现认证撤销和恢复的技术结构，单列说明。关系模型见 <<fig-schema>> 与 <<fig-session-table>>。
''')
 add(fig('schema','业务数据库表关系'));add(fig('session-table','技术会话表关系'))
 add('=== 数据字典\n'+table(['表名','职责','主要字段与键','约束'],TABLES,'2,2,5,4'))
 fields=[('department','id BIGINT 自增；name VARCHAR(100)；parent_id BIGINT 可空。'),('app_user','id BIGINT 自增；username VARCHAR(64)；name VARCHAR(100)；phone VARCHAR(20)；department_id BIGINT 非空。'),('role','id BIGINT 自增；name VARCHAR(100)；department_id BIGINT 非空。'),('business_system','id BIGINT 自增；system_key VARCHAR(64)；name VARCHAR(100)。'),('permission','id BIGINT 自增；system_id BIGINT；code VARCHAR(100)；name VARCHAR(100)。'),('user_role','user_id BIGINT 与 role_id BIGINT 组成联合主键，各自为外键。'),('role_permission','role_id BIGINT 与 permission_id BIGINT 组成联合主键，各自为外键。'),('login_session','id CHAR(36)；user_id BIGINT；system_key VARCHAR(64)；expires_at TIMESTAMP(6)；revoked_at TIMESTAMP(6) 可空。')]
 for name,description in fields:add('==== '+name+'\n\n'+description)
 add('''=== 索引、事务与删除

数据库采用 MySQL 8.4 InnoDB，文本使用区分大小写的排序规则。用户名和系统标识建立唯一索引，角色名及权限编码采用所属范围内的联合唯一索引。两个授权关联表使用联合主键，并为反向关联列建立索引。

写用例在 Service 事务边界内执行。用户删除先清理会话与用户角色，再删除账号；角色、权限、系统和部门存在引用时返回冲突。部门父节点创建时确定，接口仅支持重命名。

关联查询通过用户角色和角色权限读取当前并集。每个鉴权请求检查已提交的授权关系；已进入事务的请求按数据库事务可见性完成。唯一约束负责并发重复写的最终裁决。

== 功能需求与详细设计

=== 总体架构与对象职责

系统总体分层见 <<fig-architecture>>，包依赖见 <<fig-packages>>。API 通过用例端口调用启动配置选中的服务；存储适配器使用 Spring JDBC，显式 SQL 集中在 Infrastructure。认证为共享装配。
''')
 add(fig('architecture','总体分层架构'));add(fig('packages','完整 UML 包图'));add(fig('packages-files','源文件与配置文件组织'));add(fig('domain','DDD 领域对象关系'))
 add('''=== 功能描述

以下每项列出需求、调用说明与详细交互。写请求先校验输入并使用 CloneFactory 转为用例输入；操作者身份由 BearerFilter 和 CurrentIdentity 提供。DDD 的 User、Role 维护角色及权限标识集合，其他简单实体维护自身名称变化。
''')
 for module in ['Auth','User','Role','Department','System','Permission']:
  title,req,entities=MODULES[module];add('==== '+title)
  add(table(['项目','内容'],[('用例编号',req),('参与者','已登录管理用户；认证登录允许匿名提交凭据。' if module=='Auth' else '具有相应管理权限的本部门用户。'),('目标',title+'操作与当前授权一致。'),('前置条件','账号存在，目标系统及操作许可有效；目录和对象边界通过。'),('主成功场景','输入校验、认证与权限检查、对象加载、业务处理、提交及返回结果。'),('扩展场景','输入无效 400；未认证 401；越权 403；不存在 404；引用或重复 409；依赖故障 503。'),('相关数据',entities)],'2,7','role="use-case"'))
  for op in [o for o in OPS if o.module==module]:
   add('===== '+op.key+' '+op.title)
   add(table(['调用说明','内容'],[('调用 URL',op.method+' '+op.route),('功能',op.rule),('权限',op.permission),('结果',op.result),('输入','路径标识、分页或专用请求 DTO；只接收本功能声明字段。'),('失败处理','按统一异常表返回；数据库写失败回滚当前事务。')],'2,7'))
   if module=='Auth' and op.function!='updateMe':add(fig(op.key.lower()+'-shared',op.key+' 共享认证交互'))
   else:
    for mode,label in [('structured','结构化'),('ddd','DDD')]:add(fig(op.key.lower()+'-'+mode,op.key+' '+label+' 详细交互'))
  if module=='Auth':add(fig('class-auth-shared','共享认证模块类图'))
  if module!='Auth':
   add('===== '+title+'模块类图')
   for mode,label in [('structured','结构化'),('ddd','DDD')]:add(fig('class-'+mode+'-'+module.lower(),title+' '+label+' 模块类图'))
 add('==== 课程管理系统接入\n\n需求 REQ-F-07。课程前端直接向平台 /auth/login 登录，携带 course-project-management audience 的令牌调用老师后端。反向代理把 /course-api/ 前缀剥离后转发。老师的业务规则保留，适配层完成验签、实时身份与 API 权限查询。')
 add(table(['operationId','老师 API','权限码','行为'],COURSE,'3,5,1,4'))
 for i,(operation,route,permission,behavior) in enumerate(COURSE,1):
  add('===== COURSE-'+str(i).zfill(2)+' '+operation)
  add(table(['调用说明','内容'],[('调用 URL',route),('operationId',operation),('权限码',permission),('行为',behavior)],'2,7'))
  add(fig('course-'+str(i).zfill(2),operation+' 接入详细交互'))
 add('''课程列表 page 从 1 开始，courseStatus 为 1 申请、2 待审核、3 已开课。审核输入 decision 为 approved 或 rejected，驳回必须填写 comment。菜单来自当前权限，后端仍独立检查。该课程状态属于老师系统的课程对象。

接口适配补丁固定到 c96efa2，保留 MIT 许可。其他老师项目路由在此演示装配中拒绝访问；选定业务服务仍使用老师原实现。

=== 公共接口与输入规范

平台列表 page 从 0 开始，size 默认为 20、最大 100，page 最大 1000000。平台实体编号在 JSON 中为十进制字符串，避免前端精度损失。老师课程接口保留原分页及编号格式。

路径编号要求正整数。用户名长度 1 至 64，名称 1 至 100，手机号为中国大陆 11 位数字格式。系统标识使用小写字母、数字和连字符；权限码允许大小写字母、数字、点、冒号、下划线和连字符，以兼容 TC、CR 等编码。

创建、资料修改、重命名和分配角色使用不同 DTO。未声明字段返回 400。普通资料修改只接受 name、phone，空修改返回 400；未提交字段保持原值。手机号变更后新登录使用新手机号，当前会话保留至撤销或到期。

=== 认证与异常

JWT 使用 RS256，携带 iss、sub、aud、jti、iat 和 exp，默认有效期 30 分钟。BearerFilter 校验签名、到期时间、单个目标系统、数据库会话、当前账号及 login 权限。目标系统和用户须与会话一致。

/auth/me 返回 subject、username、name、departmentId、roles、permissions；/identity/users/{id} 返回 userId、no、name。角色名称用于显示，管理资格由权限集合确定。权限检查返回 allowed 布尔值。
''')
 add(table(['HTTP','业务码','含义'],ERRORS,'1,3,6'))
 add('''=== CloneFactory 与分层日志

请求 record 使用 @CopyTo 声明 Command 目标类型，由老师 CloneFactory 注解处理器生成拷贝方法。编译检查生成方法，运行时直接调用生成代码。

API 记录方法、路由、结果和耗时；业务服务记录用例、操作者与目标标识；存储适配记录操作名称、结果和耗时。DDD 领域对象保持框架无关，其结果由应用服务记录。SLF4J 统一输出到 Logback 控制台和滚动文件。异常边界仅对未预期故障记录完整堆栈。

== 性能需求

=== 测量方法

本版先建立实测基线。登录、用户列表、权限查询分别记录吞吐量、P50、P95、错误率与内存；两套实现使用相同数据、连接池、容器资源和负载。报告保存硬件、数据规模、测试日期和原始结果，回归时按同一条件比较。

列表采用有界分页，数据库连接池限制为 5。实时授权查询使用关联表主键及反向索引，权限保存在数据库当前关系中。指标与验收结论见实施报告，历史版本中的高并发指标由本次基线替换。

=== 精度与一致性

手机号与系统标识按字符串处理，权限比较区分大小写。角色权限按集合取并集。JWT 不缓存权限；撤销提交后的新请求读取当前关系。写失败回滚，重复分配由数据库联合主键裁决。

== 运行需求

=== 配置与启动
''')
 add(table(['配置项','用途'],[('BACKEND_STRATEGY','structured 或 ddd，默认 structured，启动时装配。'),('DB_URL / DB_USER / DB_PASSWORD','RBAC 数据库连接，凭据由环境或 Secret 注入。'),('JWT_PRIVATE_KEY / JWT_PUBLIC_KEY','持久化 RSA 签名密钥和校验公钥的 Resource 路径。'),('app.jwt.lifetime','令牌及数据库会话的有效期，默认 30 分钟。'),('app.demo.enabled / app.demo.phone','空库演示初始化。'),('logging.config / LOG_PATH','Logback 及滚动日志目录。')],'3,5'))
 add('''源码构建使用 Java 21、Spring Boot 4.1.2 和 CloneFactory 0.0.2；前端使用 Vue 3.5、TypeScript 5.7 与 Vite 6。Maven Wrapper 固定版本并使用 HTTPS 镜像。构建与检查入口为 tooling/test.sh。

=== Swarm 部署与恢复

部署复用现有三节点 Swarm，第四台作为辅助节点。管理节点运行两个 JVM 和 Nginx，第一个工作节点运行 MySQL，第二个工作节点保留原应用。数据库及日志使用独立卷，密钥和数据库密码使用 Docker Secret。

演示入口采用 IP 加 HTTP。数据库不发布公网端口，服务通过加密 Overlay 通信。备份在辅助节点保存，恢复验证使用独立临时数据库。单管理节点和单数据库实例的故障范围在概要设计中列为架构风险。

=== 图文与构建产物

当前图稿由手工坐标生成 SVG、PNG 和 StarUML 源文件，入口为 draw_v33.py 与 draw_data_v33.py。文字入口为 write_v33.py；历史生成器不用于当前交付。导出时核对版本、图引用、页面尺寸和目录页码。

== 其他需求与验收

=== 验收场景
''')
 add(table(['编号','场景','通过条件'],[('ACC-01','两种业务策略','同一契约套件在 structured、ddd 下通过。'),('ACC-02','RBAC 与部门边界','无许可和跨部门调用返回 403，已有授权保持并集语义。'),('ACC-03','输入与数据库','非法、未知字段拒绝；唯一和引用冲突返回 409。'),('ACC-04','会话及撤权','退出、到期、用户删除和权限撤销对后续请求生效。'),('ACC-05','课程接入','真实学期 CRUD、课程列表和审核；撤权后原令牌被拒绝。'),('ACC-06','日志','浏览器功能请求可对应 API、Service 和存储层日志，凭据不进入日志。'),('ACC-07','恢复和性能','记录实际恢复时长及两种配置的相同负载结果。'),('ACC-08','文档与图','两份 PDF 均为统一 A4 纵向，源文件可编辑，编号和接口对应。')],'2,3,5'))
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
 ('S3 可用性 U-A1','REQ-N-03','应用进程退出后，Swarm 重启应用。未过期且未撤销的令牌通过原签名密钥和数据库会话继续验证；依赖中断时拒绝受控操作。',[('刺激源','运行平台或故障注入脚本'),('刺激','应用进程退出或身份依赖暂时不可用'),('环境','数据库卷和签名密钥仍可用'),('制品','Swarm 任务、签名配置、数据库会话'),('响应','进程恢复后验证原会话；依赖故障返回 503'),('响应度量','记录退出至首个成功请求的秒数；原有效会话恢复成功，故障期间越权成功数为 0')]),
 ('S4 可修改性 U-M1','REQ-N-04','开发者把业务配置从 structured 改为 ddd 并重启应用。管理前端、外部课程系统及数据库结构保持相同契约，重复执行既有验收。',[('刺激源','开发者'),('刺激','更换业务策略配置'),('环境','相同镜像、相同数据库和接口'),('制品','用例端口、装配配置及两套业务服务'),('响应','仅装配选定实现，重复执行相同契约'),('响应度量','API、数据库迁移及前端修改数为 0；两套配置的共同契约用例全部通过')])]
 for title,req,text,rows in stories:add('==== '+title+'\n\n'+text+' 对应 '+req+'。\n'+table(['ATAM 要素','内容'],rows,'2,7'))
 add('=== 轻量级 ATAM 评估\n\n==== 质量属性效用树\n\n图 <<OV-UTILITY-01>> 从效用展开到四个场景，叶节点标记重要性与难度。\n'+fig('utility','OV-UTILITY-01 质量属性效用树','OV-UTILITY-01'))
 add('==== 架构方法与高优先级场景\n'+table(['叶节点','架构方法','效果及验证'],[('U-S1','AD-03 实时 RBAC；AD-07 课程端独立检查','旧令牌不携带旧权限；使用真实撤权请求验证。'),('U-P1','AD-05 有界分页、连接池及关联索引','控制内存和数据库排队，使用相同负载记录结果。'),('U-A1','AD-04 外置会话；AD-06 重启策略','进程恢复后重用持久化会话；记录实际恢复时长。'),('U-M1','AD-01、AD-02 共享端口和配置装配','用共同契约套件比较两种配置。')],'2,4,5'))
 findings=[('风险 R-01','AD-05 使用单实例 MySQL；实例失效会中断认证和业务读写；降低节点故障期间的可用性。'),('风险 R-02','AD-06 两个 JVM 同置管理节点；节点失效同时影响入口和应用；应用进程重启不能覆盖节点损失。'),('风险 R-03','AD-03 同步实时鉴权；数据库或身份网络变慢会增加课程调用等待；影响 P95 与吞吐。'),('非风险 N-01','AD-04 在数据库保存撤销记录，权限保存在当前 RBAC 关系；应用重启和旧令牌不会恢复已撤销权限。'),('非风险 N-02','AD-01、AD-02 两套实现共用契约和存储约束，配置切换可用同一套测试验证。'),('敏感点 SENS-01','数据库连接池上限和 SQL 索引显著影响请求排队及 P95。'),('敏感点 SENS-02','签名密钥持久化与数据库卷直接决定应用重启后的令牌及会话可验证性。'),('权衡 T-01','实时查权限提高撤权及时性，同时增加数据库访问量。'),('权衡 T-02','有限服务器上使用单副本降低内存占用，同时保留入口和数据库单点。')]
 add('==== 评估发现\n'+table(['类型与编号','决策、论证及质量影响'],findings,'2,7'))
 add('==== 改进结论\n'+table(['改进','落实情况'],[('持久化会话和签名密钥，统一检查目标系统、会话及实时权限','已纳入 AD-03、AD-04 与实现。'),('限定连接池、分页和容器内存，采集相同条件的性能基线','已纳入 AD-05、AD-06；数值结果见实施报告。'),('设置依赖超时、拒绝策略及数据库备份恢复流程','已纳入 AD-06、AD-07；恢复测试记录实际结果。')],'5,4'))
 add('''== 模块设计与逻辑架构

=== 包图与分层

逻辑划分见 <<OV-PKG-01>>。本图到包和组件粒度，模块内部类见第 4 章与详细设计文档。
'''+fig('packages','OV-PKG-01 系统包依赖','OV-PKG-01'))
 add('=== 模块职责与功能分配\n'+table(['模块','职责和核心输入输出','需求'],[('API 输入层','接收 HTTP 路径与请求数据，校验输入，返回结果或统一错误','REQ-F-01 至 REQ-F-07'),('认证与身份','验证用户名手机号、管理技术会话、返回身份及许可','REQ-F-01；REQ-N-01'),('用户管理','接收操作者与用户资料，维护本部门账号及用户角色','REQ-F-02'),('角色授权','接收角色和权限标识，维护本部门角色与角色权限','REQ-F-03'),('目录管理','维护部门树、业务系统及权限命名空间','REQ-F-04 至 REQ-F-06'),('Service Port','为两套业务实现定义相同用例及数据类别','REQ-N-04'),('Infrastructure','持久化、签名与验签、分层日志和环境装配','REQ-N-01 至 REQ-N-05'),('课程适配','读取统一身份、查询 API 许可、保留课程业务检查','REQ-F-07')],'2,5,3'))
 add('=== 模块间接口契约\n'+table(['接口编号','接口名称与职责','输入 / 输出大类','协议'],[('INT-01','API 到用例端口','操作者、命令或查询 / 资源结果、分页或错误','进程内 Java 调用'),('INT-02','业务到 ManagementStore','对象与关联标识 / 当前数据或持久化结果','进程内存储端口'),('INT-03','认证到 SessionStore','会话身份和有效期 / 当前会话或撤销结果','进程内存储端口'),('INT-04','课程到身份平台','Bearer、operationId 或用户标识 / 身份或 allowed','HTTP/JSON')],'2,4,5,2'))
 add('''== 设计模型

=== 核心设计类图

分析中的用户、角色和权限关系转换为 API 边界、用例控制与领域实体。结构化实现把业务编排放在 Service；DDD 实现把资料修改、角色分配、权限集合变化交给领域对象。核心类及关系见 <<OV-CLASS-01>>、<<OV-CLASS-02>> 和 <<OV-DOMAIN-01>>，不列数据库外键字段。
''')
 add(fig('core-structured','OV-CLASS-01 结构化核心设计类','OV-CLASS-01'));add(fig('core-ddd','OV-CLASS-02 DDD 核心设计类','OV-CLASS-02'));add(fig('domain','OV-DOMAIN-01 DDD 领域对象关系','OV-DOMAIN-01'))
 add('=== 关键类状态设计\n\n登录会话具有有效、已撤销、已过期三种状态。有效状态要求当前时间早于到期且未记录撤销时间；退出触发撤销，达到到期时间触发过期。图 <<OV-STATE-01>> 转换自需求模型 AN-ST-01。\n'+fig('session-state','OV-STATE-01 登录会话状态','OV-STATE-01'))
 add('=== 系统级数据流图\n\n图 <<OV-DFD-00>> 与 <<OV-DFD-01>> 保持相同外部请求与结果名称。D1 代表七张业务表，D2 代表技术登录会话。\n'+fig('dfd-0','OV-DFD-00 顶层数据流','OV-DFD-00')+fig('dfd-1','OV-DFD-01 第一层数据流','OV-DFD-01'))
 add(table(['加工','数据流到模块的映射','全局数据'],[('P1 认证会话','登录、退出、本人资料请求映射 AuthService 和 UserUseCase','用户与授权关系、登录会话'),('P2 目录维护','目录请求映射用户、部门、系统、权限用例模块','用户、部门、系统和权限'),('P3 角色授权','授权请求映射角色用例和用户角色用例','角色及两组关联'),('P4 身份鉴权','外部查询映射认证、AccessPolicy 与身份适配','用户、角色、权限、登录会话')],'2,5,4'))
 add('=== 关键运行时设计\n\n三张时序图使用模块为生命线，分别对应 S1/U-S1、S2/U-P1 和 S3/U-A1。图中展示跨模块消息及响应，详细功能交互见合订本对应功能。')
 for stem,title in [('security','S1 / U-S1 撤权后的调用'),('performance','S2 / U-P1 并发鉴权'),('availability','S3 / U-A1 应用恢复与会话')]:add(fig('story-'+stem,title))
 add('''== 接口设计

=== 外部接口

用户界面为桌面浏览器 Web 页面，提供列表、表单及授权选择；两种后台策略使用同一套界面。浏览器使用鼠标、键盘和屏幕输入输出。

硬件接口：本章节不适用，原因：当前系统通过浏览器与服务器网络通信，不接入专用硬件设备。

第三方软件接口为老师课程管理系统，使用 HTTP/JSON 和 Authorization Bearer。课程前端向平台申请目标系统令牌，课程后端验签并查询当前身份和操作许可。对应 REQ-F-07、AD-07。
''')
 add(table(['外部接口编号','路径','核心输入','核心输出'],[('EXT-01','POST /auth/login','用户名、手机号和目标系统','Bearer 凭据及有效期'),('EXT-02','GET /auth/me','Bearer','当前身份、角色、当前系统权限'),('EXT-03','GET /auth/permissions/{permission}','Bearer 与权限码','allowed'),('EXT-04','GET /auth/api-permissions/{operationId}','Bearer 与操作编号','allowed'),('EXT-05','GET /identity/users/{id}','Bearer 与用户标识','用户标识、工号和姓名'),('EXT-06','POST /auth/logout','Bearer','撤销结果')],'2,5,3,3'))
 add('=== 内部系统级接口\n\n前后端使用 HTTP/JSON；平台管理请求要求 rbac audience 与相应操作权限。以下按模块列出路由和数据类别，具体字段见详细设计文档。')
 add(table(['接口编号','路由范围','核心输入 / 输出','需求'],[('API-U','/api/users 及用户角色子资源','资料、用户角色标识 / 用户、角色或分页结果','REQ-F-02'),('API-R','/api/roles 及角色权限子资源','角色名称与权限标识 / 角色、权限或分页结果','REQ-F-03'),('API-D','/api/departments','名称和父节点 / 部门目录','REQ-F-04'),('API-S','/api/systems','系统标识和名称 / 系统目录','REQ-F-05'),('API-P','/api/systems/{id}/permissions 与 /api/permissions/{id}','编码、名称或权限标识 / 权限目录','REQ-F-06'),('API-C','/course-api/terms、/course-api/admin/courses、/course-api/courses/{id}/review','学期资料、分页或审核决定 / 老师业务结果','REQ-F-07')],'2,5,4,2'))
 add('''== 数据设计与存储策略

=== 全局逻辑结构

实体关系见 <<OV-ER-01>>。部门构成树，用户和角色各属于一个部门；用户与角色、角色与权限均为多对多。业务系统拥有权限。登录会话作为技术结构关联用户与目标系统。
'''+fig('chen','OV-ER-01 高层陈氏 ER 图','OV-ER-01'))
 add('=== 概要级表结构\n'+table(['表','职责','主要字段及主外键'],[(x[0],x[1],x[2]) for x in TABLES],'2,2,6'))
 add('''=== 存储与物理策略

MySQL InnoDB 保存业务数据和登录会话，利用事务、外键及唯一约束维护关系一致性。平台与课程系统使用不同数据库和账号。当前采用单实例，不设置读写分离、分片或额外缓存；实体编号由数据库自增生成，登录会话使用随机 UUID。

授权从数据库当前关系查询，避免缓存失效改变撤权语义。用户、角色列表按部门及分页读取；关联索引用于当前权限查询。数据库连接池有界，性能场景在相同数据与配置下测量。

备份保存两个数据库及迁移版本，签名密钥独立保管。恢复先在临时数据库核对表数、记录及授权关系，再记录恢复结果。引用关系和技术会话均在备份范围内。

== 部署设计

=== 部署图

物理部署见 <<OV-DEPLOY-01>>。三台服务器维持现有 Swarm 角色，第四台用于辅助；原有 MongoDB、Java 应用及监控容器保留。
'''+fig('deployment','OV-DEPLOY-01 Docker Swarm 部署','OV-DEPLOY-01'))
 add('=== 拓扑与资源说明\n'+table(['节点','资源与地址','当前项目部署'],[('Manager','2 vCPU，约 1.8 GiB 可见内存；113.44.144.119','入口与前端，RBAC、课程应用'),('Worker 1','2 vCPU，约 1.8 GiB；119.3.221.88','MySQL 与独立数据卷'),('Worker 2','2 vCPU，约 792 MiB；120.46.176.79','保留已有服务，本轮不新增 JVM'),('辅助节点','2 vCPU，约 792 MiB；1.92.106.198','备份及验收辅助')],'2,5,4'))
 add('''浏览器经公网 HTTP 访问入口，服务与 MySQL 经加密 Overlay 通信，数据库不发布公网端口。密钥和密码通过 Docker Secret 提供。项目使用独立网络、数据卷和服务名；资源限制与健康检查控制应用进程故障后的重启。

入口、两个 JVM 与数据库均为单副本。可用性验证覆盖进程恢复、依赖故障拒绝和数据恢复，单管理节点及数据库节点故障列于 ATAM 风险。

== 非功能需求实现方案
''')
 add(table(['需求','架构及模块落实','验证证据'],[('REQ-N-01 安全','AD-03、AD-04、AD-07；字段白名单、单部门边界、实时 RBAC、会话撤销','S1；共同契约及真实课程撤权测试'),('REQ-N-02 性能','AD-05；分页、连接池与关系索引，记录同条件两种策略结果','S2；性能基线报告'),('REQ-N-03 可用性','AD-04、AD-06；持久化会话、密钥、数据库卷、进程重启与备份','S3；恢复与依赖故障报告'),('REQ-N-04 可修改性','AD-01、AD-02；共享用例端口与独立领域模型','S4；两种配置的相同测试'),('REQ-N-05 可重复部署','固定教师提交、依赖版本、容器构建及独立部署资源','构建日志、镜像及部署记录')],'3,6,4'))
 add('== 附录 需求可追溯性矩阵\n\n需求编号对应当前合订本第 2 章。所有决策、模块、接口和数据策略均在下表关联；硬件接口不适用原因保留在第 5 章。')
 rows=[]
 for module,(title,req,data) in MODULES.items():
  rows.append((req,title,'AD-01、AD-02、AD-03；S1',module+' 用例、对应控制器','EXT-01 至 EXT-06' if module=='Auth' else {'User':'API-U','Role':'API-R','Department':'API-D','System':'API-S','Permission':'API-P'}[module]+'；INT-01、INT-02',data))
 rows += [('REQ-F-07','课程接入','AD-07；S1','课程适配模块','EXT-01 至 EXT-06；API-C；INT-04','用户、角色、权限及关联'),('REQ-N-01','安全','AD-03、AD-04、AD-07；S1','认证、AccessPolicy、课程适配','EXT-01 至 EXT-06；全部 API','用户、会话、两组授权关联'),('REQ-N-02','性能','AD-05；S2','授权查询、JDBC 存储','INT-02；EXT-03、EXT-04','关联索引、业务数据'),('REQ-N-03','可用性','AD-04、AD-05、AD-06；S3','会话模块、部署和备份','INT-03；全部 API','会话、全部业务表及数据卷'),('REQ-N-04','可修改性','AD-01、AD-02；S4','两套业务实现、共享端口','INT-01、INT-02','共享表结构'),('REQ-N-05','可重复部署','AD-06、AD-07','配置、镜像及教师适配','INT-04；部署入口','独立数据库与迁移版本')]
 add(table(['需求编号','需求名称','故事 / 决策','模块 / 类','内部 / 外部接口','全局数据实体'],rows,'2,2,3,3,3,3'))
 add('=== 需求分析模型引用清单\n'+table(['图编号','模型','转换与使用'],[('AN-UC-01','当前基线用例图','第 3 章职责分配与功能追溯'),('AN-ACT-01','登录活动','认证模块和运行时消息'),('AN-ACT-02','角色分配活动','用户、角色及边界规则'),('AN-CC-01','概念实体关系','核心设计类图与全局 ER'),('AN-ST-01','会话状态','OV-STATE-01'),('AN-DFD-00 / AN-DFD-01','顶层与第一层分析数据流','OV-DFD-00 / OV-DFD-01，加入模块映射')],'3,4,5'))
 (R/'统一权限管理系统概要设计说明书.adoc').write_text('\n'.join(p).rstrip()+'\n')
if __name__=='__main__':detail();outline()
