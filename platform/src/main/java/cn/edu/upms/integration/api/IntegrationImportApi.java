package cn.edu.upms.integration.api;

import java.util.List;

/** 启动用系统登记导入，不提供在线批量绕过管理范围的入口. */
public interface IntegrationImportApi {
  /** 源资料中的系统登记身份. */
  record SystemRow(long id, String key, String name) {}

  /** 在外层基础导入事务内写入空系统数据集. */
  void load(List<SystemRow> systems);
}
