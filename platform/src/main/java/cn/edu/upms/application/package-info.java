/**
 * HTTP 适配、启动初始化及跨模块应用协调，不直接拥有员工、角色或审计数据.
 *
 * <p>后台请求先经过 SecurityFilterChain 处理身份、CSRF 和并发入口限制，再进入 Controller。 Controller 将请求转换成公开能力调用，Service
 * 在事务中操作本模块 Repository；异常按 FailureAdvice 转成已知 HTTP 错误。过滤器自身的拒绝可能在进入 MVC 之前已经返回。
 *
 * <p>本包的少量协调事务用于组合多个模块的不变量，例如先检查角色无分配再删除。协调代码可以调用多个 api，但不能越过它们直接读写 internal 表实体。员工生命周期单独放在
 * EmployeeLifecycle，避免所有流程都堆进一个 HTTP 方法。
 *
 * <p>启动顺序由 Order 明确：教师基线、演示账号、演示本人动作、专用压测配置。seed_marker 与对应数据同事务提交，决定重复启动是否跳过；数据库结构则由 Flyway 迁移维护。
 */
package cn.edu.upms.application;
