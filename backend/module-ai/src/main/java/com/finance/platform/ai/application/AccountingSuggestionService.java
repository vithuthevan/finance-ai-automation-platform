package com.finance.platform.ai.application;

import com.finance.platform.finance.domain.model.Category;

import java.util.List;

public interface AccountingSuggestionService {

	AccountingSuggestion suggest(ExtractedDocument extracted, List<Category> validCategories, Category historical);
}
