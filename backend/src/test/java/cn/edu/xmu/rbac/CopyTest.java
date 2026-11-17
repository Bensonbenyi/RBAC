package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.Requests;
import cn.edu.xmu.rbac.service.port.Data;
import org.junit.jupiter.api.Test;

/** 验证教师处理器生成的 record 拷贝和 PATCH 空字段保留. */
class CopyTest {
  @Test
  void copiesRecordAndKeepsUnsubmittedFields() {
    var command =
        CloneFactory.copy(new Data.Profile(null, null), new Requests.Profile("新姓名", null));
    var old = new Data.Profile("原姓名", "13800000000");
    var merged = CloneFactory.copyNotNull(old, command);
    assertEquals(new Data.Profile("新姓名", "13800000000"), merged);
    assertEquals(new Data.Profile("原姓名", "13800000000"), old);
    assertEquals(old, CloneFactory.copyNotNull(old, new Data.Profile(null, null)));
  }
}
