package com.zosh.repository;

import com.zosh.domain.SubscriptionStatus;
import com.zosh.modal.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByStoreId(Long storeId);
    List<Subscription> findByStoreIdAndStatus(Long storeId, SubscriptionStatus status);
    List<Subscription> findByStatus(SubscriptionStatus status);
    List<Subscription> findByEndDateBeforeAndStatus(LocalDateTime date, SubscriptionStatus status);
    long countByStatus(SubscriptionStatus status);
}
