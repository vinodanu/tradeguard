package com.tradeguard.trade;

import java.util.Map;

@FunctionalInterface
public interface FreshnessVerifier {
  Map<String, Object> check(String evaluationId, String token);
}
