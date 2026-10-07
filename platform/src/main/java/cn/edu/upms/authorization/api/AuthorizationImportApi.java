package cn.edu.upms.authorization.api;

import java.util.List;

/** 专用空压测库的初始化能力，不用于运行期写入，也不公开为 HTTP 接口. */
public interface AuthorizationImportApi {
  /** 已由初始化协调器核对归属的测试分配事实. */
  record Row(long employeeId, long systemId, long roleId) {}

  /** 空分配表一次性装载；非空时拒绝，防止覆盖演示或运行数据. */
  void load(List<Row> rows);
}
