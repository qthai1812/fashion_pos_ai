package com.zosh.service.impl;

import com.zosh.modal.*;
import com.zosh.payload.response.*;
import com.zosh.repository.*;
import com.zosh.service.StoreAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreAnalyticsServiceImpl implements StoreAnalyticsService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final StoreRepository storeRepository;

    private List<Order> getAllOrdersForUser(Long storeAdminId) throws Exception {
        userRepository.findById(storeAdminId)
                .orElseThrow(() -> new Exception("User not found: " + storeAdminId));
        Store store = storeRepository.findByStoreAdminId(storeAdminId);
        if (store == null) {
            throw new Exception("Store not found for user: " + storeAdminId);
        }
        // Truy vấn 1 lần kèm @EntityGraph thay vì lặp qua từng Branch gây N+1 queries
        return orderRepository.findAllByStoreIdWithDetails(store.getId());
    }

    @Override
    public StoreOverviewDTO getStoreOverview(Long storeAdminId) throws Exception {
        List<Order> orders = getAllOrdersForUser(storeAdminId);
        Store store = storeRepository.findByStoreAdminId(storeAdminId);
        if (store == null) throw new Exception("Store not found");
        Long storeId = store.getId();
        List<Branch> branches = branchRepository.findByStoreId(storeId);
        List<User> employees = userRepository.findByStore(store);
        long totalProducts = productRepository.findByStoreId(storeId).size();
        double totalRevenue = orders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
        double avgOrderValue = orders.isEmpty() ? 0 : totalRevenue / orders.size();

        LocalDate now = LocalDate.now();
        double thisMonth = orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().getMonth() == now.getMonth()
                        && o.getCreatedAt().getYear() == now.getYear())
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
        double lastMonth = orders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().getMonth() == now.minusMonths(1).getMonth()
                        && o.getCreatedAt().getYear() == now.minusMonths(1).getYear())
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
        double growth = lastMonth == 0 ? 0 : ((thisMonth - lastMonth) / lastMonth * 100);

        return new StoreOverviewDTO(totalRevenue, orders.size(), branches.size(),
                (int) totalProducts, employees.size(), Math.round(avgOrderValue * 100) / 100.0,
                Math.round(growth * 10) / 10.0);
    }

    @Override
    public List<SalesTrendDTO> getSalesTrends(Long storeAdminId, String period) throws Exception {
        List<Order> orders = getAllOrdersForUser(storeAdminId);
        Map<String, double[]> grouped = new TreeMap<>();
        DateTimeFormatter fmt = period.equals("monthly") ? DateTimeFormatter.ofPattern("yyyy-MM") :
                period.equals("weekly") ? DateTimeFormatter.ofPattern("yyyy-'W'ww") :
                        DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Order o : orders) {
            if (o.getCreatedAt() == null) continue;
            String key = o.getCreatedAt().format(fmt);
            grouped.computeIfAbsent(key, k -> new double[]{0, 0});
            grouped.get(key)[0] += o.getTotalAmount() != null ? o.getTotalAmount() : 0;
            grouped.get(key)[1]++;
        }
        List<SalesTrendDTO> result = new ArrayList<>();
        String[] keys = grouped.keySet().toArray(new String[0]);
        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            double sales = grouped.get(k)[0];
            double prevSales = i > 0 ? grouped.get(keys[i - 1])[0] : 0;
            double growth = prevSales == 0 ? 0 : ((sales - prevSales) / prevSales * 100);
            result.add(new SalesTrendDTO(k, sales, (int) grouped.get(k)[1], Math.round(growth * 10) / 10.0));
        }
        return result;
    }

    @Override
    public List<DailySalesDTO> getDailySales(Long storeAdminId) throws Exception {
        List<Order> orders = getAllOrdersForUser(storeAdminId);
        List<DailySalesDTO> result = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            List<Order> dayOrders = orders.stream()
                    .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(start) && o.getCreatedAt().isBefore(end))
                    .collect(Collectors.toList());
            double total = dayOrders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
            result.add(new DailySalesDTO(date.toString(), total, dayOrders.size()));
        }
        return result;
    }

    @Override
    public List<DailySalesDTO> getMonthlySales(Long storeAdminId) throws Exception {
        List<Order> orders = getAllOrdersForUser(storeAdminId);
        Map<String, double[]> monthMap = new TreeMap<>();
        for (Order o : orders) {
            if (o.getCreatedAt() == null) continue;
            String key = o.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            monthMap.computeIfAbsent(key, k -> new double[]{0, 0});
            monthMap.get(key)[0] += o.getTotalAmount() != null ? o.getTotalAmount() : 0;
            monthMap.get(key)[1]++;
        }
        return monthMap.entrySet().stream()
                .map(e -> new DailySalesDTO(e.getKey(), e.getValue()[0], (int) e.getValue()[1]))
                .collect(Collectors.toList());
    }

    @Override
    public List<CategorySalesDTO> getSalesByCategory(Long storeAdminId) throws Exception {
        List<Order> orders = getAllOrdersForUser(storeAdminId);
        Map<String, double[]> catMap = new HashMap<>();
        for (Order o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItem item : o.getItems()) {
                if (item.getProduct() == null || item.getProduct().getCategory() == null) continue;
                String cat = item.getProduct().getCategory().getName();
                catMap.computeIfAbsent(cat, k -> new double[]{0, 0});
                catMap.get(cat)[0] += item.getQuantity();
                catMap.get(cat)[1] += item.getPrice() != null ? item.getPrice() : 0;
            }
        }
        double totalRev = catMap.values().stream().mapToDouble(v -> v[1]).sum();
        return catMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .map(e -> {
                    double pct = totalRev == 0 ? 0 : (e.getValue()[1] * 100.0 / totalRev);
                    return new CategorySalesDTO(e.getKey(), (long) e.getValue()[0], e.getValue()[1], Math.round(pct * 10) / 10.0);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentBreakdownDTO> getSalesByPaymentMethod(Long storeAdminId) throws Exception {
        return getPaymentBreakdown(storeAdminId);
    }

    @Override
    public List<BranchPerformanceDTO> getSalesByBranch(Long storeAdminId) throws Exception {
        return getBranchPerformance(storeAdminId);
    }

    @Override
    public List<PaymentBreakdownDTO> getPaymentBreakdown(Long storeAdminId) throws Exception {
        List<Order> orders = getAllOrdersForUser(storeAdminId);
        Map<String, double[]> payMap = new HashMap<>();
        for (Order o : orders) {
            if (o.getPaymentType() == null) continue;
            String pt = o.getPaymentType().name();
            payMap.computeIfAbsent(pt, k -> new double[]{0, 0});
            payMap.get(pt)[0]++;
            payMap.get(pt)[1] += o.getTotalAmount() != null ? o.getTotalAmount() : 0;
        }
        double totalAmt = payMap.values().stream().mapToDouble(v -> v[1]).sum();
        return payMap.entrySet().stream()
                .map(e -> {
                    double pct = totalAmt == 0 ? 0 : (e.getValue()[1] * 100.0 / totalAmt);
                    return new PaymentBreakdownDTO(e.getKey(), (long) e.getValue()[0], e.getValue()[1], Math.round(pct * 10) / 10.0);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<BranchPerformanceDTO> getBranchPerformance(Long storeAdminId) throws Exception {
        userRepository.findById(storeAdminId).orElseThrow(() -> new Exception("User not found"));
        Store store = storeRepository.findByStoreAdminId(storeAdminId);
        if (store == null) throw new Exception("Store not found");
        List<Branch> branches = branchRepository.findByStoreId(store.getId());
        List<BranchPerformanceDTO> result = new ArrayList<>();
        for (Branch branch : branches) {
            List<Order> branchOrders = orderRepository.findByBranchId(branch.getId());
            double rev = branchOrders.stream().mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0).sum();
            double avg = branchOrders.isEmpty() ? 0 : rev / branchOrders.size();
            int empCount = userRepository.findByBranchId(branch.getId()).size();
            result.add(new BranchPerformanceDTO(branch.getId(), branch.getName(), rev, branchOrders.size(), empCount, Math.round(avg * 100) / 100.0));
        }
        return result.stream().sorted((a, b) -> Double.compare(b.getTotalRevenue(), a.getTotalRevenue())).collect(Collectors.toList());
    }

    @Override
    public StoreAlertsDTO getStoreAlerts(Long storeAdminId) throws Exception {
        userRepository.findById(storeAdminId).orElseThrow(() -> new Exception("User not found"));
        Store store = storeRepository.findByStoreAdminId(storeAdminId);
        if (store == null) {
            return StoreAlertsDTO.builder()
                    .inactiveCashiers(new ArrayList<>())
                    .lowStockAlerts(new ArrayList<>())
                    .noSaleToday(new ArrayList<>())
                    .refundSpike(new ArrayList<>())
                    .build();
        }

        Long storeId = store.getId();
        List<Branch> branches = branchRepository.findByStoreId(storeId);
        List<com.zosh.payload.dto.ProductDTO> lowStockAlerts = new ArrayList<>();
        List<com.zosh.payload.dto.BranchDTO> noSaleToday = new ArrayList<>();

        LocalDate today = LocalDate.now();

        for (Branch branch : branches) {
            List<Order> todayOrders = orderRepository.findByBranchIdAndCreatedAtBetween(
                    branch.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            if (todayOrders.isEmpty()) {
                noSaleToday.add(com.zosh.mapper.BranchMapper.toDTO(branch));
            }

            List<Inventory> inventories = inventoryRepository.findByBranchId(branch.getId());
            for (Inventory inv : inventories) {
                if (inv.getQuantity() != null && inv.getMinStockLevel() != null && inv.getQuantity() <= inv.getMinStockLevel()) {
                    if (inv.getProduct() != null) {
                        lowStockAlerts.add(com.zosh.mapper.ProductMapper.toDTO(inv.getProduct()));
                    }
                }
            }
        }

        return StoreAlertsDTO.builder()
                .inactiveCashiers(new ArrayList<>())
                .lowStockAlerts(lowStockAlerts)
                .noSaleToday(noSaleToday)
                .refundSpike(new ArrayList<>())
                .build();
    }
}
