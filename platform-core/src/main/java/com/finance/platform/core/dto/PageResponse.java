package com.finance.platform.core.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
}
