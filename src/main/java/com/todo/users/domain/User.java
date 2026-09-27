package com.todo.users.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(length = 255)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserProvider provider;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	private User(String name, String email, String password, UserProvider provider) {
		this.name = name;
		this.email = email;
		this.password = password;
		this.provider = provider;
	}

	public static User local(String name, String email, String passwordHash) {
		return new User(name, email, passwordHash, UserProvider.LOCAL);
	}

	public static User google(String name, String email) {
		return new User(name, email, null, UserProvider.GOOGLE);
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getEmail() {
		return email;
	}

	public String getPassword() {
		return password;
	}

	public UserProvider getProvider() {
		return provider;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
