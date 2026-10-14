package com.example.banking.security;

/**
 * TEMPORARY (Milestone 3 only): identifies the caller by a plain request header.
 * This is NOT secure - any client can claim any user id. Milestone 4 replaces it with the
 * user taken from a verified JWT; services and ownership checks stay unchanged.
 */
public final class DemoUserHeader {

	public static final String NAME = "X-Demo-User-Id";

	private DemoUserHeader() {
	}

}
