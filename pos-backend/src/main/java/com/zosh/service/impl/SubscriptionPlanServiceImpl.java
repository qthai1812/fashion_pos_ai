package com.zosh.service.impl;

import com.zosh.modal.SubscriptionPlan;
import com.zosh.payload.dto.SubscriptionPlanDTO;
import com.zosh.repository.SubscriptionPlanRepository;
import com.zosh.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    public SubscriptionPlanDTO createPlan(SubscriptionPlanDTO dto) {
        SubscriptionPlan plan = toEntity(dto);
        SubscriptionPlan saved = subscriptionPlanRepository.save(plan);
        return toDTO(saved);
    }

    @Override
    public SubscriptionPlanDTO updatePlan(Long id, SubscriptionPlanDTO dto) throws Exception {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new Exception("Subscription plan not found with id: " + id));

        plan.setName(dto.getName());
        plan.setPrice(dto.getPrice());
        plan.setBillingCycle(dto.getBillingCycle());
        plan.setMaxBranches(dto.getMaxBranches());
        plan.setMaxUsers(dto.getMaxUsers());
        plan.setMaxProducts(dto.getMaxProducts());
        plan.setActive(dto.getActive());
        plan.setAdvancedReports(dto.getAdvancedReports());
        plan.setInventory(dto.getInventory());
        plan.setIntegrations(dto.getIntegrations());
        plan.setEcommerce(dto.getEcommerce());
        plan.setInvoiceBranding(dto.getInvoiceBranding());
        plan.setPrioritySupport(dto.getPrioritySupport());
        plan.setMultiLocation(dto.getMultiLocation());
        plan.setDescription(dto.getDescription());
        plan.setExtraFeatures(dto.getExtraFeatures());

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
                .billingCycle(dto.getBillingCycle())
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
                .extraFeatures(dto.getExtraFeatures())
                .build();
    }
}
