package com.zosh.service.impl;

import com.zosh.domain.SubscriptionStatus;
import com.zosh.modal.SubscriptionPlan;
import com.zosh.payload.dto.SubscriptionPlanDTO;
import com.zosh.repository.SubscriptionPlanRepository;
import com.zosh.repository.SubscriptionRepository;
import com.zosh.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;

    private static final java.util.Set<String> BILLING_CYCLES = java.util.Set.of("MONTHLY", "YEARLY");

    /** Kiểm tra dữ liệu gói ở phía server (không tin vào validate của form frontend). */
    private void validate(SubscriptionPlanDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Plan name is required");
        }
        if (dto.getPrice() == null || dto.getPrice() < 0) {
            throw new IllegalArgumentException("Plan price must be 0 or greater");
        }
        if (dto.getBillingCycle() == null || !BILLING_CYCLES.contains(dto.getBillingCycle().toUpperCase())) {
            throw new IllegalArgumentException("Billing cycle must be MONTHLY or YEARLY");
        }
        if (dto.getMaxBranches() == null || dto.getMaxBranches() < 1
                || dto.getMaxUsers() == null || dto.getMaxUsers() < 1
                || dto.getMaxProducts() == null || dto.getMaxProducts() < 1) {
            throw new IllegalArgumentException("Branches, users and products limits must be at least 1");
        }
    }

    private static boolean flag(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    @Override
    public SubscriptionPlanDTO createPlan(SubscriptionPlanDTO dto) {
        validate(dto);
        SubscriptionPlan plan = toEntity(dto);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        return toDTO(saved);
    }

    @Override
    public SubscriptionPlanDTO updatePlan(Long id, SubscriptionPlanDTO dto) throws Exception {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new Exception("Subscription plan not found with id: " + id));
        validate(dto);

        plan.setName(dto.getName());
        plan.setPrice(dto.getPrice());
        plan.setBillingCycle(dto.getBillingCycle().toUpperCase());
        plan.setMaxBranches(dto.getMaxBranches());
        plan.setMaxUsers(dto.getMaxUsers());
        plan.setMaxProducts(dto.getMaxProducts());
        if (dto.getActive() != null) {
            plan.setActive(dto.getActive());
        }
        plan.setAdvancedReports(flag(dto.getAdvancedReports()));
        plan.setInventory(flag(dto.getInventory()));
        plan.setIntegrations(flag(dto.getIntegrations()));
        plan.setEcommerce(flag(dto.getEcommerce()));
        plan.setInvoiceBranding(flag(dto.getInvoiceBranding()));
        plan.setPrioritySupport(flag(dto.getPrioritySupport()));
        plan.setMultiLocation(flag(dto.getMultiLocation()));
        plan.setDescription(dto.getDescription());
        // Tính năng của gói là cố định (cờ boolean), không còn danh sách tính năng tự do
        plan.setExtraFeatures(new java.util.ArrayList<>());

        SubscriptionPlan updated = subscriptionPlanRepository.save(plan);
        return toDTO(updated);
    }

    @Override
    public List<SubscriptionPlanDTO> getAllPlans() {
        return subscriptionPlanRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SubscriptionPlanDTO getPlanById(Long id) throws Exception {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new Exception("Subscription plan not found with id: " + id));
        return toDTO(plan);
    }

    @Override
    public void deletePlan(Long id) throws Exception {
        if (!subscriptionPlanRepository.existsById(id)) {
            throw new Exception("Subscription plan not found with id: " + id);
        }
        boolean inUse = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE).stream()
                .anyMatch(s -> s.getPlanId() != null && s.getPlanId().trim().equals(String.valueOf(id)));
        if (inUse) {
            throw new IllegalArgumentException("This plan is used by stores with an active subscription and cannot be deleted. Deactivate it instead.");
        }
        subscriptionPlanRepository.deleteById(id);
    }

    // ─── Mapper methods ──────────────────────────────────────────────

    private SubscriptionPlanDTO toDTO(SubscriptionPlan plan) {
        return SubscriptionPlanDTO.builder()
                .id(plan.getId())
                .name(plan.getName())
                .price(plan.getPrice())
                .billingCycle(plan.getBillingCycle())
                .maxBranches(plan.getMaxBranches())
                .maxUsers(plan.getMaxUsers())
                .maxProducts(plan.getMaxProducts())
                .active(plan.getActive())
                .advancedReports(plan.getAdvancedReports())
                .inventory(plan.getInventory())
                .integrations(plan.getIntegrations())
                .ecommerce(plan.getEcommerce())
                .invoiceBranding(plan.getInvoiceBranding())
                .prioritySupport(plan.getPrioritySupport())
                .multiLocation(plan.getMultiLocation())
                .description(plan.getDescription())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .extraFeatures(plan.getExtraFeatures())
                .build();
    }

    private SubscriptionPlan toEntity(SubscriptionPlanDTO dto) {
        return SubscriptionPlan.builder()
                .name(dto.getName())
                .price(dto.getPrice())
                                .billingCycle(dto.getBillingCycle().toUpperCase())
                .maxBranches(dto.getMaxBranches())
                .maxUsers(dto.getMaxUsers())
                .maxProducts(dto.getMaxProducts())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .advancedReports(dto.getAdvancedReports())
                .inventory(dto.getInventory())
                .integrations(dto.getIntegrations())
                .ecommerce(dto.getEcommerce())
                .invoiceBranding(dto.getInvoiceBranding())
                .prioritySupport(dto.getPrioritySupport())
                .multiLocation(dto.getMultiLocation())
                .description(dto.getDescription())
                .extraFeatures(new java.util.ArrayList<>())
                .build();
    }
}
