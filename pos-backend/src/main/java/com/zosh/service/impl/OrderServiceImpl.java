package com.zosh.service.impl;

import com.zosh.domain.OrderStatus;
import com.zosh.domain.PaymentType;
import com.zosh.domain.StoreStatus;
import com.zosh.domain.UserRole;
import com.zosh.mapper.OrderMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.OrderDTO;
import com.zosh.payload.dto.OrderItemDTO;
import com.zosh.repository.*;
import com.zosh.service.InventoryService;
import com.zosh.service.OrderService;
import com.zosh.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final UserService userService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BranchRepository branchRepository;
    private final CouponRepository couponRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;
    private final RefundRepository refundRepository;

    private static final double VND_PER_POINT_EARNED = 10000.0;
    private static final double VND_PER_POINT_REDEEMED = 1000.0;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO createOrder(OrderDTO orderDTO) throws Exception {
        User cashier = userService.getCurrentUser();

        Branch branch;
        if (cashier.getBranch() != null) {
            if (orderDTO.getBranchId() != null && !cashier.getBranch().getId().equals(orderDTO.getBranchId())) {
                throw new Exception("You do not have permission to create orders for another branch");
            }
            branch = cashier.getBranch();
        } else if (orderDTO.getBranchId() != null) {
            branch = branchRepository.findById(orderDTO.getBranchId())
                    .orElseThrow(() -> new Exception("Branch not found with id: " + orderDTO.getBranchId()));
        } else {
            throw new Exception("Branch is required to create an order");
        }

        // Kiểm tra trạng thái Store và quyền truy cập Branch
        if (branch.getStore() != null && branch.getStore().getStatus() == StoreStatus.BLOCKED) {
            throw new Exception("Store is currently blocked and cannot create new orders");
        }
        if (cashier.getRole() != UserRole.ROLE_ADMIN && branch.getStore() != null) {
            Long cashierStoreId = cashier.getStore() != null
                    ? cashier.getStore().getId()
                    : (branch.getStore().getStoreAdmin() != null
                    && branch.getStore().getStoreAdmin().getId().equals(cashier.getId())
                    ? branch.getStore().getId() : null);
            if (cashierStoreId == null || !cashierStoreId.equals(branch.getStore().getId())) {
                throw new Exception("You do not have permission to create orders for this branch");
            }
        }

        if (orderDTO.getItems() == null || orderDTO.getItems().isEmpty()) {
            throw new Exception("Order must contain at least one item");
        }

        for (OrderItemDTO itemDto : orderDTO.getItems()) {
            if (itemDto.getQuantity() == null || itemDto.getQuantity() <= 0) {
                throw new IllegalArgumentException("Item quantity must be greater than 0");
            }
        }

        Customer customer = null;
        Long reqCustomerId = orderDTO.getCustomerId() != null
                ? orderDTO.getCustomerId()
                : (orderDTO.getCustomer() != null ? orderDTO.getCustomer().getId() : null);
        if (reqCustomerId != null) {
            customer = customerRepository.findByIdWithLock(reqCustomerId).orElse(null);
        }

        final Branch finalBranch = branch;

        Order order = Order.builder()
                .branch(finalBranch)
                .cashier(cashier)
                .customer(customer)
                .paymentType(orderDTO.getPaymentType())
                .note(orderDTO.getNote())
                .status(orderDTO.getStatus() != null ? orderDTO.getStatus() : OrderStatus.COMPLETED)
                .build();

        List<Long> productIds = orderDTO.getItems().stream()
                .map(OrderItemDTO::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderItem> orderItems = orderDTO.getItems().stream()
                .map(itemDto -> {
                    Product product = productMap.get(itemDto.getProductId());
                    if (product == null) {
                        throw new EntityNotFoundException("Product not found with id: " + itemDto.getProductId());
                    }
                    if (product.getStore() != null && finalBranch.getStore() != null
                            && !product.getStore().getId().equals(finalBranch.getStore().getId())) {
                        throw new IllegalArgumentException("Product " + product.getId() + " does not belong to this store");
                    }
                    double unitPrice = product.getSellingPrice() != null ? product.getSellingPrice() : 0.0;

                    return OrderItem.builder()
                            .order(order)
                            .product(product)
                            .quantity(itemDto.getQuantity())
                            .price(unitPrice * itemDto.getQuantity())
                            .build();
                })
                .collect(Collectors.toList());

        double subTotal = orderItems.stream()
                .mapToDouble(OrderItem::getPrice)
                .sum();

        double discountAmount = 0.0;
        String appliedCouponCode = null;
        int pointsUsed = 0;

        if (orderDTO.getCouponCode() != null && !orderDTO.getCouponCode().trim().isEmpty()) {
            String code = orderDTO.getCouponCode().trim().toUpperCase();

            if (code.startsWith("POINTS")) {
                if (customer == null) {
                    throw new Exception("Please select a customer to redeem loyalty points");
                }
                int availablePoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
                if (availablePoints <= 0) {
                    throw new Exception("Customer does not have any loyalty points to redeem");
                }
                int maxPointsUsable = (int) Math.floor(subTotal / VND_PER_POINT_REDEEMED);
                if (maxPointsUsable <= 0) {
                    throw new Exception("Order subtotal must be at least 1,000 VND to redeem loyalty points");
                }
                pointsUsed = Math.min(availablePoints, maxPointsUsable);
                discountAmount = pointsUsed * VND_PER_POINT_REDEEMED;
                appliedCouponCode = "POINTS (-" + pointsUsed + " pts)";
            } else {
                if (finalBranch.getStore() == null) {
                    throw new Exception("Branch does not belong to a valid store to apply coupon");
                }
                Coupon coupon = couponRepository.findByCodeAndStoreId(
                                code,
                                finalBranch.getStore().getId())
                        .orElseThrow(() -> new Exception("Invalid coupon code"));

                LocalDateTime now = LocalDateTime.now();
                if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
                    throw new Exception("Coupon is not active yet");
                }
                if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
                    throw new Exception("Coupon is expired");
                }
                if (Boolean.FALSE.equals(coupon.getIsActive())) {
                    throw new Exception("Coupon is disabled");
                }
                if (coupon.getMinOrderValue() != null && subTotal < coupon.getMinOrderValue()) {
                    throw new Exception("Order amount is below minimum required for this coupon");
                }

                if ("PERCENTAGE".equalsIgnoreCase(coupon.getDiscountType())) {
                    discountAmount = (subTotal * coupon.getDiscountValue()) / 100.0;
                } else if ("FLAT".equalsIgnoreCase(coupon.getDiscountType())) {
                    discountAmount = coupon.getDiscountValue();
                }

                if (discountAmount > subTotal) {
                    discountAmount = subTotal;
                }
                appliedCouponCode = coupon.getCode();
            }
        } else if (orderDTO.getDiscountAmount() != null && orderDTO.getDiscountAmount() > 0) {
            discountAmount = Math.min(orderDTO.getDiscountAmount(), subTotal);
        }

        double taxAmount = orderDTO.getTaxAmount() != null
                ? Math.max(0.0, orderDTO.getTaxAmount())
                : 0.0;

        double totalAmount = Math.max(0.0, subTotal + taxAmount - discountAmount);

        order.setItems(orderItems);
        order.setSubTotal(subTotal);
        order.setTaxAmount(taxAmount);
        order.setDiscountAmount(discountAmount);
        order.setCouponCode(appliedCouponCode);
        order.setTotalAmount(totalAmount);

        List<OrderItem> sortedItemsForLock = orderItems.stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .collect(Collectors.toList());

        for (OrderItem item : sortedItemsForLock) {
            Long productId = item.getProduct().getId();
            Long branchId = finalBranch.getId();
            int qty = item.getQuantity();
            try {
                inventoryService.deductInventory(productId, branchId, qty);
            } catch (Exception e) {
                throw new Exception("Failed to deduct inventory for product id " + productId + ": " + e.getMessage());
            }
        }

        if (customer != null) {
            int currentPoints = customer.getLoyaltyPoints() != null ? customer.getLoyaltyPoints() : 0;
            int earnedPoints = (int) Math.floor(totalAmount / VND_PER_POINT_EARNED);
            int updatedPoints = Math.max(0, currentPoints - pointsUsed + earnedPoints);

            customer.setLoyaltyPoints(updatedPoints);
            Customer savedCustomer = customerRepository.save(customer);
            order.setCustomer(savedCustomer);
            log.info("Customer {} loyalty points updated: {} -> {} (used: {}, earned: {})",
                    customer.getId(), currentPoints, updatedPoints, pointsUsed, earnedPoints);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order {} created successfully. Inventory deducted for {} items.", savedOrder.getId(), orderItems.size());

        return OrderMapper.toDTO(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDTO getOrderById(Long id) throws Exception {
        return orderRepository.findById(id)
                .map(OrderMapper::toDTO)
                .orElseThrow(() -> new Exception("Order not found with id " + id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDTO updateOrderStatus(Long id, OrderStatus status) throws Exception {
        if (status == null) {
            throw new IllegalArgumentException("Order status is required");
        }
        Order order = orderRepository.findById(id).orElseThrow(
                () -> new Exception("Order not found with id " + id)
        );
        order.setStatus(status);
        Order saved = orderRepository.save(order);
        return OrderMapper.toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByBranch(Long branchId,
                                            Long customerId,
                                            Long cashierId,
                                            PaymentType paymentType,
                                            OrderStatus status) throws Exception {
        return orderRepository.findByBranchId(branchId).stream()
                .filter(order -> customerId == null ||
                        (order.getCustomer() != null &&
                                order.getCustomer().getId().equals(customerId)))
                .filter(order -> cashierId == null ||
                        (order.getCashier() != null &&
                                order.getCashier().getId().equals(cashierId)))
                .filter(order -> paymentType == null ||
                        order.getPaymentType() == paymentType)
                .filter(order -> status == null ||
                        order.getStatus() == status)
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrderByCashier(Long cashierId) {
        return orderRepository.findByCashierId(cashierId).stream()
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id) throws Exception {
        Order order = orderRepository.findById(id).orElseThrow(
                () -> new Exception("Order not found with id " + id)
        );

        List<Refund> orderRefunds = refundRepository.findByOrderId(id);
        for (Refund r : orderRefunds) {
            r.setOrder(null);
            refundRepository.save(r);
        }

        if (order.getStatus() != OrderStatus.REFUNDED
                && order.getBranch() != null
                && order.getItems() != null) {
            List<OrderItem> sortedItems = order.getItems().stream()
                    .filter(i -> i.getProduct() != null && i.getProduct().getId() != null)
                    .sorted(Comparator.comparing(i -> i.getProduct().getId()))
                    .collect(Collectors.toList());
            for (OrderItem item : sortedItems) {
                try {
                    inventoryService.restoreInventory(
                            item.getProduct().getId(),
                            order.getBranch().getId(),
                            item.getQuantity()
                    );
                } catch (Exception e) {
                    log.warn("Could not restore inventory for product {} when deleting order {}: {}",
                            item.getProduct().getId(), id, e.getMessage());
                }
            }
        }

        orderRepository.delete(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getTodayOrdersByBranch(Long branchId) throws Exception {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        return orderRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end)
                .stream()
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByCustomerId(Long customerId) throws Exception {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderDTO> getTop5RecentOrdersByBranchId(Long branchId) throws Exception {
        return orderRepository.findTop5ByBranchIdOrderByCreatedAtDesc(branchId).stream()
                .map(OrderMapper::toDTO)
                .collect(Collectors.toList());
    }
}
