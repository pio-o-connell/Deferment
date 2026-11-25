package com.defermentsystem.exceptions;

public class InvalidDeferralStateException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public InvalidDeferralStateException(String message) {
		super(message);
	}
}
