package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.Data;

/** 权限领域对象，维护名称. */
public class Permission {
  private String name;

  public Permission(Data.Permission data) {
    name = data.name();
  }

  public void rename(String value) {
    name = value;
  }

  public String name() {
    return name;
  }
}
