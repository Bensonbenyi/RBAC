"""Detailed architecture and diagram plates embedded in the seven-chapter spec."""
from flow_catalog import MODULES, WRITES, READS, CASE_GROUPS, figures, entries

def plate(stem,title,description=''):
 return f'\n[.diagram.plate]\n--\n[[{stem}]]\n.{title}\nimage::spec/{stem}.svg[{title},width=100%]\n'+(f'\n{description}\n' if description else '')+'--\n'

def architecture():
 s='''=== 两套架构设计

==== 分层与运行装配

两套设计共用 API、Service Port、基础设施及数据库。结构化版以事务脚本组织用例；DDD 版由应用服务编排，领域对象执行资料和授权规则。用例端口的输入输出是 Command、Result 和 Data，JPA PO 保留在基础设施内。
'''
 s+=plate('01-structured-packages','结构化设计 包图')
 s+=plate('02-ddd-packages','DDD 设计 包图')
 s+=plate('architecture-runtime','运行装配与依赖结构','虚线开箭头指向被依赖包。配置在启动时选择服务实现，基础设施实现持久化端口。')
 s+='''[source,yaml]
----
app:
  backend-strategy: structured
----

app.backend-strategy 接受 structured 或 ddd，默认 structured。@ConditionalOnProperty 分别注册两套服务，每个用例端口恰有一个实现 Bean；非法值启动失败。配置切换后 HTTP、表结构和业务结果相同。

==== 对象职责与模块类图

API 按最终资源划分 Controller。UserRoleController 和 RolePermissionController 分别处理两类授权关系；AuthController 处理当前身份与本人资料。每个资源的 UseCase 提供共享接口，两套 Service 实现该接口，Store 隔离持久化技术。

图中实线关联表示持有对象引用，虚线开箭头表示依赖，虚线空心三角表示接口实现。模块图展示写操作签名，查询与授权操作在相应顺序图和授权类图中展开。结构化服务内部的 validateChange 按操作分支执行业务规则。DDD 应用服务从共享 Data 建立领域对象，调用领域行为后将变更写回。
'''
 for m in MODULES:
  s+='\n===== '+m.title+'模块\n'
  s+=plate(f'class-{m.key}-structured',m.title+'模块 结构化类图')
  s+=plate(f'class-{m.key}-ddd',m.title+'模块 DDD 类图')
 s+='\n===== 认证与会话\n'
 s+=plate('class-auth','认证与实时鉴权 类图')
 s+='\n===== 授权关系\n'
 s+=plate('class-relations-structured','授权关系 结构化类图')
 s+=plate('class-relations-ddd','授权关系 DDD 类图')
 s+='''[cols="23,35,42",options="header"]
|===
|领域对象 |持有状态 |领域规则
|User |id、username、name、phone、departmentId、roleIds |changeProfile 仅改姓名与手机号；assignRole 要求同部门且尚未分配；revokeRole 幂等撤销。
|Department |id、name、parentId |改名保持 parentId；引用检查通过后可删除。
|BusinessSystem |id、systemKey、name |创建后 systemKey 固定；有权限时拒绝删除。
|Permission |id、systemId、code、name |名称可改；系统与 code 固定；被角色引用时拒绝删除。
|Role |id、departmentId、name、permissionIds |部门固定；grantPermission 与 revokePermission 幂等变更集合。
|===

以上是五类已有业务实体的对象职责。用户角色和角色权限仍由原有两张关联表保存。跨聚合引用采用编号，应用服务查询必要数据并验证对象范围。唯一性和引用完整性同时由数据库约束保证；并发写入冲突按统一映射处理。

==== 公共请求、拷贝与事务边界

客户端写请求先取得 CSRF 凭据，再提交 Cookie 和指定请求头。Controller 校验输入格式、字段白名单和路径编号，通过 CloneFactory 将 DTO 转为 Command。操作者编号取自服务端会话。受保护请求检查当前账号与目标系统 login 权限，管理操作还要求 rbac 目标系统及相应操作权限。
'''
 s+=plate('flow-preflight','受保护请求 校验顺序图','公共流程适用于受保护查询和写操作。登录、匿名 CSRF 获取与幂等退出采用各自流程。服务在事务内复核操作资格和数据范围。')
 s+=plate('flow-copy','API 边界对象拷贝 顺序图','以新建用户为例；其余 DTO 与 VO 使用相同边界转换，分别声明允许拷贝的字段。')
 s+=plate('flow-adapter','持久化读取与对象拷贝 顺序图','适配器返回共享 Data；DDD 领域对象由应用服务以 Data 构造。集合关联通过专用持久化操作维护。')
 s+='''写操作的服务公开方法是事务边界。图中的加载包含本次需要的对象、归属、现有关系或引用计数；按具体用例读取所需字段。事务脚本 validateChange 逐条校验规则，DDD 构造与行为维护对象不变量，应用服务负责操作权限、跨对象存在性和范围校验。

break 框的守卫为校验失败，成立时终止该用例后续交互。不存在返回 404，范围或操作权限拒绝返回 403，唯一性与引用冲突返回 409。数据库在写入或提交时仍可因并发发生约束冲突，整个事务回滚；数据库不可用返回 503，未预期异常返回 500。正常 HTTP 响应在事务提交成功后发送。

==== 对象拷贝与运行日志

对象拷贝依赖老师的 cn.edu.xmu:clonefactory:0.0.1-SNAPSHOT，在 Maven 编译插件中配置注解处理器路径。@CopyFrom、@CopyTo 和字段排除注解声明转换，编译生成 cn.edu.xmu.clonefactory.util.CloneFactory。调用 copy(target, source) 返回目标对象。请求 DTO 至 Command、Result 至 VO、持久化 PO 至 Data 均使用生成的转换代码。

[cols="22,55,23",options="header"]
|===
|可执行层 |日志内容 |级别
|API |请求方法、路由模板、结果、耗时。 |DEBUG；规则拒绝 WARN。
|结构化服务 |用例、操作者与目标编号、分支结果。 |DEBUG / WARN。
|DDD 应用服务 |用例编排、领域行为结果和事务失败。 |DEBUG / WARN。
|DDD 领域规则 |由调用方记录规则结果，领域保持框架无关。 |应用服务记录。
|Infrastructure |查询与写操作名称、耗时和异常类型。 |DEBUG；故障 ERROR。
|===

各执行层通过 SLF4J 使用 Logback 输出控制台与滚动文件。统一异常边界记录一次完整堆栈；日志排除输入凭据、完整手机号、Cookie、Session 标识和 CSRF 凭据。接口与普通数据类保持数据和契约职责。

=== 各功能详细交互

所有已定义的 HTTP 方法与路径均登记在流程配图覆盖清单。查询图通过 UseCase 表示选定实现，两套查询使用相同协作。每个业务写操作分别给出结构化与 DDD 图，授权关系的新增与撤销分别展开。用例后的调用表保留实际方法、URL 与功能的对应关系。
'''
 # Authentication is shared; each state change has a separate plate.
 auth_titles={'csrf':'取得 CSRF 凭据','login':'账号与登录资格校验','session':'登录成功建立会话','me':'查看当前用户','logout':'退出当前会话','authorize':'当前操作鉴权'}
 for slug in ['csrf','login','session','me','logout','authorize']:
  s+='\n==== '+auth_titles[slug]+'\n'
  desc={'login':'用户名和 password 严格匹配，password 的内容为手机号；systems 恰含一个标识。无目标系统 login 权限返回 403。',
  'session':'凭据与登录资格均通过后取得 Session，更换 Session 标识并保存 userId 与 systemKey。随后返回 LoginVO 与 Cookie。',
  'logout':'退出允许无身份重复调用，均返回 204；写请求仍需有效 CSRF。',
  'authorize':'查询实时连接 user_role、role_permission、permission 和 business_system。多个角色取并集，撤销部分关系后重新计算；未命中返回 allowed=false。'}.get(slug,'')
  s+=plate('flow-'+slug,auth_titles[slug]+' 顺序图',desc)
 for m in MODULES:
  s+='\n==== '+m.title+'管理流程\n'
  for slug,title,key,eps,method,rule in READS:
   if key==m.key:s+=plate('flow-'+slug,title+' 顺序图',rule+'。')
  for f in WRITES:
   if f['module']!=m.key:continue
   s+='\n===== '+f['title']+'\n\n'+f['rule']+'。\n'
   for mode in ['structured','ddd']:s+=plate('flow-'+f['slug']+'-'+mode,f['title']+' '+('结构化' if mode=='structured' else 'DDD')+'顺序图')
 return s

def usecase_mapping(cid):
 titles={slug:title for slug,title,_,_ in entries()};titles['session']='建立会话'
 rows=[]
 for slug in CASE_GROUPS[cid]:
  for stem in figures(slug):
   mode=' 结构化' if stem.endswith('-structured') else ' DDD' if stem.endswith('-ddd') else ''
   rows.append('<<'+stem+','+titles[slug]+mode+'>>')
 return '对应顺序图：'+ '、'.join(rows)+'。'
