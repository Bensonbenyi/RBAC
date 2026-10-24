package cn.edu.upms.integration.internal;

import cn.edu.upms.integration.api.IntegrationImportApi;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 为两个本机演示客户端登记精确回调；其余系统只加载目录数据. */
@Service
public class IntegrationImportService implements IntegrationImportApi {
  private final JdbcTemplate jdbc;
  private final String oaUrl;
  private final String knowledgeUrl;
  private final String clientSuffix;
  private final String directorySuffix;

  /** 注入本模块的数据访问工具. */
  public IntegrationImportService(
      JdbcTemplate jdbc,
      @Value("${upms.demo-oa-url:http://localhost:18081}") String oaUrl,
      @Value("${upms.demo-knowledge-url:http://localhost:18082}") String knowledgeUrl,
      @Value("${upms.bootstrap-client-suffix:demo-secret}") String clientSuffix,
      @Value("${upms.bootstrap-directory-suffix:directory-secret}") String directorySuffix) {
    this.jdbc = jdbc;
    this.oaUrl = oaUrl;
    this.knowledgeUrl = knowledgeUrl;
    this.clientSuffix = clientSuffix;
    this.directorySuffix = directorySuffix;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void load(List<SystemRow> systems) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM business_system", Long.class) != 0) {
      throw new IllegalStateException("系统基础导入要求空数据集");
    }
    var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    for (SystemRow row : systems) {
      String base =
          row.key().equals("oa") ? oaUrl : row.key().equals("knowledge") ? knowledgeUrl : null;
      jdbc.update(
          """
          INSERT INTO business_system(id,system_key,name,client_secret,directory_secret,
            redirect_uri,logout_redirect_uri) VALUES (?,?,?,?,?,?,?)
          """,
          row.id(),
          row.key(),
          row.name(),
          encoder.encode(row.key() + "-" + clientSuffix),
          encoder.encode(row.key() + "-" + directorySuffix),
          base == null ? null : base + "/login/oauth2/code/upms",
          base);
    }
  }
}
