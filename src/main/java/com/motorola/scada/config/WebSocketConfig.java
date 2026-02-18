package com.motorola.scada.config;

import com.motorola.scada.websocket.TelemetryWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private TelemetryWebSocketHandler telemetryHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(telemetryHandler, "/ws/telemetry")
                .setAllowedOrigins("*");
    }
}
