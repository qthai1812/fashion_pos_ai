package com.zosh.repository;

import com.zosh.modal.Order;
import com.zosh.modal.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "cashier", "customer", "branch"})
    List<Order> findByCustomerId(Long customerId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "cashier", "customer", "branch"})
    List<Order> findByBranchId(Long branchId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "cashier", "customer", "branch"})
    List<Order> findByCashierId(Long cashierId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "cashier", "customer", "branch"})
    List<Order> findByBranchIdAndCreatedAtBetween(
            Long branchId,
            LocalDateTime from,
            LocalDateTime to
    );

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "cashier", "customer", "branch"})
    List<Order> findByCashierAndCreatedAtBetween(
            User cashier,
            LocalDateTime from,
            LocalDateTime to
    );

    @EntityGraph(attributePaths = {"cashier", "customer", "branch"})
    List<Order> findTop5ByBranchIdOrderByCreatedAtDesc(Long branchId);

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category", "cashier", "customer", "branch"})
    @Query("SELECT o FROM Order o WHERE o.branch.store.id = :storeId")
    List<Order> findAllByStoreIdWithDetails(@Param("storeId") Long storeId);
}
