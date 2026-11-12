"""Detailed architecture and diagram plates embedded in the seven-chapter spec."""
from flow_catalog import MODULES, WRITES, READS, CASE_GROUPS, figures, entries

def plate(stem,title,description=''):
 return f'\n[.diagram.plate]\n--\n[[{'uml-'+stem if stem[0].isdigit() else stem}]]\n.{title}\nimage::spec/{stem}.svg[{title},width=100%]\n'+(f'\n{description}\n' if description else '')+'--\n'

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
 s+='''[.keep-table]
[cols="23,35,42",options="header"]
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
 s+='''写操作的服务公开方法是事务边界。图中的加载包含本次需要的对象、归属、现有关系或引用计数；按具体用例读取所需字段。新建时 data 的候选资料来自 Command，读取父节点、归属及唯一性信息；修改时领域对象从当前 Data 构造，changedData 取其更新后的状态。事务脚本 validateChange 逐条校验规则，DDD 构造与行为维护对象不变量，应用服务负责操作权限、跨对象存在性和范围校验。

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

'''
 return s


def usecase_mapping(cid):
 titles={slug:title for slug,title,_,_ in entries()};titles['session']='建立会话'
 slug=CASE_GROUPS[cid][0];stem=figures(slug)[0]
 return '顺序图：<<'+stem+',查看对应交互>>。'


def interactions(cid):
 """Place each interaction directly beneath its owning functional description."""
 writes={f['slug']:f for f in WRITES}
 reads={slug:(title,rule) for slug,title,_,_,_,rule in READS}
 auth={'csrf':('取得 CSRF 凭据','允许匿名取得；写请求携带 Cookie 与指定请求头。'),
 'login':('校验账号与登录资格','password 内容为手机号；systems 恰有一个系统标识。账号或凭据错误返回 401，无目标系统 login 权限返回 403。'),
 'session':('建立登录会话','校验通过后更换 Session 标识，保存 userId 与 systemKey，返回 Cookie。'),
 'me':('查看当前用户','返回本人编号、用户名、姓名及部门；已删除账号返回 401。'),
 'logout':('退出当前会话','校验 CSRF 后销毁已有 Session 并清理 Cookie；重复退出返回 204。'),
 'authorize':('查询当前操作权限','实时连接用户角色与角色权限，多个角色取并集；未命中返回 allowed=false。')}
 s=''
 for slug in CASE_GROUPS[cid]:
  if slug in writes:
   f=writes[slug];s+='\n===== '+f['title']+'\n\n'+f['rule']+'。\n'
   for mode in ['structured','ddd']:s+=plate('flow-'+slug+'-'+mode,f['title']+' '+('结构化' if mode=='structured' else 'DDD')+'顺序图')
  else:
   title,rule=reads.get(slug,auth.get(slug,('', '')))
   s+='\n===== '+title+'\n\n'+rule.rstrip('。')+'。\n'
   s+=plate('flow-'+slug,title+' 顺序图')
 return s
