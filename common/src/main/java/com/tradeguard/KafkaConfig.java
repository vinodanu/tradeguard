package com.tradeguard;

import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {
  @Bean
  public DefaultErrorHandler kafkaErrors(KafkaTemplate<String, String> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, error) ->
                new org.apache.kafka.common.TopicPartition(
                    record.topic() + ".DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3L));
    handler.setAckAfterHandle(true);
    return handler;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumer, DefaultErrorHandler errors) {
    var f = new ConcurrentKafkaListenerContainerFactory<String, String>();
    f.setConsumerFactory(consumer);
    f.setCommonErrorHandler(errors);
    f.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return f;
  }
}
