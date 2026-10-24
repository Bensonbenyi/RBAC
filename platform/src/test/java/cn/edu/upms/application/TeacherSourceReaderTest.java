package cn.edu.upms.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 只修改临时副本验证来源校验，教师原表始终只读. */
class TeacherSourceReaderTest {
  @TempDir Path temporary;
  static final String ORGANIZATION = "市政公司部门信息表.xlsx";
  static final String EMPLOYEES = "市政公司员工信息表.xlsx";
  static final String PERMISSIONS = "市政公司应用软件功能权限清单.xlsx";

  @Test
  @DisplayName("ACC-F-001 未知归属、必填缺失、两类重复身份都阻止导入")
  void invalidEmployees() throws Exception {
    for (int scenario = 0; scenario < 5; scenario++) {
      final int selected = scenario;
      Path copy = copy("employee-" + scenario);
      edit(
          copy,
          EMPLOYEES,
          sheet -> {
            var first = sheet.getRow(1);
            var second = sheet.getRow(2);
            switch (selected) {
              case 0 -> first.getCell(3).setCellValue("不存在的部门");
              case 1 -> first.getCell(0).setCellValue("");
              case 2 -> second.getCell(1).setCellValue(first.getCell(1).getStringCellValue());
              case 3 -> second.getCell(5).setCellValue(first.getCell(5).getStringCellValue());
              case 4 -> {
                first.getCell(2).setCellValue("总公司");
                first.getCell(3).setCellValue("总公司");
              }
              default -> throw new AssertionError();
            }
          });
      assertThatThrownBy(() -> TeacherSourceReader.read(copy))
          .isInstanceOf(IllegalStateException.class);
    }
    Path copy = copy("unknown-company");
    edit(
        copy,
        ORGANIZATION,
        sheet -> {
          var row = sheet.createRow(sheet.getLastRowNum() + 1);
          row.createCell(0).setCellValue("陌生部门");
          row.createCell(2).setCellValue("");
        });
    assertThatThrownBy(() -> TeacherSourceReader.read(copy)).hasMessageContaining("未知公司");
  }

  @Test
  @DisplayName("ACC-F-001 五项规模逐项检查，不能用部分导入冒充完整基线")
  void scaleChecks() throws Exception {
    for (int scenario = 0; scenario < 5; scenario++) {
      final int selected = scenario;
      Path copy = copy("scale-" + scenario);
      if (scenario < 2) {
        edit(
            copy,
            ORGANIZATION,
            sheet -> {
              var row = sheet.createRow(sheet.getLastRowNum() + 1);
              row.createCell(0).setCellValue(selected == 0 ? "新公司" : "总公司-新增部门");
              row.createCell(2).setCellValue(selected == 0 ? "新公司" : "总公司");
            });
      } else if (scenario < 4) {
        edit(
            copy,
            EMPLOYEES,
            sheet -> {
              if (selected == 2) {
                var row = sheet.createRow(sheet.getLastRowNum() + 1);
                for (int c = 0; c < 6; c++) {
                  row.createCell(c).setCellValue(sheet.getRow(1).getCell(c).getStringCellValue());
                }
                row.getCell(1).setCellValue("extra-account");
                row.getCell(5).setCellValue("13999999999");
              } else {
                for (var row : sheet) {
                  if (row.getRowNum() > 0
                      && row.getCell(2)
                          .getStringCellValue()
                          .equals(row.getCell(3).getStringCellValue())) {
                    row.getCell(2).setCellValue(sheet.getRow(1).getCell(2).getStringCellValue());
                    row.getCell(3).setCellValue(sheet.getRow(1).getCell(3).getStringCellValue());
                    break;
                  }
                }
              }
            });
      } else {
        edit(
            copy,
            PERMISSIONS,
            sheet -> {
              var row = sheet.createRow(sheet.getLastRowNum() + 1);
              row.createCell(0).setCellValue("额外系统");
            });
      }
      assertThatThrownBy(() -> TeacherSourceReader.read(copy)).hasMessageContaining("规模");
    }
  }

  @Test
  @DisplayName("空目录、空行与尾部后台都能解析，动作支持全部、view、无及空段")
  void directoryEdges() throws Exception {
    Path copy = copy("directory");
    edit(
        copy,
        PERMISSIONS,
        sheet -> {
          for (int r = sheet.getLastRowNum(); r >= 2; r--) {
            if (sheet.getRow(r) != null) {
              sheet.removeRow(sheet.getRow(r));
            }
          }
          sheet.createRow(2);
          var first = sheet.createRow(3);
          first.createCell(1).setCellValue("空系统标题行");
        });
    assertThatThrownBy(() -> TeacherSourceReader.read(copy)).hasMessageContaining("规模");
    edit(
        copy,
        PERMISSIONS,
        sheet -> {
          var row = sheet.createRow(4);
          row.createCell(0).setCellValue("系统管理后台");
          row.createCell(2).setCellValue("管理功能");
        });
    assertThatThrownBy(() -> TeacherSourceReader.read(copy)).hasMessageContaining("规模");
    Path complete = copy("actions");
    edit(
        complete,
        PERMISSIONS,
        sheet -> {
          for (var row : sheet) {
            if (row.getRowNum() > 1
                && row.getCell(2) != null
                && !row.getCell(2).toString().isBlank()
                && row.getCell(0) != null
                && !row.getCell(0).toString().equals("系统管理后台")) {
              row.getCell(4).setCellValue("全部/view//无");
              var next = sheet.getRow(row.getRowNum() + 1);
              if (next.getCell(0) == null) {
                next.createCell(0);
              }
              next.getCell(0).setCellValue(row.getCell(0).toString());
              break;
            }
          }
        });
    assertThat(TeacherSourceReader.read(complete).systems()).hasSize(17);
  }

  private Path copy(String name) throws Exception {
    Path result = Files.createDirectory(temporary.resolve(name));
    for (String file : List.of(ORGANIZATION, EMPLOYEES, PERMISSIONS)) {
      Files.write(result.resolve(file), Files.readAllBytes(Path.of("../docs").resolve(file)));
    }
    return result;
  }

  private static void edit(Path directory, String name, Consumer<Sheet> change) throws Exception {
    Path file = directory.resolve(name);
    try (var input = Files.newInputStream(file);
        var workbook = WorkbookFactory.create(input)) {
      change.accept(workbook.getSheetAt(0));
      try (var output = Files.newOutputStream(file)) {
        workbook.write(output);
      }
    }
  }
}
