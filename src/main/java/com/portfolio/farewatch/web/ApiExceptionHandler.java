package com.portfolio.farewatch.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, Object> notFound(NoSuchElementException e) {
		return body("not_found", e.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> badRequest(IllegalArgumentException e) {
		return body("bad_request", e.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> invalid(MethodArgumentNotValidException e) {
		List<String> fields = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + ": " + f.getDefaultMessage())
				.toList();
		Map<String, Object> body = body("validation_failed", "request validation failed");
		body.put("fields", fields);
		return body;
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, Object> conflict(DataIntegrityViolationException e) {
		return body("conflict", "duplicate watch or constraint violation");
	}

	private Map<String, Object> body(String error, String message) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("error", error);
		m.put("message", message);
		return m;
	}
}
