package com.todo.mail;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ResendMailSender implements MailSender {

	private static final Logger log = LoggerFactory.getLogger(ResendMailSender.class);

	private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";

	private final RestClient restClient;

	private final MailProperties properties;

	@Autowired
	public ResendMailSender(MailProperties properties) {
		this(RestClient.builder(), properties);
	}

	ResendMailSender(RestClient.Builder restClientBuilder, MailProperties properties) {
		this.restClient = restClientBuilder.build();
		this.properties = properties;
	}

	@Override
	public void send(MailMessage message) {
		if (properties.resendApiKey() == null || properties.resendApiKey().isBlank()) {
			log.warn("app.mail.resend-api-key is not configured; email to {} was not sent: {}", message.to(),
					message.subject());
			return;
		}
		restClient.post()
			.uri(RESEND_EMAILS_URL)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.resendApiKey())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("from", properties.from(), "to", List.of(message.to()), "subject", message.subject(), "html",
					message.html()))
			.retrieve()
			.toBodilessEntity();
	}

}
