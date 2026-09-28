package com.todo.mail;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendMailSenderTests {

	@Test
	void sendsEmailThroughResendApi() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ResendMailSender sender = new ResendMailSender(builder, new MailProperties("from@example.com", "test-key"));

		server.expect(requestTo("https://api.resend.com/emails"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer test-key"))
			.andExpect(content().json("""
					{
					  "from": "from@example.com",
					  "to": ["invitee@example.com"],
					  "subject": "Hello",
					  "html": "<p>Hi</p>"
					}
					"""))
			.andRespond(withSuccess());

		sender.send(new MailMessage("invitee@example.com", "Hello", "<p>Hi</p>"));

		server.verify();
	}

	@Test
	void skipsSendWhenApiKeyIsMissing() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		ResendMailSender sender = new ResendMailSender(builder, new MailProperties("from@example.com", ""));

		assertThatCode(() -> sender.send(new MailMessage("invitee@example.com", "Hello", "<p>Hi</p>")))
			.doesNotThrowAnyException();

		server.verify();
	}

}
