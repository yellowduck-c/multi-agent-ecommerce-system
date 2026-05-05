package com.ecommerce.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LLMConfig {

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    // 正确的做法：直接声明依赖，Spring 会自动注入 Builder
    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
        // 使用自动注入的 Builder 构建 ChatClient
        // 这里我们简单地设置一下 model，或者你也可以在这里配置通用的 system prompt
        return chatClientBuilder
                .defaultSystem("你是一个电商助手") // 可选：设置默认系统提示词
                .build();
    }
}