package com.tradeguard.trade;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.*;

@SpringBootApplication(scanBasePackages = "com.tradeguard")
@EnableScheduling
@org.springframework.boot.persistence.autoconfigure.EntityScan(
    basePackages = {"com.tradeguard.sql", "com.tradeguard.trade"})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
