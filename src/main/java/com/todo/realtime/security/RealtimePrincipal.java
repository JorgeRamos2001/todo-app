package com.todo.realtime.security;

import java.security.Principal;

public record RealtimePrincipal(Long id, String email, String name) implements Principal {

	@Override
	public String getName() {
		return email;
	}

}
