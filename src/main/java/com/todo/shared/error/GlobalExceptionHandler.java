package com.todo.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request body validation failed");
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException exception) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request parameter validation failed");
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getParameterValidationResults().forEach((result) -> {
			String parameter = result.getMethodParameter().getParameterName();
			result.getResolvableErrors()
				.forEach((error) -> errors.putIfAbsent(parameter, error.getDefaultMessage()));
		});
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Constraint violation");
		Map<String, String> errors = new LinkedHashMap<>();
		exception.getConstraintViolations().forEach((violation) -> errors
			.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return problem(HttpStatus.FORBIDDEN, "Access denied", "You are not allowed to perform this action");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception exception) {
		log.error("Unhandled exception", exception);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "Unexpected error");
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}

}
