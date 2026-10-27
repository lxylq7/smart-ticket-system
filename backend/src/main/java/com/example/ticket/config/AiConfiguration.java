package com.example.ticket.config;

import com.example.ticket.ai.AiProperties;
import com.example.ticket.ai.DisabledTicketClassifier;
import com.example.ticket.ai.SpringAiTicketClassifier;
import com.example.ticket.ai.TicketClassifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

    @Bean(name = "ticketAiExecutor")
    public TaskExecutor ticketAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ticket-ai-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
    public TicketClassifier springAiTicketClassifier(ChatClient.Builder builder) {
        return new SpringAiTicketClassifier(builder.build());
    }

    @Bean
    @ConditionalOnMissingBean(TicketClassifier.class)
    public TicketClassifier disabledTicketClassifier() {
        return new DisabledTicketClassifier();
    }
}
