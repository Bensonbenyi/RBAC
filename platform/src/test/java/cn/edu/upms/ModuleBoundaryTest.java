package cn.edu.upms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 从实际字节码验证公开能力边界，禁止 Repository、实体和缓存的跨模块访问. */
class ModuleBoundaryTest {
  private static final Set<String> modules =
      Set.of("identity", "organization", "integration", "catalog", "authorization", "audit");

  @Test
  @DisplayName("设计约束 六模块仅经 api 协作，且不向上依赖应用协调层")
  void onlyPublicCapabilitiesAcrossModules() {
    var imported =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("cn.edu.upms");
    classes()
        .that()
        .resideInAPackage("cn.edu.upms..")
        .should(
            new ArchCondition<JavaClass>("跨模块只依赖 api，并保持应用层在上方") {
              @Override
              public void check(JavaClass source, ConditionEvents events) {
                String from = module(source.getPackageName());
                if (!modules.contains(from)) {
                  return;
                }
                for (var dependency : source.getDirectDependenciesFromSelf()) {
                  JavaClass target = dependency.getTargetClass();
                  String to = module(target.getPackageName());
                  boolean internalAccess =
                      modules.contains(to)
                          && !from.equals(to)
                          && !target.getPackageName().startsWith("cn.edu.upms." + to + ".api");
                  boolean upward = target.getPackageName().startsWith("cn.edu.upms.application");
                  events.add(
                      new SimpleConditionEvent(
                          dependency, !internalAccess && !upward, dependency.getDescription()));
                }
              }
            })
        .check(imported);
  }

  @Test
  @DisplayName("设计约束 业务模块无循环，控制器不直接存取，纯权限规则不依赖框架")
  void layersAndCycles() {
    var imported =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("cn.edu.upms");
    com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
        .matching("cn.edu.upms.(*)..")
        .should()
        .beFreeOfCycles()
        .check(imported);
    classes()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .onlyDependOnClassesThat()
        .resideOutsideOfPackages("..internal..", "..repository..")
        .check(imported);
    classes()
        .that()
        .haveSimpleNameStartingWith("EffectivePermissionRule")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage("java..", "cn.edu.upms.authorization.internal")
        .check(imported);
  }

  private static String module(String packageName) {
    String prefix = "cn.edu.upms.";
    if (!packageName.startsWith(prefix)) {
      return "";
    }
    return packageName.substring(prefix.length()).split("\\.")[0];
  }
}
