package com.todo.users.service;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.todo.users.dto.UserResponse;
import com.todo.users.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> searchByEmail(String email) {
		String normalized = email.trim().toLowerCase(Locale.ROOT);
		return userRepository.findByEmail(normalized)
			.map((user) -> List.of(new UserResponse(user.getId(), user.getName(), user.getEmail())))
			.orElseGet(List::of);
	}

}
