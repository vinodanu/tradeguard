package com.tradeguard.live;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;

@Component
public class ProjectionGate {
  public final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
}
