package com.finance.platform.finance.application.port;

import com.finance.platform.auth.domain.model.User;

import java.util.UUID;

public interface CurrentUserEntityPort {

	User requireById(UUID userId);
}
