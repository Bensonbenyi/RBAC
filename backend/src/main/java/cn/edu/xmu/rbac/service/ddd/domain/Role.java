package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.Data;

/** 角色名称由领域对象维护，角色与权限的直接关系由应用服务写入关联表. */
public class Role {
  private String name;

  public Role(Data.Role data) {
    name = data.name();
  }

  public void rename(String value) {
    name = value;
  }

  public String name() {
    return name;
  }
}
