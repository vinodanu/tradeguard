package com.tradeguard;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.testcontainers.kafka.KafkaContainer;

class KafkaDeliveryIT {
  @Test
  void uncommittedMessageIsRedeliveredAfterConsumerRestart() throws Exception {
    try (var kafka = new KafkaContainer("apache/kafka:4.0.0")) {
      kafka.start();
      Properties producer = new Properties();
      producer.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
      producer.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      producer.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
      producer.put(ProducerConfig.ACKS_CONFIG, "all");
      try (var p = new KafkaProducer<String, String>(producer)) {
        p.send(new ProducerRecord<>("test-events", "order", "same-event-id")).get();
      }
      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "recovery-test");
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      for (int attempt = 0; attempt < 2; attempt++) {
        try (var c = new KafkaConsumer<String, String>(props)) {
          c.subscribe(List.of("test-events"));
          boolean found = false;
          long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
          while (!found && System.nanoTime() < deadline) {
            for (var record : c.poll(Duration.ofSeconds(1)))
              if (record.value().equals("same-event-id")) found = true;
          }
          assertTrue(found, "The uncommitted event should be delivered on both starts");
        }
      }
    }
  }
}
