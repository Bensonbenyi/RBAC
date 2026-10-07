package cn.edu.upms.catalog.api;

import java.util.Map;
import java.util.Set;

/** 初始五类角色模板导入；模板不会直接授予任何员工. */
public interface CatalogImportApi {
  /** 在已接收目录上建立初始全局模板，以稳定权限标识关联. */
  void templates(long systemId, Map<String, Set<String>> grants);
}
