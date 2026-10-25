package com.zosh.service.impl;

import com.zosh.modal.Order;
import com.zosh.modal.OrderItem;
import com.zosh.payload.response.*;
import com.zosh.repository.InventoryRepository;
import com.zosh.repository.OrderRepository;
import com.zosh.repository.ShiftReportRepository;
import com.zosh.service.BranchAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchAnalyticsServiceImpl implements BranchAnalyticsService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final ShiftReportRepository shiftReportRepository;

    @Override
    public List<DailySalesDTO> getDailySalesChart(Long branchId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1);
        LocalDateTime rangeStart = startDate.atStartOfDay();
        LocalDateTime rangeEnd = today.plusDays(1).atStartOfDay();

        // Truy vấn 1 lần duy nhất cho toàn bộ khoảng ngày (loại bỏ vòng lặp N queries)
        List<Order> ordersInRange = orderRepository.findByBranchIdAndCreatedAtBetween(branchId, rangeStart, rangeEnd);
        Map<LocalDate, List<Order>> ordersByDate = ordersInRange.stream()
                .filter(o -> o.getCreatedAt() != null)
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().toLocalDate()));

        List<DailySalesDTO> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<Order> dayOrders = ordersByDate.getOrDefault(date, Collections.emptyList());
            double total = dayOrders.stream()
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                    .sum();
            result.add(new DailySalesDTO(date.toString(), total, dayOrders.size()));
        }
        return result;
    }

    @Override
    public List<TopProductDTO> getTopProductsByQuantity(Long branchId) {
        List<Order> allOrders = orderRepository.findByBranchId(branchId);
        Map<Long, long[]> productStats = new HashMap<>();
        Map<Long, String[]> productMeta = new HashMap<>();

        for (Order order : allOrders) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null) continue;
                Long pid = item.getProduct().getId();
                productStats.computeIfAbsent(pid, k -> new long[]{0, 0});
                productStats.get(pid)[0] += item.getQuantity();
                productStats.get(pid)[1] += (long) (item.getPrice() != null ? item.getPrice() * 100 : 0);
                productMeta.put(pid, new String[]{item.getProduct().getName(), item.getProduct().getSku()});
            }
        }

        long totalQty = productStats.values().stream().mapToLong(v -> v[0]).sum();
        return productStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Long pid = e.getKey();
                    long qty = e.getValue()[0];
                    double rev = e.getValue()[1] / 100.0;
                    double pct = totalQty == 0 ? 0 : (qty * 100.0 / totalQty);
                    String[] meta = productMeta.getOrDefault(pid, new String[]{"Unknown", ""});
                    return new TopProductDTO(pid, meta[0], meta[1], qty, rev, Math.round(pct * 10) / 10.0);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<TopCashierDTO> getTopCashiersByRevenue(Long branchId) {
        List<Order> allOrders = orderRepository.findByBranchId(branchId);
        Map<Long, double[]> cashierStats = new HashMap<>();
        Map<Long, String> cashierNames = new HashMap<>();

        for (Order order : allOrders) {
            if (order.getCashier() == null) continue;
            Long cid = order.getCashier().getId();
            cashierStats.computeIfAbsent(cid, k -> new double[]{0, 0});
            cashierStats.get(cid)[0] += order.getTotalAmount() != null ? order.getTotalAmount() : 0;
            cashierStats.get(cid)[1]++;
            cashierNames.put(cid, order.getCashier().getFullName());
        }

        return cashierStats.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> new TopCashierDTO(e.getKey(), cashierNames.getOrDefault(e.getKey(), "Unknown"),
                        e.getValue()[0], (int) e.getValue()[1]))
                .collect(Collectors.toList());
    }

    @Override
    public List<CategorySalesDTO> getCategoryWiseSalesBreakdown(Long branchId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Order> orders = orderRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end);

        Map<String, double[]> categoryStats = new HashMap<>();
        for (Order order : orders) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null || item.getProduct().getCategory() == null) continue;
                String cat = item.getProduct().getCategory().getName();
                categoryStats.computeIfAbsent(cat, k -> new double[]{0, 0});
                categoryStats.get(cat)[0] += item.getQuantity();
                categoryStats.get(cat)[1] += item.getPrice() != null ? item.getPrice() : 0;
            }
        }

        double totalRevenue = categoryStats.values().stream().mapToDouble(v -> v[1]).sum();
        return categoryStats.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .map(e -> {
                    double pct = totalRevenue == 0 ? 0 : (e.getValue()[1] * 100.0 / totalRevenue);
                    return new CategorySalesDTO(e.getKey(), (long) e.getValue()[0], e.getValue()[1],
                            Math.round(pct * 10) / 10.0);
                })
                .collect(Collectors.toList());
    }

    @Override
    public BranchOverviewDTO getTodayOverview(Long branchId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        List<Order> todayOrders = orderRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end);
        double todayRevenue = todayOrders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
        double avgOrderValue = todayOrders.isEmpty() ? 0 : todayRevenue / todayOrders.size();
        long lowStock = inventoryRepository.findByBranchId(branchId).stream()
                .filter(inv -> inv.getMinStockLevel() != null && inv.getQuantity() <= inv.getMinStockLevel()).count();
        return new BranchOverviewDTO(todayRevenue, todayOrders.size(), 0.0, 0, (int) lowStock,
                Math.round(avgOrderValue * 100) / 100.0);
    }

    @Override
    public List<PaymentBreakdownDTO> getPaymentBreakdown(Long branchId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Order> orders = orderRepository.findByBranchIdAndCreatedAtBetween(branchId, start, end);
        Map<String, double[]> paymentStats = new HashMap<>();
        for (Order order : orders) {
            if (order.getPaymentType() == null) continue;
            String pt = order.getPaymentType().name();
            paymentStats.computeIfAbsent(pt, k -> new double[]{0, 0});
            paymentStats.get(pt)[0]++;
            paymentStats.get(pt)[1] += order.getTotalAmount() != null ? order.getTotalAmount() : 0;
        }
        double totalAmount = paymentStats.values().stream().mapToDouble(v -> v[1]).sum();
        return paymentStats.entrySet().stream()
                .map(e -> {
                    double pct = totalAmount == 0 ? 0 : (e.getValue()[1] * 100.0 / totalAmount);
                    return new PaymentBreakdownDTO(e.getKey(), (long) e.getValue()[0], e.getValue()[1],
                            Math.round(pct * 10) / 10.0);
                })
                .collect(Collectors.toList());
    }
}
