package com.zosh.service.impl;

import com.zosh.domain.PaymentType;
import com.zosh.mapper.ShiftReportMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.ShiftReportDTO;
import com.zosh.repository.*;
import com.zosh.service.ShiftReportService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    private List<PaymentSummary> getPaymentSummaries(List<Order> orders, double totalSales) {
        Map<PaymentType, List<Order>> grouped = orders.stream()
                .collect(Collectors.groupingBy(order -> order.getPaymentType() != null ? order.getPaymentType() : PaymentType.CASH));

        List<PaymentSummary> summaries = new ArrayList<>();
        for (Map.Entry<PaymentType, List<Order>> entry : grouped.entrySet()) {
            double amount = entry.getValue().stream()
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                    .sum();
            int transactions = entry.getValue().size();
            double percent = totalSales > 0 ? (amount / totalSales) * 100.0 : 0.0;

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
        Map<Product, Integer> productSalesMap = new HashMap<>();
        for (Order order : orders) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    productSalesMap.put(product, productSalesMap.getOrDefault(product, 0) + item.getQuantity());
                }
            }
        }
        return productSalesMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<Order> getRecentOrders(List<Order> orders) {
        return orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .limit(5)
                .collect(Collectors.toList());
    }

    private ShiftReport populateShiftDetails(ShiftReport shiftReport) {
        if (shiftReport == null || shiftReport.getCashier() == null || shiftReport.getShiftStart() == null) {
            return shiftReport;
        }
        LocalDateTime end = shiftReport.getShiftEnd() != null ? shiftReport.getShiftEnd() : LocalDateTime.now();
        List<Order> orders = orderRepository.findByCashierAndCreatedAtBetween(
                shiftReport.getCashier(), shiftReport.getShiftStart(), end
        );
        List<Refund> refunds = refundRepository.findByCashierIdAndCreatedAtBetween(
                shiftReport.getCashier().getId(), shiftReport.getShiftStart(), end
        );
        double totalSales = shiftReport.getTotalSales() != null
                ? shiftReport.getTotalSales()
                : orders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0).sum();

        shiftReport.setRecentOrders(getRecentOrders(orders));
        shiftReport.setTopSellingProducts(getTopSellingProducts(orders));
        shiftReport.setPaymentSummaries(getPaymentSummaries(orders, totalSales));
        shiftReport.setRefunds(refunds);
        return shiftReport;
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftReportDTO getShiftReportById(Long id) throws Exception {
        return shiftReportRepository.findById(id)
                .map(this::populateShiftDetails)
                .map(ShiftReportMapper::toDTO)
                .orElseThrow(() -> new Exception("shift report not found with given id " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getAllShiftReports() {
        List<ShiftReport> reports = shiftReportRepository.findAll();
        return reports.stream()
                .map(this::populateShiftDetails)
                .map(ShiftReportMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getShiftReportsByBranchId(Long branchId) {
        List<ShiftReport> reports = shiftReportRepository.findByBranchId(branchId);
        return reports.stream()
                .map(this::populateShiftDetails)
                .map(ShiftReportMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftReportDTO> getShiftReportsByCashierId(Long cashierId) {
        List<ShiftReport> reports = shiftReportRepository.findByCashierId(cashierId);
        return reports.stream()
                .map(this::populateShiftDetails)
                .map(ShiftReportMapper::toDTO)
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
        } else {
            shiftReport = shiftReportRepository
                    .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(currentUser)
                    .orElseThrow(() -> new Exception("Shift not found"));
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
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        int totalOrders = orders.size();
        double netSales = totalSales - totalRefunds;

        shiftReport.setTotalRefunds(totalRefunds);
        shiftReport.setTotalSales(totalSales);
        shiftReport.setTotalOrders(totalOrders);
        shiftReport.setNetSale(netSales);
        shiftReport.setRecentOrders(getRecentOrders(orders));
        shiftReport.setTopSellingProducts(getTopSellingProducts(orders));
        shiftReport.setPaymentSummaries(getPaymentSummaries(orders, totalSales));
        shiftReport.setRefunds(refunds);

        ShiftReport savedReport = shiftReportRepository.save(shiftReport);
        return ShiftReportMapper.toDTO(savedReport);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftReportDTO getCurrentShiftProgress(Long cashierId) throws Exception {
        User targetCashier = (cashierId != null)
                ? userRepository.findById(cashierId).orElseThrow(() -> new Exception("Cashier not found"))
                : userService.getCurrentUser();

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
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        int totalOrders = orders.size();
        double netSales = totalSales - totalRefunds;

        shiftReport.setTotalRefunds(totalRefunds);
        shiftReport.setTotalSales(totalSales);
        shiftReport.setTotalOrders(totalOrders);
        shiftReport.setNetSale(netSales);
        shiftReport.setRecentOrders(getRecentOrders(orders));
        shiftReport.setTopSellingProducts(getTopSellingProducts(orders));
        shiftReport.setPaymentSummaries(getPaymentSummaries(orders, totalSales));
        shiftReport.setRefunds(refunds);

        ShiftReport savedReport = shiftReportRepository.save(shiftReport);
        return ShiftReportMapper.toDTO(savedReport);
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
        LocalDateTime start = date.withHour(0).withMinute(0).withSecond(0);
        LocalDateTime end = date.withHour(23).withMinute(59).withSecond(59);

        ShiftReport report = shiftReportRepository.findTopByCashierAndShiftStartBetweenOrderByShiftStartDesc(
                cashier, start, end
        ).orElseThrow(() -> new Exception("shift not found for date"));

        return ShiftReportMapper.toDTO(populateShiftDetails(report));
    }

}