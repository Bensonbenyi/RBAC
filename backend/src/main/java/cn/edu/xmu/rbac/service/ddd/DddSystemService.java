package cn.edu.xmu.rbac.service.ddd;

import cn.edu.xmu.rbac.service.port.*;
import cn.edu.xmu.rbac.service.security.AccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@ConditionalOnProperty(name = "app.backend-strategy", havingValue = "ddd")
public class DddSystemService implements SystemUseCase {
  private static final Logger log = LoggerFactory.getLogger(DddSystemService.class);
  private final ManagementStore store;
  private final AccessPolicy access;

  public DddSystemService(ManagementStore store, AccessPolicy access) {
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
    var entity = new cn.edu.xmu.rbac.service.ddd.domain.System(store.system(id));
    entity.rename(command.name());
    store.renameSystem(id, entity.name());
    log.debug("renameSystem actor={} target={}", actor.userId(), id);
  }

  public void delete(Data.Identity actor, Long id) {
    access.require(actor, "system.manage");
    store.system(id);
    store.deleteSystem(id);
    log.debug("deleteSystem actor={} target={}", actor.userId(), id);
  }
}
