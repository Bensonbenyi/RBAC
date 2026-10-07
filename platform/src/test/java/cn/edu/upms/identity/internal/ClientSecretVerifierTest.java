package cn.edu.upms.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 成功校验复用不能跨越凭据更新，也不能接受错误明文. */
class ClientSecretVerifierTest {
  @Test
  @DisplayName("ACC-F-018 凭据哈希更新后旧成功缓存不能放行，错误凭据始终拒绝")
  void rotationInvalidatesByContent() {
    var verifier = new ClientSecretVerifier();
    String old = verifier.encode("old-secret");
    String current = verifier.encode("new-secret");
    assertThat(verifier.matches("old-secret", old)).isTrue();
    assertThat(verifier.matches("old-secret", old)).isTrue();
    assertThat(verifier.matches("old-secret", current)).isFalse();
    assertThat(verifier.matches("wrong", current)).isFalse();
    assertThat(verifier.matches("new-secret", current)).isTrue();
  }
}
