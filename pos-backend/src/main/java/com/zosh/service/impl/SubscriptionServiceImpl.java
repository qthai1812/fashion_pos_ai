package com.zosh.service.impl;

import com.zosh.domain.PaymentGateway;
import com.zosh.domain.SubscriptionStatus;
import com.zosh.modal.Store;
import com.zosh.modal.Subscription;
import com.zosh.payload.dto.SubscriptionDTO;
import com.zosh.repository.StoreRepository;
import com.zosh.repository.SubscriptionRepository;
import com.zosh.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final StoreRepository storeRepository;

    private SubscriptionDTO toDTO(Subscription s) {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.setId(s.getId());
        if (s.getStore() != null) {
            dto.setStoreId(s.getStore().getId());
            dto.setStoreName(s.getStore().getBrand());
        }
        dto.setPlanName(s.getPlanName());
        dto.setPlanId(s.getPlanId());
        dto.setStatus(s.getStatus());
        dto.setGateway(s.getGateway());
        dto.setTransactionId(s.getTransactionId());
        dto.setCheckoutUrl(s.getCheckoutUrl());
        dto.setAmount(s.getAmount());
        dto.setCurrency(s.getCurrency());
        dto.setStartDate(s.getStartDate());
        dto.setEndDate(s.getEndDate());
        dto.setCreatedAt(s.getCreatedAt());
        return dto;
    }

    @Override
    @Transactional
    public SubscriptionDTO subscribe(Long storeId, String planId, PaymentGateway gateway, String transactionId) throws Exception {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new Exception("Store not found: " + storeId));

        Subscription subscription = Subscription.builder()
                .store(store)
                .planId(planId)
                .planName(resolvePlanName(planId))
                .status(SubscriptionStatus.PENDING)
                .gateway(gateway != null ? gateway : PaymentGateway.RAZORPAY)
                .transactionId(transactionId)
                .startDate(LocalDateTime.now())
                .endDate(LocalDateTime.now().plusMonths(1))
                .currency("INR")
                .build();
        return toDTO(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional
    public SubscriptionDTO upgrade(Long storeId, String planId, PaymentGateway gateway, String transactionId) throws Exception {
        return subscribe(storeId, planId, gateway, transactionId);
    }

    @Override
    @Transactional
    public SubscriptionDTO activate(Long subscriptionId) throws Exception {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new Exception("Subscription not found: " + subscriptionId));
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusMonths(1));
        return toDTO(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionDTO cancel(Long subscriptionId) throws Exception {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new Exception("Subscription not found: " + subscriptionId));
        sub.setStatus(SubscriptionStatus.CANCELLED);
        return toDTO(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional
    public SubscriptionDTO updatePaymentStatus(Long subscriptionId, SubscriptionStatus status) throws Exception {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new Exception("Subscription not found: " + subscriptionId));
        sub.setStatus(status);
        return toDTO(subscriptionRepository.save(sub));
    }

    @Override
    public List<SubscriptionDTO> getStoreSubscriptions(Long storeId, SubscriptionStatus status) throws Exception {
        List<Subscription> subs = status != null
                ? subscriptionRepository.findByStoreIdAndStatus(storeId, status)
                : subscriptionRepository.findByStoreId(storeId);
        return subs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<SubscriptionDTO> getAllSubscriptions(SubscriptionStatus status) throws Exception {
        List<Subscription> subs = status != null
                ? subscriptionRepository.findByStatus(status)
                : subscriptionRepository.findAll();
        return subs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<SubscriptionDTO> getExpiringSubscriptions(int days) throws Exception {
        LocalDateTime cutoff = LocalDateTime.now().plusDays(days);
        return subscriptionRepository.findByEndDateBeforeAndStatus(cutoff, SubscriptionStatus.ACTIVE)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public long countByStatus(SubscriptionStatus status) throws Exception {
        return subscriptionRepository.countByStatus(status);
    }

    private String resolvePlanName(String planId) {
        if (planId == null) return "Basic";
        switch (planId.toLowerCase()) {
            case "basic": return "Basic Plan";
            case "pro": return "Pro Plan";
            case "enterprise": return "Enterprise Plan";
            case "trial": return "Trial Plan";
            default: return planId;
        }
    }
}
