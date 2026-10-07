package cn.edu.upms.application;

import cn.edu.upms.catalog.api.CatalogApi.DirectoryItem;
import cn.edu.upms.organization.api.OrganizationImportApi.CompanyRow;
import cn.edu.upms.organization.api.OrganizationImportApi.DepartmentRow;
import cn.edu.upms.organization.api.OrganizationImportApi.EmployeeRow;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/** 只读解析三份教师资料，校验后交给各模块导入；不使用不可靠的范围标记. */
public final class TeacherSourceReader {
  /** 完整基础资料；模板权限以目录稳定标识关联. */
  public record Source(
      List<CompanyRow> companies,
      List<DepartmentRow> departments,
      List<EmployeeRow> employees,
      List<SystemRow> systems) {}

  /** 一个外部系统的功能动作及五个全局角色模板. */
  public record SystemRow(
      long id,
      String key,
      String name,
      List<DirectoryItem> permissions,
      Map<String, Set<String>> templates) {}

  private TeacherSourceReader() {}

  /** 读取并核对约定规模；任一未知归属或重复身份使基础导入失败. */
  public static Source read(Path directory) throws IOException {
    List<List<String>> orgRows = rows(directory.resolve("市政公司部门信息表.xlsx"), 3);
    List<List<String>> employeeRows = rows(directory.resolve("市政公司员工信息表.xlsx"), 6);
    Map<String, Long> companyIds = new LinkedHashMap<>();
    orgRows.stream()
        .skip(1)
        .map(r -> r.get(2))
        .filter(n -> !n.isBlank())
        .forEach(n -> companyIds.computeIfAbsent(n, k -> (long) companyIds.size() + 1));
    List<CompanyRow> companies =
        companyIds.entrySet().stream().map(e -> new CompanyRow(e.getValue(), e.getKey())).toList();
    List<DepartmentRow> departments = new ArrayList<>();
    Map<String, Long> departmentIds = new LinkedHashMap<>();
    for (List<String> row : orgRows.subList(1, orgRows.size())) {
      if (companyIds.containsKey(row.getFirst())) {
        continue;
      }
      String company = row.get(2);
      String name =
          row.getFirst().replaceFirst("^" + java.util.regex.Pattern.quote(company + "-"), "");
      long id = departments.size() + 1L;
      departments.add(new DepartmentRow(id, requireCompany(companyIds, company), name));
      departmentIds.put(company + "/" + name, id);
    }
    List<EmployeeRow> employees = new ArrayList<>();
    Set<String> usernames = new LinkedHashSet<>();
    Set<String> phones = new LinkedHashSet<>();
    for (List<String> row : employeeRows.subList(1, employeeRows.size())) {
      String company = row.get(2);
      String department = row.get(3);
      Long departmentId = departmentIds.get(company + "/" + department);
      if (departmentId == null && !(department.equals(company) && !"总公司".equals(company))) {
        throw new IllegalStateException("未知部门归属：" + company + "/" + department);
      }
      if (row.stream().anyMatch(String::isBlank)
          || !usernames.add(row.get(1))
          || !phones.add(row.get(5))) {
        throw new IllegalStateException("员工必填资料缺失或用户名、手机号重复");
      }
      employees.add(
          new EmployeeRow(
              employees.size() + 1L,
              row.get(1),
              row.get(0),
              row.get(5),
              requireCompany(companyIds, company),
              departmentId,
              row.get(4)));
    }
    List<SystemRow> systems = systems(directory);
    if (companies.size() != 10
        || departments.size() != 101
        || employees.size() != 10000
        || employees.stream().filter(e -> e.departmentId() == null).count() != 54
        || systems.size() != 17) {
      throw new IllegalStateException("源资料规模与需求基线不符，停止导入");
    }
    return new Source(companies, List.copyOf(departments), List.copyOf(employees), systems);
  }

  private static List<SystemRow> systems(Path directory) throws IOException {
    List<List<String>> rows = rows(directory.resolve("市政公司应用软件功能权限清单.xlsx"), 9);
    List<SystemRow> result = new ArrayList<>();
    String currentSystem = "";
    String module = "";
    List<DirectoryItem> items = new ArrayList<>();
    Map<String, Set<String>> templates = templates();
    int featureIndex = 0;
    for (List<String> row : rows.subList(2, rows.size())) {
      if (!row.getFirst().isBlank() && !row.getFirst().equals(currentSystem)) {
        if (!currentSystem.isBlank() && !currentSystem.equals("系统管理后台")) {
          result.add(systemRow(result.size() + 1L, currentSystem, items, templates));
        }
        currentSystem = row.getFirst();
        module = "";
        items = new ArrayList<>();
        templates = templates();
        featureIndex = 0;
      }
      if ("系统管理后台".equals(currentSystem)) {
        continue;
      }
      if (!row.get(1).isBlank()) {
        module = row.get(1);
      }
      if (row.get(2).isBlank()) {
        continue;
      }
      featureIndex++;
      List<Set<String>> selections = new ArrayList<>();
      Set<String> concrete = new LinkedHashSet<>();
      for (int i = 4; i < 9; i++) {
        Set<String> actions = actions(row.get(i));
        selections.add(actions);
        actions.stream().filter(a -> !a.equals("全部")).forEach(concrete::add);
      }
      for (String action : concrete) {
        String key = String.format("f%03d:%s", featureIndex, action);
        items.add(new DirectoryItem(key, module, row.get(2), action, true));
        int index = 0;
        for (Set<String> grants : templates.values()) {
          Set<String> selection = selections.get(index++);
          if (selection.contains("全部") || selection.contains(action)) {
            grants.add(key);
          }
        }
      }
    }
    if (!currentSystem.isBlank() && !currentSystem.equals("系统管理后台")) {
      result.add(systemRow(result.size() + 1L, currentSystem, items, templates));
    }
    return List.copyOf(result);
  }

  private static SystemRow systemRow(
      long id, String name, List<DirectoryItem> items, Map<String, Set<String>> templates) {
    String key =
        name.equals("OA协同办公系统") ? "oa" : name.equals("企业知识库管理系统") ? "knowledge" : "external-" + id;
    Map<String, Set<String>> frozen = new LinkedHashMap<>();
    templates.forEach((role, grants) -> frozen.put(role, Set.copyOf(grants)));
    return new SystemRow(
        id, key, name, List.copyOf(items), java.util.Collections.unmodifiableMap(frozen));
  }

  private static Map<String, Set<String>> templates() {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (String name : List.of("系统管理员", "公司领导", "部门经理", "项目经理", "普通员工")) {
      result.put(name, new LinkedHashSet<>());
    }
    return result;
  }

  private static Set<String> actions(String raw) {
    Set<String> result = new LinkedHashSet<>();
    for (String action : raw.replace("view", "查看").split("/")) {
      if (!action.isBlank() && !action.equals("无")) {
        result.add(action.trim());
      }
    }
    return result;
  }

  private static long requireCompany(Map<String, Long> ids, String name) {
    Long id = ids.get(name);
    if (id == null) {
      throw new IllegalStateException("未知公司归属：" + name);
    }
    return id;
  }

  private static List<List<String>> rows(Path file, int width) throws IOException {
    List<List<String>> result = new ArrayList<>();
    DataFormatter formatter = new DataFormatter(java.util.Locale.ROOT);
    try (var workbook = WorkbookFactory.create(file.toFile(), null, true)) {
      // 逐个遍历实际行，并按已知列数取值，不依赖 worksheet dimension。
      for (Row row : workbook.getSheetAt(0)) {
        List<String> values = new ArrayList<>();
        for (int column = 0; column < width; column++) {
          values.add(formatter.formatCellValue(row.getCell(column)).trim());
        }
        if (values.stream().anyMatch(v -> !v.isBlank())) {
          result.add(List.copyOf(values));
        }
      }
    }
    return result;
  }
}
