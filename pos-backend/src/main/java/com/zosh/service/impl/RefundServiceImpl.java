package com.zosh.service.impl;

import com.zosh.mapper.RefundMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.RefundDTO;
import com.zosh.repository.CustomerRepository;
import com.zosh.repository.OrderRepository;
import com.zosh.repository.RefundRepository;
import com.zosh.repository.ShiftReportRepository;
import com.zosh.service.InventoryService;
import com.zosh.service.RefundService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final UserService userService;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final InventoryService inventoryService;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundDTO createRefund(RefundDTO refund) throws Exception {
        User cashier = userService.getCurrentUser();

        Order order = orderRepository.findById(refund.getOrderId()).orElseThrow(
                () -> new Exception("Order not found with id: " + refund.getOrderId())
        );

        if (refundRepository.existsByOrderId(refund.getOrderId()) || order.getStatus() == com.zosh.domain.OrderStatus.REFUNDED) {
            throw new Exception("This order has already been refunded");
        }

        Branch branch = order.getBranch();
        com.zosh.domain.PaymentType paymentType =
                refund.getPaymentType() != null ? refund.getPaymentType() : order.getPaymentType();

        double orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
        double refundAmount = (refund.getAmount() != null && refund.getAmount() > 0)
                ? refund.getAmount()
                : orderTotal;

        if (refundAmount <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than 0");
        }
        if (refundAmount > orderTotal) {
            throw new Exception(
                    "Refund amount (" + refundAmount + ") exceeds order total (" + orderTotal + ")");
        }

        boolean isFullRefund = Math.abs(refundAmount - orderTotal) < 0.01;
        if (isFullRefund) {
            order.setStatus(com.zosh.domain.OrderStatus.REFUNDED);
            orderRepository.save(order);

            if (order.getItems() != null && !order.getItems().isEmpty()) {
                List<OrderItem> sortedItems = order.getItems().stream()
                        .sorted(Comparator.comparing(i -> i.getProduct().getId()))
                        .collect(Collectors.toList());

                for (OrderItem item : sortedItems) {
                    Long productId = item.getProduct().getId();
                    Long branchId = branch.getId();
                    int qty = item.getQuantity();
                    inventoryService.restoreInventory(productId, branchId, qty);
                    log.info("Restored {} units of product {} to branch {} due to full refund on order {}",
                            qty, productId, branchId, order.getId());
                }
            }
        }

        ShiftReport activeShift = shiftReportRepository
                .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(cashier)
                .orElse(null);

        Refund createdRefund = Refund.builder()
                .order(order)
                .cashier(cashier)
                .branch(branch)
                .shiftReport(activeShift)
                .reason(refund.getReason())
                .amount(refundAmount)
                .paymentType(paymentType)
                .createdAt(LocalDateTime.now())
                .build();

        // Thu hồi & hoàn trả điểm tích lũy của khách hàng khi hoàn tiền
        if (order.getCustomer() != null) {
            Customer customer = customerRepository.findByIdWithLock(order.getCustomer().getId())
                    .orElse(order.getCustomer());
            int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;

            int originalEarnedPoints = (int) Math.floor(orderTotal / 10000.0);
            int pointsToDeduct = isFullRefund
                    ? originalEarnedPoints
                    : (int) Math.floor(refundAmount / 10000.0);

            int pointsToRestore = 0;
            if (isFullRefund && order.getCouponCode() != null && order.getCouponCode().startsWith("POINTS")) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("-(\\d+)\\s*pts")
                        .matcher(order.getCouponCode());
                if (matcher.find()) {
                    pointsToRestore = Integer.parseInt(matcher.group(1));
                }
            }

            int updatedPoints = Math.max(0, currentPoints + pointsToRestore - pointsToDeduct);
            if (updatedPoints != currentPoints) {
                customer.setLoyaltyPoints(updatedPoints);
                customerRepository.save(customer);
            }
        }

        Refund savedRefund = refundRepository.save(createdRefund);
        return RefundMapper.toDTO(savedRefund);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getAllRefunds() throws Exception {
        return refundRepository.findAll().stream()
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByCashier(Long cashierId) throws Exception {
        return refundRepository.findByCashierId(cashierId).stream()
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByShiftReport(Long shiftReportId) throws Exception {
        return refundRepository.findByShiftReportId(shiftReportId).stream()
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByCashierAndDateRange(Long cashierId,
                                                          LocalDateTime startDate,
                                                          LocalDateTime endDate) throws Exception {
        return refundRepository.findByCashierIdAndCreatedAtBetween(cashierId, startDate, endDate)
                .stream().map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByBranch(Long branchId) throws Exception {
        return refundRepository.findByBranchId(branchId).stream()
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RefundDTO getRefundById(Long refundId) throws Exception {
        return refundRepository.findById(refundId)
                .map(RefundMapper::toDTO).orElseThrow(
                        () -> new Exception("Refund not found with id: " + refundId)
                );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRefund(Long refundId) throws Exception {
        this.getRefundById(refundId);
        refundRepository.deleteById(refundId);
    }
}