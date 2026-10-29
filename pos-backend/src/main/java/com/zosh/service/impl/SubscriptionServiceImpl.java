package com.zosh.service.impl;

import com.zosh.domain.PaymentGateway;
import com.zosh.domain.SubscriptionStatus;
import com.zosh.modal.Store;
import com.zosh.modal.Subscription;
import com.zosh.modal.SubscriptionPlan;
import com.zosh.payload.dto.SubscriptionDTO;
import com.zosh.repository.StoreRepository;
import com.zosh.repository.SubscriptionPlanRepository;
import com.zosh.repository.SubscriptionRepository;
import com.zosh.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final StoreRepository storeRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

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
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionDTO subscribe(Long storeId, String planId, PaymentGateway gateway, String transactionId) throws Exception {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new Exception("Store not found: " + storeId));

        // Hủy các gói ACTIVE cũ của cửa hàng trước khi kích hoạt gói mới
        List<Subscription> activeSubs = subscriptionRepository.findByStoreIdAndStatus(storeId, SubscriptionStatus.ACTIVE);
        for (Subscription oldSub : activeSubs) {
            oldSub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(oldSub);
        }

        SubscriptionPlan matchedPlan = null;
        if (planId != null) {
            try {
                Long numericPlanId = Long.parseLong(planId.trim());
                Optional<SubscriptionPlan> planOpt = subscriptionPlanRepository.findById(numericPlanId);
                if (planOpt.isPresent()) {
                    matchedPlan = planOpt.get();
                }
            } catch (NumberFormatException ignored) {
            }
        }

        String resolvedName = matchedPlan != null ? matchedPlan.getName() : resolvePlanName(planId);
        Double amount = matchedPlan != null ? matchedPlan.getPrice() : 0.0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = (matchedPlan != null && "YEARLY".equalsIgnoreCase(matchedPlan.getBillingCycle()))
                ? now.plusYears(1)
                : now.plusMonths(1);

        Subscription subscription = Subscription.builder()
                .store(store)
                .planId(planId)
                .planName(resolvedName)
                .amount(amount)
                .status(SubscriptionStatus.ACTIVE)
                .gateway(gateway != null ? gateway : PaymentGateway.RAZORPAY)
                .transactionId(transactionId)
                .startDate(now)
                .endDate(endDate)
                .currency("VND")
                .build();

        return toDTO(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionDTO upgrade(Long storeId, String planId, PaymentGateway gateway, String transactionId) throws Exception {
        return subscribe(storeId, planId, gateway, transactionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionDTO activate(Long subscriptionId) throws Exception {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new Exception("Subscription not found: " + subscriptionId));
        sub.setStatus(SubscriptionStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        sub.setStartDate(now);

        boolean isYearly = false;
        if (sub.getPlanId() != null) {
            try {
                Long numericPlanId = Long.parseLong(sub.getPlanId().trim());
                isYearly = subscriptionPlanRepository.findById(numericPlanId)
                        .map(p -> "YEARLY".equalsIgnoreCase(p.getBillingCycle()))
                        .orElse(false);
            } catch (NumberFormatException ignored) {}
        }
        sub.setEndDate(isYearly ? now.plusYears(1) : now.plusMonths(1));
        return toDTO(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionDTO cancel(Long subscriptionId) throws Exception {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new Exception("Subscription not found: " + subscriptionId));
        sub.setStatus(SubscriptionStatus.CANCELLED);
        return toDTO(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubscriptionDTO updatePaymentStatus(Long subscriptionId, SubscriptionStatus status) throws Exception {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new Exception("Subscription not found: " + subscriptionId));
        sub.setStatus(status);
        return toDTO(subscriptionRepository.save(sub));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDTO> getStoreSubscriptions(Long storeId, SubscriptionStatus status) throws Exception {
        List<Subscription> subs = status != null
                ? subscriptionRepository.findByStoreIdAndStatus(storeId, status)
                : subscriptionRepository.findByStoreId(storeId);
        return subs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDTO> getAllSubscriptions(SubscriptionStatus status) throws Exception {
        List<Subscription> subs = status != null
                ? subscriptionRepository.findByStatus(status)
                : subscriptionRepository.findAll();
        return subs.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionDTO> getExpiringSubscriptions(int days) throws Exception {
        LocalDateTime cutoff = LocalDateTime.now().plusDays(days);
        return subscriptionRepository.findByEndDateBeforeAndStatus(cutoff, SubscriptionStatus.ACTIVE)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
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