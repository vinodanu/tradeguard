package com.tradeguard;

import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Base64;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

public final class Keys {
  public static RSAPublicKey publicKey() {
    try {
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA")
              .generatePublic(
                  new X509EncodedKeySpec(
                      pem(System.getenv().getOrDefault("JWT_PUBLIC_KEY", "secrets/public.pem"))));
    } catch (Exception e) {
      throw new IllegalStateException("JWT public key missing; run scripts/bootstrap.py", e);
    }
  }

  public static RSAPrivateKey privateKey() {
    try {
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA")
              .generatePrivate(
                  new PKCS8EncodedKeySpec(
                      pem(System.getenv().getOrDefault("JWT_PRIVATE_KEY", "secrets/private.pem"))));
    } catch (Exception e) {
      throw new IllegalStateException("JWT private key missing", e);
    }
  }

  private static byte[] pem(String path) throws Exception {
    return Base64.getDecoder()
        .decode(
            Files.readString(Path.of(path))
                .replaceAll("-----[^-]+-----", "")
                .replaceAll("\\s", ""));
  }

  public static org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> validator() {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer("tradeguard"),
        j ->
            j.getAudience().contains("tradeguard-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience", null)));
  }
}
