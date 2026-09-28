package com.todo.invitations.service;

import org.springframework.stereotype.Component;

import com.todo.invitations.config.InvitationProperties;
import com.todo.invitations.domain.Invitation;
import com.todo.mail.MailMessage;

@Component
public class InvitationEmailComposer {

	public MailMessage compose(Invitation invitation, InvitationProperties properties) {
		String boardTitle = invitation.getBoard().getTitle();
		String acceptLink = properties.acceptUrl() + "?token=" + invitation.getToken();
		String rejectLink = properties.rejectUrl() + "?token=" + invitation.getToken();
		String html = """
				<html>
				<body style="font-family: sans-serif; color: #222;">
				  <h2>%s invited you to "%s"</h2>
				  <p>You have been invited to join as <strong>%s</strong>.</p>
				  <p>
				    <a href="%s">Accept invitation</a>
				    &nbsp;|&nbsp;
				    <a href="%s">Reject</a>
				  </p>
				  <p style="color: #666;">This invitation expires on %s.</p>
				</body>
				</html>
				"""
			.formatted(escape(invitation.getInviter().getName()), escape(boardTitle), invitation.getRole(), acceptLink,
					rejectLink, invitation.getExpiresAt());
		return new MailMessage(invitation.getInviteeEmail(), "Invitation to " + boardTitle, html);
	}

	private String escape(String value) {
		return value.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

}
