package cn.edu.xmu.rbac.infrastructure.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 记录各层方法、耗时及失败结果，参数与凭据不入日志. */
@Aspect
@Component
public class LayerLogging {
  private static final Logger log = LoggerFactory.getLogger(LayerLogging.class);

  /** 记录调用耗时与执行结果，异常保持原样抛出. */
  @Around(
      "execution(public * cn.edu.xmu.rbac.api..*Controller.*(..)) || execution(public *"
          + " cn.edu.xmu.rbac.infrastructure.persistence..*(..)) || execution(public *"
          + " cn.edu.xmu.rbac.service.auth..*(..)) || execution(public *"
          + " cn.edu.xmu.rbac.service.catalog..*(..)) || execution(public *"
          + " cn.edu.xmu.rbac.service.structured..*(..)) || execution(public *"
          + " cn.edu.xmu.rbac.service.ddd.Ddd*Service.*(..))")
  public Object measure(ProceedingJoinPoint call) throws Throwable {
    long start = System.nanoTime();
    String outcome = "ok";
    try {
      return call.proceed();
    } catch (Throwable error) {
      outcome = error.getClass().getSimpleName();
      throw error;
    } finally {
      log.debug(
          "layer operation={} outcome={} elapsedMicros={}",
          call.getSignature().toShortString(),
          outcome,
          (System.nanoTime() - start) / 1000);
    }
  }
}
