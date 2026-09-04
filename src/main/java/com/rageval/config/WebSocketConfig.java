package com.rageval.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final EvaluationProgressHandler evaluationProgressHandler;

    public WebSocketConfig(EvaluationProgressHandler evaluationProgressHandler) {
        this.evaluationProgressHandler = evaluationProgressHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(evaluationProgressHandler, "/ws/evaluation-progress")
                .setAllowedOrigins("*");
    }
}
