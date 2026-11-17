package cn.edu.xmu.rbac.service.ddd.domain;

import cn.edu.xmu.rbac.service.port.Data;

public class Department {
  private String name;

  public Department(Data.Department data) {
    name = data.name();
  }

  public void rename(String value) {
    name = value;
  }

  public String name() {
    return name;
  }
}
