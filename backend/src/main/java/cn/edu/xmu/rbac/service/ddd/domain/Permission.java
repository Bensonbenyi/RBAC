package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.Data;

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
