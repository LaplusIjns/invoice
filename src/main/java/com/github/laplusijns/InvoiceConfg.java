package com.github.laplusijns;

import jakarta.servlet.http.HttpSessionListener;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.openai.api.ResponseFormat.Type;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods
public class InvoiceConfg {

    private final String baseUrl;
    private final String apiKey;
    private final String optionsModel;
    private final InvpoceSessionListener invpoceSessionListener;

    public InvoiceConfg(
            @Value("${spring.ai.openai.base-url}") final String baseUrl,
            @Value("${spring.ai.openai.api-key:}") final String apiKey,
            @Value("${spring.ai.openai.chat.options.model}") final String optionsModel,
            final InvpoceSessionListener invpoceSessionListener) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.optionsModel = optionsModel;
        this.invpoceSessionListener = invpoceSessionListener;
    }

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

    @Bean
    ServletListenerRegistrationBean<HttpSessionListener> sessionListener() {
        final ServletListenerRegistrationBean<HttpSessionListener> listenerRegBean =
                new ServletListenerRegistrationBean<>();
        listenerRegBean.setListener(invpoceSessionListener);
        return listenerRegBean;
    }
}
