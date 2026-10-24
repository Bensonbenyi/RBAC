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

/**
 * 只读解析组织、员工及功能权限三份来源 Excel，返回经过校验的导入事实.
 *
 * <p>本类不访问数据库，不任命管理员，也不根据岗位给员工授予业务角色。来源中存在合法的直属员工待确认部门，保留 null；其他未知归属、重复身份或规模不符会使导入在写库前停止。
 * LinkedHashMap 保持来源顺序，便于在相同资料上得到可重复的初始编号.
 */
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
    // 先读取组织三列和员工六列，格式化为文字，避免手机号被当作数值运算。
    List<List<String>> orgRows = rows(directory.resolve("市政公司部门信息表.xlsx"), 3);
    List<List<String>> employeeRows = rows(directory.resolve("市政公司员工信息表.xlsx"), 6);
    // 使用插入有序映射，按来源首次出现顺序生成可重复的公司编号。
    Map<String, Long> companyIds = new LinkedHashMap<>();
    orgRows.stream()
        // 跳过标题，从所属公司列收集非空名称。
        .skip(1)
        .map(r -> r.get(2))
        .filter(n -> !n.isBlank())
        // 相同公司名复用已有编号，首次出现才生成下一个编号。
        .forEach(n -> companyIds.computeIfAbsent(n, k -> (long) companyIds.size() + 1));
    // 把公司索引转换为只含导入事实的记录列表。
    List<CompanyRow> companies =
        companyIds.entrySet().stream().map(e -> new CompanyRow(e.getValue(), e.getKey())).toList();
    // 部门列表保存事实，名称索引供后续员工归属匹配使用。
    List<DepartmentRow> departments = new ArrayList<>();
    Map<String, Long> departmentIds = new LinkedHashMap<>();
    // 逐行处理部门，但公司自身那一行不应再生成同名部门。
    for (List<String> row : orgRows.subList(1, orgRows.size())) {
      if (companyIds.containsKey(row.getFirst())) {
        continue;
      }
      // 读取所属公司，再去掉部门显示名称中可选的公司前缀。
      String company = row.get(2);
      String name =
          row.getFirst().replaceFirst("^" + java.util.regex.Pattern.quote(company + "-"), "");
      // 部门编号按有效部门顺序生成，归属公司必须已经能从索引查到。
      long id = departments.size() + 1L;
      departments.add(new DepartmentRow(id, requireCompany(companyIds, company), name));
      // 索引同时使用公司和部门名，避免不同公司的同名部门冲突。
      departmentIds.put(company + "/" + name, id);
    }
    // 员工列表与两个唯一性集合分开，解析阶段就发现重复登录资料。
    List<EmployeeRow> employees = new ArrayList<>();
    Set<String> usernames = new LinkedHashSet<>();
    // 手机号也独立唯一，不能只以用户名是否重复作为校验依据。
    Set<String> phones = new LinkedHashSet<>();
    // 员工标题跳过后，每行分别解析公司、部门和身份资料。
    for (List<String> row : employeeRows.subList(1, employeeRows.size())) {
      String company = row.get(2);
      String department = row.get(3);
      // 按公司与部门组合查找归属，未知部门只允许下面明确确认的特例。
      Long departmentId = departmentIds.get(company + "/" + department);
      // 来源表把部分分公司直属员工的"部门"填为公司名；只承认这一已确认特例。
      // 其他未知部门直接报错，不能统一吞成 null，否则会隐藏错误归属。
      if (departmentId == null && !(department.equals(company) && !"总公司".equals(company))) {
        throw new IllegalStateException("未知部门归属：" + company + "/" + department);
      }
      // 必填资料全部非空，用户名和手机号都须第一次出现。
      if (row.stream().anyMatch(String::isBlank)
          || !usernames.add(row.get(1))
          || !phones.add(row.get(5))) {
        throw new IllegalStateException("员工必填资料缺失或用户名、手机号重复");
      }
      // 按固定列顺序构造员工事实；可空部门保留待确认状态，不自行猜测。
      employees.add(
          new EmployeeRow(
              employees.size() + 1L,
              // 用户名与姓名来自不同列，不能因展示习惯而互换。
              row.get(1),
              row.get(0),
              // 手机号作为文字保留，公司编号则通过已知公司索引解析。
              row.get(5),
              requireCompany(companyIds, company),
              // 部门可能为合法的 null，岗位只保留原文，不自动授予权限。
              departmentId,
              row.get(4)));
    }
    // 权限清单单独解析，再统一核对各类基础数据规模。
    List<SystemRow> systems = systems(directory);
    // 数量和 54 名待确认员工均须符合已确认基线，否则整次导入停止。
    if (companies.size() != 10
        || departments.size() != 101
        || employees.size() != 10000
        || employees.stream().filter(e -> e.departmentId() == null).count() != 54
        || systems.size() != 17) {
      throw new IllegalStateException("源资料规模与需求基线不符，停止导入");
    }
    // 返回不可变列表给后续写库阶段，文件解析本身不修改数据库。
    return new Source(companies, List.copyOf(departments), List.copyOf(employees), systems);
  }

  private static List<SystemRow> systems(Path directory) throws IOException {
    // 权限清单读取九列；前两行是标题，后续每行描述一个功能及五类模板选择。
    List<List<String>> rows = rows(directory.resolve("市政公司应用软件功能权限清单.xlsx"), 9);
    List<SystemRow> result = new ArrayList<>();
    // 来源有合并单元格，因此保存最近一次非空系统名和模块名。
    String currentSystem = "";
    String module = "";
    // 每个系统独立收集目录及五类模板选择，不能跨系统复用集合。
    List<DirectoryItem> items = new ArrayList<>();
    Map<String, Set<String>> templates = templates();
    // 功能序号在每个系统内部递增，用于首次导入生成稳定格式标识。
    int featureIndex = 0;
    // 跳过标题后逐行扫描，遇到新系统时先结算上一个系统。
    for (List<String> row : rows.subList(2, rows.size())) {
      if (!row.getFirst().isBlank() && !row.getFirst().equals(currentSystem)) {
        // 平台自己的管理后台不属于外部系统，其余已读完系统加入结果。
        if (!currentSystem.isBlank() && !currentSystem.equals("系统管理后台")) {
          result.add(systemRow(result.size() + 1L, currentSystem, items, templates));
        }
        // 切换系统时清空模块上下文及目录，避免携带上一个系统的资料。
        currentSystem = row.getFirst();
        module = "";
        items = new ArrayList<>();
        // 模板和功能序号也重新开始，每个系统有自己的初始角色集合。
        templates = templates();
        featureIndex = 0;
      }
      // 整段跳过本平台权限，不能把平台管理员身份当成外部系统模板。
      if ("系统管理后台".equals(currentSystem)) {
        continue;
      }
      // 模块列非空时更新上下文，空值表示沿用合并单元格上方的模块名。
      if (!row.get(1).isBlank()) {
        module = row.get(1);
      }
      // 没有功能名称的行不生成权限，避免创建空功能目录。
      if (row.get(2).isBlank()) {
        continue;
      }
      // 每个实际功能仅推进一次序号，并收集该行的模板动作集合。
      featureIndex++;
      List<Set<String>> selections = new ArrayList<>();
      // 具体动作去重但保留来源顺序，稍后为每个动作生成独立目录项。
      Set<String> concrete = new LinkedHashSet<>();
      // 第四到第八下标对应五类模板，各自的选择集合按固定顺序保存。
      for (int i = 4; i < 9; i++) {
        Set<String> actions = actions(row.get(i));
        // 同时保留原选择和所有具体动作的并集，全部将在并集上展开。
        selections.add(actions);
        actions.stream().filter(a -> !a.equals("全部")).forEach(concrete::add);
      }
      // "全部"是模板选择规则，不生成一个绕过具体动作校验的万能权限。
      // 它只展开为本功能在来源中明确出现的具体动作；"无"则不形成任何选择。
      for (String action : concrete) {
        // 这是首次课程导入按固定来源顺序生成的标识；运行期目录须保持 key 稳定。
        // 来源重排不应被误当作可随意覆盖已有目录的在线更新方案。
        String key = String.format("f%03d:%s", featureIndex, action);
        // 一条功能动作对应一项有效目录；模板选择只保存该项 key。
        items.add(new DirectoryItem(key, module, row.get(2), action, true));
        // 按模板的插入顺序对应前面五列，避免把某类岗位权限交给另一类。
        int index = 0;
        for (Set<String> grants : templates.values()) {
          // 取当前模板在本功能上的动作选择，再决定是否加入这个具体动作。
          Set<String> selection = selections.get(index++);
          if (selection.contains("全部") || selection.contains(action)) {
            // 模板显式选中了动作或选了全部时，加入该动作的稳定标识。
            grants.add(key);
          }
        }
      }
    }
    // 循环结束还需结算最后一个系统，因为后面没有新系统触发切换。
    if (!currentSystem.isBlank() && !currentSystem.equals("系统管理后台")) {
      result.add(systemRow(result.size() + 1L, currentSystem, items, templates));
    }
    // 冻结结果列表，后续导入过程不会继续改变解析顺序。
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
