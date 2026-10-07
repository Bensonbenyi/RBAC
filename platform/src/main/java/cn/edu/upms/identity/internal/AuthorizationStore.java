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

/** 保存框架授权状态及令牌索引；有界且单节点，失去状态只会拒绝请求. */
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
    return authorization != null && tokens(authorization).contains(token) ? authorization : null;
  }

  private static List<String> tokens(OAuth2Authorization authorization) {
    List<String> result = new ArrayList<>();
    var code = authorization.getToken(OAuth2AuthorizationCode.class);
    if (code != null) {
      result.add(code.getToken().getTokenValue());
    }
    var access = authorization.getAccessToken();
    if (access != null) {
      result.add(access.getToken().getTokenValue());
    }
    var id = authorization.getToken(OidcIdToken.class);
    if (id != null) {
      result.add(id.getToken().getTokenValue());
    }
    String state = authorization.getAttribute("state");
    if (state != null) {
      result.add(state);
    }
    return result;
  }
}
