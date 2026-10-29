package com.zosh.repository;

import com.zosh.modal.ShiftReport;
import com.zosh.modal.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShiftReportRepository extends JpaRepository<ShiftReport, Long> {

    List<ShiftReport> findByCashierId(Long id);
    List<ShiftReport> findByBranchId(Long id);

    Optional<ShiftReport> findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(User cashier);

    Optional<ShiftReport> findTopByCashierAndShiftStartBetweenOrderByShiftStartDesc(
            User cashier,
            LocalDateTime start,
            LocalDateTime end
    );
}