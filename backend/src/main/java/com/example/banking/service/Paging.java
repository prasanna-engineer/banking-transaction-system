package com.example.banking.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.banking.exception.ApiException;

/** Shared page/size rules so no endpoint can return an unbounded list. */
final class Paging {

	static final int MAX_PAGE_SIZE = 100;

	private Paging() {
	}

	/** Rejects negative pages, caps the size at 100, and always adds id as a tie-breaker for stable pages. */
	static PageRequest of(int page, int size, Sort.Direction direction, String field) {
		if (page < 0) {
			throw ApiException.badRequest("INVALID_PAGE", "'page' must be 0 or greater.");
		}
		return PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE),
				Sort.by(direction, field).and(Sort.by(direction, "id")));
	}

}
