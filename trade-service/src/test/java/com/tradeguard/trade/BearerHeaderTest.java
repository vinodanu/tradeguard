package com.tradeguard.trade;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

class BearerHeaderTest {
  @Test
  void emptyBearerHeaderIsRejectedBeforeCredentialAuthentication() {
    var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    request.addHeader("Authorization", "Bearer");
    assertThrows(OAuth2AuthenticationException.class,
        () -> new DefaultBearerTokenResolver().resolve(request));
  }

  @Test
  void loginWithoutAuthorizationHeaderHasNoBearerTokenToReject() {
    var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    assertNull(new DefaultBearerTokenResolver().resolve(request));
  }
}
