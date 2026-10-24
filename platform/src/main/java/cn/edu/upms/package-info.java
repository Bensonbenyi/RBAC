/**
 * 统一权限平台的后端源码入口，按业务能力组织模块，并在模块内部使用技术分层.
 *
 * <p>建议先沿三个场景阅读，再展开各模块内部：
 *
 * <ol>
 *   <li>后台换角：{@link cn.edu.upms.application.AdminController} 取得当前操作者，调用 {@link
 *       cn.edu.upms.authorization.api.AuthorizationApi}；实现校验范围、取得锁、比较版本，保存分配并写审计，最后把事实副本作为 JSON 返回。
 *   <li>实时鉴权：{@link cn.edu.upms.application.RuntimeController} 先调用 {@link
 *       cn.edu.upms.identity.api.IdentityApi} 验证访问令牌，再请求授权模块判断当前动作。
 *   <li>员工停用：{@link cn.edu.upms.application.EmployeeLifecycle} 协调组织状态、全部撤权与审计，
 *       数据库提交后才让身份模块撤销该员工的全部统一会话。
 * </ol>
 *
 * <p>包名含义：application 适配 HTTP、启动任务或协调跨模块用例；各业务模块的 api 包定义公开契约和事实 record，internal 包隐藏实现、实体与
 * Repository；shared 只放通用基础类型。 Java 的 public 可见性不自动等于业务允许依赖，架构边界测试还会约束跨模块访问。
 *
 * <p>常用术语：DTO 表达请求或响应形状；Service 执行用例与规则；Entity 映射持久化状态； Repository 负责本模块查询和保存。Spring
 * 在启动时创建这些协作对象，依赖注入把它们连起来；单例仅表示容器通常复用一个实例，不保证方法会被串行调用。
 *
 * <p>对照学习时，需求规格回答"应做什么"，分析与设计指南解释"为何这样分工"，接口与数据库指南解释"操作和数据如何落地"；代码注释着重说明当前执行次序、失败语义和并发约束。
 */
package cn.edu.upms;
