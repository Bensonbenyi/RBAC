package cn.edu.xmu.rbac.service.structured;

import cn.edu.xmu.rbac.service.port.*;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "structured")
public class StructuredSystemService implements SystemUseCase {
  private static final Logger log = LoggerFactory.getLogger(StructuredSystemService.class);
  private final ManagementStore store;
  private final AccessPolicy access;

  public StructuredSystemService(ManagementStore store, AccessPolicy access) {
    this.store = store;
    this.access = access;
  }

  public Data.Page<Data.System> list(Data.Identity actor, int page, int size) {
    access.require(actor, "system.manage");
    return store.systems(page, size);
  }

  public Data.System create(Data.Identity actor, Data.CreateSystem command) {
    access.require(actor, "system.manage");
    log.debug("createSystem actor={}", actor.userId());
    return store.createSystem(command);
  }

  public void update(Data.Identity actor, Long id, Data.Name command) {
    access.require(actor, "system.manage");
    store.system(id);
    store.renameSystem(id, command.name());
    log.debug("renameSystem actor={} target={}", actor.userId(), id);
  }

  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "system.manage");
    store.system(id);
    store.deleteSystem(id);
    log.debug("deleteSystem actor={} target={}", actor.userId(), id);
  }
}
