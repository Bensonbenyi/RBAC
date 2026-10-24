package cn.edu.upms.identity.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

/**
 * 适配框架 OAuth2AuthorizationService，保存授权对象及其查找索引.
 *
 * <p>authorizations 按授权编号保存完整状态，tokenIndex 按授权码、访问令牌、ID Token 或 state 找到所属授权编号。这里没有数据库表；Caffeine
 * 有容量和到期限制，重启或淘汰后请求只能重新认证.
 *
 * <p>线程安全缓存不等于一次兑换流程原子完成，读取、检查、消耗授权码的整段竞争由 AtomicCodeExchange 另外串行化。本存储也不负责判断员工业务权限.
 */
@Component
class AuthorizationStore implements OAuth2AuthorizationService {
  private final Cache<String, OAuth2Authorization> authorizations =
      Caffeine.newBuilder().maximumSize(50000).expireAfterWrite(Duration.ofHours(8)).build();
  private final Cache<String, String> tokenIndex =
      Caffeine.newBuilder().maximumSize(200000).expireAfterWrite(Duration.ofHours(8)).build();

  @Override
  public void save(OAuth2Authorization authorization) {
    authorizations.put(authorization.getId(), authorization);
    tokens(authorization).forEach(t -> tokenIndex.put(t, authorization.getId()));
  }

  @Override
  public void remove(OAuth2Authorization authorization) {
    authorizations.invalidate(authorization.getId());
    tokens(authorization).forEach(tokenIndex::invalidate);
  }

  @Override
  public OAuth2Authorization findById(String id) {
    return authorizations.getIfPresent(id);
  }

  @Override
  public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
    if (token == null) {
      return null;
    }
    String id = tokenIndex.getIfPresent(token);
    if (id == null) {
      return null;
    }
    OAuth2Authorization authorization = authorizations.getIfPresent(id);
    // 索引命中后再次确认真实内容；类型和有效性由框架及身份用例继续校验。
    // 更新授权状态后，旧索引可暂留到淘汰；必须回到最新授权内容确认，不能只信索引存在。
    // tokenType 由调用层进一步校验，本实现没有把参数当成已经完成的类型验证。
    return authorization != null && tokens(authorization).contains(token) ? authorization : null;
  }

  private static List<String> tokens(OAuth2Authorization authorization) {
    // 收集本授权对象可用作反查键的原值，不在这里判断这些令牌是否有效。
    List<String> result = new ArrayList<>();
    // 授权码可能尚未生成；存在时加入索引，用于后端兑换定位授权。
    var code = authorization.getToken(OAuth2AuthorizationCode.class);
    if (code != null) {
      // 保存码的原值，消费状态仍由框架授权对象和原子兑换保护维护。
      result.add(code.getToken().getTokenValue());
    }
    // 访问令牌产生后也指向同一授权对象，供实时验证查找。
    var access = authorization.getAccessToken();
    if (access != null) {
      // 只有实际存在访问令牌才加入，避免授权流程中间态出现空值。
      result.add(access.getToken().getTokenValue());
    }
    // OIDC 身份令牌可用于定位退出相关授权，不能当作业务访问令牌。
    var id = authorization.getToken(OidcIdToken.class);
    if (id != null) {
      // 保存 ID Token 原值供查找，调用方还须校验其协议用途。
      result.add(id.getToken().getTokenValue());
    }
    // 授权状态也属于查找键，保存它不等于已完成回调 state 校验。
    String state = authorization.getAttribute("state");
    if (state != null) {
      // 仅加入实际存在的 state，保证结果列表无此类空条目。
      result.add(state);
    }
    // 调用者用同一列表添加或移除索引，保持不同令牌种类的处理一致。
    return result;
  }
}
