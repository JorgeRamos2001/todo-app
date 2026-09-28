package com.todo.realtime.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.todo.realtime.security.StompConnectAuthInterceptor;
import com.todo.realtime.security.StompSubscribeAuthInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class RealtimeConfig implements WebSocketMessageBrokerConfigurer {

	private final StompConnectAuthInterceptor connectAuthInterceptor;

	private final StompSubscribeAuthInterceptor subscribeAuthInterceptor;

	public RealtimeConfig(StompConnectAuthInterceptor connectAuthInterceptor,
			StompSubscribeAuthInterceptor subscribeAuthInterceptor) {
		this.connectAuthInterceptor = connectAuthInterceptor;
		this.subscribeAuthInterceptor = subscribeAuthInterceptor;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(connectAuthInterceptor, subscribeAuthInterceptor);
	}

}
