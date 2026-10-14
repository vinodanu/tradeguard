package com.tradeguard.live;

import com.tradeguard.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class Security {
  @Bean
  ReactiveJwtDecoder decoder() {
    var d = NimbusReactiveJwtDecoder.withPublicKey(Keys.publicKey()).build();
    d.setJwtValidator(Keys.validator());
    return d;
  }

  @Bean
  SecurityWebFilterChain chain(ServerHttpSecurity h) {
    return h.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            a -> a.pathMatchers("/actuator/health").permitAll().anyExchange().authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> {}))
        .build();
  }
}
