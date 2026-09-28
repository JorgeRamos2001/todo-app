package com.todo.invitations.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.invitations")
public record InvitationProperties(Duration ttl, String acceptUrl, String rejectUrl) {

}
