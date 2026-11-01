package com.zosh.service.impl;

import com.zosh.domain.OrderStatus;
import com.zosh.mapper.RefundMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.RefundDTO;
import com.zosh.repository.CustomerRepository;
import com.zosh.repository.OrderRepository;
import com.zosh.repository.RefundRepository;
import com.zosh.repository.ShiftReportRepository;
import com.zosh.service.AccessControlService;
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
    private final AccessControlService accessControl;

    private static final double VND_PER_POINT_EARNED = 10000.0;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundDTO createRefund(RefundDTO refund) throws Exception {
        User cashier = userService.getCurrentUser();

        Order order = orderRepository.findById(refund.getOrderId()).orElseThrow(
                () -> new Exception("Order not found with id: " + refund.getOrderId())
        );
        accessControl.assertCanAccessBranch(cashier, order.getBranch());

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new Exception("A cancelled order cannot be refunded");
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw new Exception("This order has already been fully refunded");
        }

        List<Refund> existingRefunds = refundRepository.findByOrderId(order.getId());
        double alreadyRefunded = existingRefunds.stream()
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                .sum();

        double orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
        double remainingRefundable = Math.max(0.0, orderTotal - alreadyRefunded);

        if (remainingRefundable < 0.01) {
            throw new Exception("This order has already been refunded");
        }

        Branch branch = order.getBranch();
        com.zosh.domain.PaymentType paymentType =
                refund.getPaymentType() != null ? refund.getPaymentType() : order.getPaymentType();

        double refundAmount = (refund.getAmount() != null && refund.getAmount() > 0)
                ? refund.getAmount()
                : remainingRefundable;

        if (refundAmount <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than 0");
        }
        if (alreadyRefunded + refundAmount > orderTotal + 0.01) {
            throw new Exception(
                    "Refund amount (" + refundAmount + ") exceeds remaining refundable order total (" + remainingRefundable + ")");
        }

        double totalRefundedAfter = alreadyRefunded + refundAmount;
        boolean isFullRefund = totalRefundedAfter >= orderTotal - 0.01;
        if (isFullRefund) {
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);

            if (order.getItems() != null && !order.getItems().isEmpty() && branch != null) {
                List<OrderItem> sortedItems = order.getItems().stream()
                        .filter(i -> i.getProduct() != null && i.getProduct().getId() != null)
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
        if (cashier.getRole() == com.zosh.domain.UserRole.ROLE_BRANCH_CASHIER && activeShift == null) {
            throw new Exception("Vui lòng bắt đầu ca làm việc (Start Shift) trước khi thực hiện hoàn tiền.");
        }

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

            int pointsToDeduct = (int) Math.floor(refundAmount / VND_PER_POINT_EARNED);

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
        User current = userService.getCurrentUser();
        // Chỉ trả về các khoản hoàn tiền thuộc phạm vi cửa hàng/chi nhánh mà user được phép xem
        return refundRepository.findAll().stream()
                .filter(r -> accessControl.canAccessBranch(current, r.getBranch()))
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByCashier(Long cashierId) throws Exception {
        User current = userService.getCurrentUser();
        return refundRepository.findByCashierId(cashierId).stream()
                .filter(r -> current.getId().equals(cashierId)
                        || accessControl.canManageBranch(current, r.getBranch()))
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByShiftReport(Long shiftReportId) throws Exception {
        User current = userService.getCurrentUser();
        return refundRepository.findByShiftReportId(shiftReportId).stream()
                .filter(r -> accessControl.canAccessBranch(current, r.getBranch()))
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByCashierAndDateRange(Long cashierId,
                                                          LocalDateTime startDate,
                                                          LocalDateTime endDate) throws Exception {
        User current = userService.getCurrentUser();
        return refundRepository.findByCashierIdAndCreatedAtBetween(cashierId, startDate, endDate)
                .stream()
                .filter(r -> current.getId().equals(cashierId)
                        || accessControl.canManageBranch(current, r.getBranch()))
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundDTO> getRefundByBranch(Long branchId) throws Exception {
        User current = userService.getCurrentUser();
        return refundRepository.findByBranchId(branchId).stream()
                .filter(r -> accessControl.canAccessBranch(current, r.getBranch()))
                .map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public RefundDTO getRefundById(Long refundId) throws Exception {
        Refund refund = refundRepository.findById(refundId).orElseThrow(
                () -> new Exception("Refund not found with id: " + refundId)
        );
        accessControl.assertCanAccessBranch(userService.getCurrentUser(), refund.getBranch());
        return RefundMapper.toDTO(refund);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRefund(Long refundId) throws Exception {
        Refund refund = refundRepository.findById(refundId).orElseThrow(
                () -> new Exception("Refund not found with id: " + refundId)
        );
        accessControl.assertCanManageBranch(userService.getCurrentUser(), refund.getBranch());

        Order order = refund.getOrder();
        if (order != null) {
            double refundAmount = refund.getAmount() != null ? refund.getAmount() : 0.0;
            double orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
            double remainingRefunded = refundRepository.findByOrderId(order.getId()).stream()
                    .filter(r -> !r.getId().equals(refund.getId()))
                    .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0.0)
                    .sum();

            boolean orderWasFullyRefunded = order.getStatus() == OrderStatus.REFUNDED;
            boolean reopenOrder = orderWasFullyRefunded && remainingRefunded < orderTotal - 0.01;

            // Xóa khoản hoàn tiền làm đơn không còn "hoàn toàn bộ" -> lấy lại hàng khỏi kho và mở lại đơn
            if (reopenOrder) {
                if (order.getItems() != null && order.getBranch() != null) {
                    List<OrderItem> sortedItems = order.getItems().stream()
                            .filter(i -> i.getProduct() != null && i.getProduct().getId() != null)
                            .sorted(Comparator.comparing(i -> i.getProduct().getId()))
                            .collect(Collectors.toList());
                    for (OrderItem item : sortedItems) {
                        inventoryService.deductInventory(
                                item.getProduct().getId(), order.getBranch().getId(), item.getQuantity());
                    }
                }
                order.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(order);
            }

            // Đảo ngược điều chỉnh điểm tích lũy đã thực hiện khi tạo khoản hoàn tiền này
            if (order.getCustomer() != null) {
                Customer customer = customerRepository.findByIdWithLock(order.getCustomer().getId())
                        .orElse(order.getCustomer());
                int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
                int pointsDeductedBefore = (int) Math.floor(refundAmount / VND_PER_POINT_EARNED);
                int pointsRestoredBefore = reopenOrder ? extractPointsUsed(order) : 0;
                int updatedPoints = Math.max(0, currentPoints - pointsRestoredBefore + pointsDeductedBefore);
                if (updatedPoints != currentPoints) {
                    customer.setLoyaltyPoints(updatedPoints);
                    customerRepository.save(customer);
                }
            }
        }

        refundRepository.delete(refund);
    }

    private int extractPointsUsed(Order order) {
        if (order.getCouponCode() == null || !order.getCouponCode().startsWith("POINTS")) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("-(\\d+)\\s*pts")
                .matcher(order.getCouponCode());
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
