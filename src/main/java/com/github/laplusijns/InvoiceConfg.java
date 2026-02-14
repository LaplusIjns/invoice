package com.github.laplusijns;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.openai.api.ResponseFormat.Type;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods
public class InvoiceConfg {

    @Value("${spring.ai.openai.base-url}")
    String baseUrl;

    @Value("${spring.ai.openai.api-key:}")
    String apiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    String optionsModel;

    @Bean
    ChatModel chatModel() {
        final var openAiApi =
                OpenAiApi.builder().apiKey(apiKey).baseUrl(baseUrl).build();
        final var openAiChatOptions = OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder().type(Type.JSON_OBJECT).build())
                .model(optionsModel)
                .temperature(0.0)
                .build();
        return OpenAiChatModel.builder()
                .defaultOptions(openAiChatOptions)
                .openAiApi(openAiApi)
                .build();
    }

    @Bean
    ChatClient chatClient(final ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
