package com.finance.platform.auth.infrastructure.security;

import com.finance.platform.auth.domain.model.Role;
import com.finance.platform.auth.domain.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import java.time.Instant;

@Getter
public class SecurityUser implements UserDetails {

	private final UUID id;
	private final UUID firmId;
	private final String email;
	private final String passwordHash;
	private final Role.RoleCode role;
	private final boolean active;
	private final Instant deletedAt;

	public SecurityUser(User user) {
		this.id = user.getId();
		this.firmId = user.getFirmId();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.role = user.getRole().getCode();
		this.active = user.isActive();
		this.deletedAt = user.getDeletedAt();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return active && deletedAt == null;
	}
}
