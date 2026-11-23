package cn.edu.xmu.rbac.service.auth;

import cn.edu.xmu.rbac.service.port.BusinessException;
import cn.edu.xmu.rbac.service.port.Data;
import cn.edu.xmu.rbac.service.port.ManagementStore;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.springframework.stereotype.Service;

/** 统一身份组织树和教师选择所用的只读目录. */
@Service
public class IdentityDirectoryService {
  private final ManagementStore store;
  private final AccessPolicy access;

  /**
   * 注入目录查询与 RBAC 权限校验.
   *
   * @param store 管理数据读写端口
   * @param access 检查目标许可和部门归属的组件
   */
  public IdentityDirectoryService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  /**
   * 查询根部门或指定上级的直接子部门.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param parentId 父部门编号，根层使用 null
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param pageSize 每页最多返回的记录数
   * @return 包含当前页记录及总数的分页结果
   */
  public Data.Page<Data.DirectoryDepartment> departments(
      Data.Identity actor, Long parentId, String keyword, int page, int pageSize) {
    require(actor, "department.manage");
    return store.directoryDepartments(parentId, keyword.strip(), page - 1, pageSize);
  }

  /**
   * 查询指定部门的直接成员.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param departmentId 所属部门编号
   * @param keyword 目录搜索词，空串表示查询当前层全部记录
   * @param page 从 0 开始的页号
   * @param pageSize 每页最多返回的记录数
   * @return 包含当前页记录及总数的分页结果
   */
  public Data.Page<Data.DirectoryUser> users(
      Data.Identity actor, Long departmentId, String keyword, int page, int pageSize) {
    require(actor, "user.view");
    try {
      store.department(departmentId);
    } catch (BusinessException e) {
      throw new BusinessException(404, "IDENTITY_DEPARTMENT_NOT_FOUND", "统一身份认证系统中没有找到该部门");
    }
    scopePlatform(actor, departmentId);
    return store.directoryUsers(departmentId, keyword.strip(), page - 1, pageSize);
  }

  /**
   * 按外部身份标识查找用户，当前标识取自账号主键.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param userId 用户的唯一编号
   * @return 目标用户的数据
   * @throws BusinessException 缺少许可、用户标识不存在或目标超出部门范围
   */
  public Data.User lookup(Data.Identity actor, String userId) {
    require(actor, "user.view");
    Data.User user;
    try {
      long id = Long.parseLong(userId);
      if (!Long.toString(id).equals(userId)) {
        throw BusinessException.missing();
      }
      user = store.user(id);
    } catch (IllegalArgumentException | BusinessException e) {
      throw new BusinessException(404, "IDENTITY_USER_NOT_FOUND", "统一身份认证系统中没有找到该用户");
    }
    scopePlatform(actor, user.departmentId());
    return user;
  }

  /**
   * 使用目标系统的目录权限检查访问.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param platformCode 权限后台访问该目录所需的权限码
   */
  private void require(Data.Identity actor, String platformCode) {
    String code = "course-project-management".equals(actor.systemKey()) ? "TEC" : platformCode;
    if (!access.allowed(actor.userId(), actor.systemKey(), code)) {
      throw BusinessException.forbidden();
    }
  }

  /**
   * 对平台访问检查操作者和目标的部门归属.
   *
   * @param actor 当前已认证账号及其系统、部门和会话
   * @param departmentId 所属部门编号
   */
  private void scopePlatform(Data.Identity actor, Long departmentId) {
    if ("rbac".equals(actor.systemKey())) {
      access.scope(actor, departmentId);
    }
  }
}
