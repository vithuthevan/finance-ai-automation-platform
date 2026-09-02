package com.finance.platform.finance.infrastructure.persistence;

import com.finance.platform.finance.domain.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanJpaRepository extends JpaRepository<SubscriptionPlan, UUID> {

	Optional<SubscriptionPlan> findByCodeAndActiveTrue(String code);

	Optional<SubscriptionPlan> findByCode(String code);
}
