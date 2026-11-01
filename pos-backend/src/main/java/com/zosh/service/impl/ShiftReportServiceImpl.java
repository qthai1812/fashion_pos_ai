package com.zosh.service.impl;

import com.zosh.domain.OrderStatus;
import com.zosh.domain.PaymentType;
import com.zosh.mapper.RefundMapper;
import com.zosh.mapper.ShiftReportMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.ShiftReportDTO;
import com.zosh.repository.*;
import com.zosh.service.AccessControlService;
import com.zosh.service.ShiftReportService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftReportServiceImpl implements ShiftReportService {

    private final ShiftReportRepository shiftReportRepository;
    private final UserService userService;
    private final BranchRepository branchRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftReportDTO startShift() throws Exception {
        User currentUser = userService.getCurrentUser();

        Optional<ShiftReport> activeShift = shiftReportRepository
                .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(currentUser);
        if (activeShift.isPresent()) {
            return getCurrentShiftProgress(currentUser.getId());
        }

        Branch branch = currentUser.getBranch();
        if (branch == null) {
            throw new Exception("You are not assigned to any branch, so you cannot start a shift");
        }
        ShiftReport shiftReport = ShiftReport.builder()
                .cashier(currentUser)
                .shiftStart(LocalDateTime.now())
                .branch(branch)
                .totalSales(0.0)
                .totalRefunds(0.0)
                .netSale(0.0)
                .totalOrders(0)
                .build();

        ShiftReport savedReport = shiftReportRepository.save(shiftReport);
        return ShiftReportMapper.toDTO(savedReport);
    }

    private List<Order> filterValidSalesOrders(List<Order> orders) {
        if (orders == null) {
            return Collections.emptyList();
        }
        return orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.REFUNDED && o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());
    }

    private List<PaymentSummary> getPaymentSummaries(List<Order> orders) {
        List<Order> validOrders = filterValidSalesOrders(orders);
        // Tỷ lệ % phải tính trên tổng của chính các đơn hợp lệ (không gồm đơn REFUNDED/CANCELLED),
        // nếu không tổng các phần trăm sẽ không bằng 100%.
        double validTotal = validOrders.stream()
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();
        Map<PaymentType, List<Order>> grouped = validOrders.stream()
                .collect(Collectors.groupingBy(order -> order.getPaymentType() != null ? order.getPaymentType() : PaymentType.CASH));

        List<PaymentSummary> summaries = new ArrayList<>();
        for (Map.Entry<PaymentType, List<Order>> entry : grouped.entrySet()) {
            double amount = entry.getValue().stream()
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                    .sum();
            int transactions = entry.getValue().size();
            double percent = validTotal > 0 ? (amount / validTotal) * 100.0 : 0.0;

            PaymentSummary ps = new PaymentSummary();
            ps.setType(entry.getKey());
            ps.setTotalAmount(amount);
            ps.setTransactionCount(transactions);
            ps.setPercentage(percent);
            summaries.add(ps);
        }
        return summaries;
    }

    private List<Product> getTopSellingProducts(List<Order> orders) {
        List<Order> validOrders = filterValidSalesOrders(orders);
        Map<Long, Product> productMap = new HashMap<>();
        Map<Long, Integer> productSalesMap = new HashMap<>();
        for (Order order : validOrders) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product != null && product.getId() != null) {
                    productMap.putIfAbsent(product.getId(), product);
                    int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                    productSalesMap.put(product.getId(), productSalesMap.getOrDefault(product.getId(), 0) + qty);
                }
            }
        }
        return productSalesMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(entry -> {
                    Product p = productMap.get(entry.getKey());
                    Product copy = Product.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .sku(p.getSku())
                            .description(p.getDescription())
                            .mrp(p.getMrp())
                            .sellingPrice(p.getSellingPrice())
                            .brand(p.getBrand())
                            .color(p.getColor())
                            .image(p.getImage())
                            .quantity(entry.getValue())
                            .category(p.getCategory())
                            .store(p.getStore())
                            .createdAt(p.getCreatedAt())
                            .updatedAt(p.getUpdatedAt())
                            .build();
                    return copy;
                })
                .collect(Collectors.toList());
    }

    private List<Order> getRecentOrders(List<Order> orders) {
        if (orders == null) {
            return Collections.emptyList();
        }
        return orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .collect(Collectors.toList());
    }

    private ShiftReportDTO buildPopulatedDTO(ShiftReport shiftReport) {
        if (shiftReport == null) {
            return null;
        }
        if (shiftReport.getCashier() == null || shiftReport.getShiftStart() == null) {
            return ShiftReportMapper.toDTO(shiftReport);
        }
        LocalDateTime end = shiftReport.getShiftEnd() != null ? shiftReport.getShiftEnd() : LocalDateTime.now();
        List<Order> orders = orderRepository.findByCashierAndCreatedAtBetween(
                shiftReport.getCashier(), shiftReport.getShiftStart(), end
        );
        List<Refund> refunds = refundRepository.findByCashierIdAndCreatedAtBetween(
                shiftReport.getCashier().getId(), shiftReport.getShiftStart(), end
        );

        shiftReport.setRecentOrders(getRecentOrders(orders));
        shiftReport.setTopSellingProducts(getTopSellingProducts(orders));
        shiftReport.setPaymentSummaries(getPaymentSummaries(orders));

        ShiftReportDTO dto = ShiftReportMapper.toDTO(shiftReport);
        dto.setRefunds(refunds != null
                ? refunds.stream().map(RefundMapper::toDTO).collect(Collectors.toList())
                : Collections.emptyList());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportDTO getShiftReportById(Long id) throws Exception {
        ShiftReport report = shiftReportRepository.findById(id)
                .orElseThrow(() -> new Exception("shift report not found with given id " + id));
        assertCanViewShift(userService.getCurrentUser(), report);
        return buildPopulatedDTO(report);
    }

    private boolean canViewShift(User user, ShiftReport report) {
        if (report.getCashier() != null && report.getCashier().getId().equals(user.getId())) {
            return true;
        }
        return accessControl.canManageBranch(user, report.getBranch());
    }

    private void assertCanViewShift(User user, ShiftReport report) throws Exception {
        if (!canViewShift(user, report)) {
            throw new Exception("You do not have permission to view this shift report");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getAllShiftReports() {
        User current;
        try {
            current = userService.getCurrentUser();
        } catch (Exception e) {
            return Collections.emptyList();
        }
        List<ShiftReport> reports = shiftReportRepository.findAll();
        return reports.stream()
                .filter(r -> canViewShift(current, r))
                .map(this::buildPopulatedDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getShiftReportsByBranchId(Long branchId) {
        User current;
        try {
            current = userService.getCurrentUser();
        } catch (Exception e) {
            return Collections.emptyList();
        }
        List<ShiftReport> reports = shiftReportRepository.findByBranchId(branchId);
        return reports.stream()
                .filter(r -> canViewShift(current, r))
                .map(this::buildPopulatedDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getShiftReportsByCashierId(Long cashierId) {
        User current;
        try {
            current = userService.getCurrentUser();
        } catch (Exception e) {
            return Collections.emptyList();
        }
        List<ShiftReport> reports = shiftReportRepository.findByCashierId(cashierId);
        return reports.stream()
                .filter(r -> canViewShift(current, r))
                .map(this::buildPopulatedDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftReportDTO endShift(Long shiftReportId, LocalDateTime shiftEnd) throws Exception {
        User currentUser = userService.getCurrentUser();

        ShiftReport shiftReport;
        if (shiftReportId != null) {
            shiftReport = shiftReportRepository.findById(shiftReportId)
                    .orElseThrow(() -> new Exception("Shift not found with id: " + shiftReportId));
            assertCanViewShift(currentUser, shiftReport);
        } else {
            shiftReport = shiftReportRepository
                    .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(currentUser)
                    .orElseThrow(() -> new Exception("Shift not found"));
        }

        if (shiftReport.getShiftEnd() != null) {
            return buildPopulatedDTO(shiftReport);
        }

        User shiftCashier = shiftReport.getCashier() != null ? shiftReport.getCashier() : currentUser;
        LocalDateTime actualEnd = shiftEnd != null ? shiftEnd : LocalDateTime.now();
        shiftReport.setShiftEnd(actualEnd);

        List<Refund> refunds = refundRepository.findByCashierIdAndCreatedAtBetween(
                shiftCashier.getId(), shiftReport.getShiftStart(), actualEnd
        );
        double totalRefunds = refunds.stream()
                .mapToDouble(refund -> refund.getAmount() != null ? refund.getAmount() : 0.0)
                .sum();

        List<Order> orders = orderRepository.findByCashierAndCreatedAtBetween(
                shiftCashier, shiftReport.getShiftStart(), actualEnd
        );
        double totalSales = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        int totalOrders = (int) orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .count();
        double netSales = totalSales - totalRefunds;

        shiftReport.setTotalRefunds(totalRefunds);
        shiftReport.setTotalSales(totalSales);
        shiftReport.setTotalOrders(totalOrders);
        shiftReport.setNetSale(netSales);
        shiftReport.setRecentOrders(getRecentOrders(orders));
        shiftReport.setTopSellingProducts(getTopSellingProducts(orders));
        shiftReport.setPaymentSummaries(getPaymentSummaries(orders));

        ShiftReport savedReport = shiftReportRepository.save(shiftReport);
        ShiftReportDTO dto = ShiftReportMapper.toDTO(savedReport);
        dto.setRefunds(refunds.stream().map(RefundMapper::toDTO).collect(Collectors.toList()));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftReportDTO getCurrentShiftProgress(Long cashierId) throws Exception {
        User targetCashier = userService.getCurrentUser();
        if (cashierId != null && !cashierId.equals(targetCashier.getId())) {
            User requested = userRepository.findById(cashierId).orElseThrow(() -> new Exception("Cashier not found"));
            accessControl.assertCanManageBranch(targetCashier, requested.getBranch());
            targetCashier = requested;
        }

        ShiftReport shiftReport = shiftReportRepository
                .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(targetCashier)
                .orElseThrow(() -> new Exception("no active shift found for cashier"));

        LocalDateTime now = LocalDateTime.now();

        List<Order> orders = orderRepository.findByCashierAndCreatedAtBetween(
                targetCashier, shiftReport.getShiftStart(), now);

        List<Refund> refunds = refundRepository.findByCashierIdAndCreatedAtBetween(
                targetCashier.getId(), shiftReport.getShiftStart(), now
        );

        double totalRefunds = refunds.stream()
                .mapToDouble(refund -> refund.getAmount() != null ? refund.getAmount() : 0.0)
                .sum();

        double totalSales = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        int totalOrders = (int) orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .count();
        double netSales = totalSales - totalRefunds;

        shiftReport.setTotalRefunds(totalRefunds);
        shiftReport.setTotalSales(totalSales);
        shiftReport.setTotalOrders(totalOrders);
        shiftReport.setNetSale(netSales);
        shiftReport.setRecentOrders(getRecentOrders(orders));
        shiftReport.setTopSellingProducts(getTopSellingProducts(orders));
        shiftReport.setPaymentSummaries(getPaymentSummaries(orders));

        ShiftReport savedReport = shiftReportRepository.save(shiftReport);
        ShiftReportDTO dto = ShiftReportMapper.toDTO(savedReport);
        dto.setRefunds(refunds.stream().map(RefundMapper::toDTO).collect(Collectors.toList()));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShiftReport(Long id) throws Exception {
        ShiftReport report = shiftReportRepository.findById(id)
                .orElseThrow(() -> new Exception("shift report not found with given id " + id));
        // Gỡ khóa ngoại trong bảng Refund trước khi xóa ShiftReport để giữ lại lịch sử hoàn tiền
        List<Refund> linkedRefunds = refundRepository.findByShiftReportId(report.getId());
        for (Refund r : linkedRefunds) {
            r.setShiftReport(null);
            refundRepository.save(r);
        }
        shiftReportRepository.delete(report);
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportDTO getShiftByCashierAndDate(Long cashierId, LocalDateTime date) throws Exception {
        User cashier = userRepository.findById(cashierId).orElseThrow(
                () -> new Exception("cashier not found with id " + cashierId)
        );
        LocalDateTime start = date.toLocalDate().atStartOfDay();
        LocalDateTime end = date.toLocalDate().atTime(LocalTime.MAX);

        ShiftReport report = shiftReportRepository.findTopByCashierAndShiftStartBetweenOrderByShiftStartDesc(
                cashier, start, end
        ).orElseThrow(() -> new Exception("shift not found for date"));

        return buildPopulatedDTO(report);
    }
}
