package com.finance.platform.core.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {

	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	private PageRequests() {
	}

	public static PageRequest of(int page, int size, Sort sort) {
		int safePage = Math.max(page, 0);
		int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		return PageRequest.of(safePage, safeSize, sort);
	}
}
