package cn.edu.xmu.rbac;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import cn.edu.xmu.rbac.api.shared.dto.ManagementDTOs;
import cn.edu.xmu.rbac.service.port.Data;
import org.junit.jupiter.api.Test;

/** 验证教师处理器生成的请求 DTO 到命令对象的拷贝. */
class CopyTest {
  @Test
  void copiesImmutableRecord() {
    var request = new ManagementDTOs.CreatePermissionDTO("CR", "课程审核");
    var target = new Data.CreatePermission(null, null);
    assertEquals(new Data.CreatePermission("CR", "课程审核"), CloneFactory.copy(target, request));
    assertEquals(new Data.CreatePermission(null, null), target);
  }
}
