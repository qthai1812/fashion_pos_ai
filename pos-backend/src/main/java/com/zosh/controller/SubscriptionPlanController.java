package com.zosh.controller;

import com.zosh.payload.dto.SubscriptionPlanDTO;
import com.zosh.payload.response.ApiResponse;
import com.zosh.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/super-admin/subscription-plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    @PostMapping
    public ResponseEntity<SubscriptionPlanDTO> createPlan(
            @RequestBody SubscriptionPlanDTO dto) {
        return ResponseEntity.ok(subscriptionPlanService.createPlan(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubscriptionPlanDTO> updatePlan(
            @PathVariable Long id,
            @RequestBody SubscriptionPlanDTO dto) throws Exception {
        return ResponseEntity.ok(subscriptionPlanService.updatePlan(id, dto));
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionPlanDTO>> getAllPlans() {
        return ResponseEntity.ok(subscriptionPlanService.getAllPlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionPlanDTO> getPlanById(
            @PathVariable Long id) throws Exception {
        return ResponseEntity.ok(subscriptionPlanService.getPlanById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deletePlan(
            @PathVariable Long id) throws Exception {
        subscriptionPlanService.deletePlan(id);
        ApiResponse response = new ApiResponse();
        response.setMessage("Subscription plan deleted successfully");
        return ResponseEntity.ok(response);
    }
}
