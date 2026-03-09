package it.glknet.financial_manager.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name("transactions-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionsDeleteTopic() {
        return TopicBuilder.name("transactions-delete-topic")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
