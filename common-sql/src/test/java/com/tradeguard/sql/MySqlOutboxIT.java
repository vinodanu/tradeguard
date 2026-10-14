package com.tradeguard.sql;

import static org.junit.jupiter.api.Assertions.*;

import com.mysql.cj.jdbc.MysqlDataSource;
import com.tradeguard.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.MySQLContainer;

class MySqlOutboxIT {
  @Test
  void businessInboxAndOutboxCommitTogetherAndRollbackTogether() {
    try (var mysql = new MySQLContainer<>("mysql:8.4.7")) {
      mysql.start();
      var datasource = new MysqlDataSource();
      datasource.setURL(mysql.getJdbcUrl());
      datasource.setUser(mysql.getUsername());
      datasource.setPassword(mysql.getPassword());
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(datasource);
      factory.setPackagesToScan("com.tradeguard.sql");
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
      factory.afterPropertiesSet();
      try (var em = factory.getObject().createEntityManager()) {
        var store = new SqlStore(null);
        store.em = em;
        Event e =
            Event.create("OrderSubmitted", "order", 0, "order", null, Map.of("quantity", "1"));
        em.getTransaction().begin();
        store.inbox("input");
        store.emit(e);
        em.getTransaction().rollback();
        em.clear();
        assertFalse(store.seen("input"));
        assertEquals(0, store.count(SqlOutbox.class));
        em.getTransaction().begin();
        store.inbox("input");
        store.emit(e);
        em.getTransaction().commit();
        em.clear();
        assertTrue(store.seen("input"));
        assertEquals(1, store.count(SqlOutbox.class));
      } finally {
        factory.destroy();
      }
    }
  }
}
