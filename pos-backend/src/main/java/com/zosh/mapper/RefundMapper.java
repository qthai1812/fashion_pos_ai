package com.zosh.mapper;

import com.zosh.modal.Refund;
import com.zosh.payload.dto.RefundDTO;

public class RefundMapper {

    public static RefundDTO toDTO(Refund refund) {
        if (refund == null) {
            return null;
        }
        return RefundDTO.builder()
                .id(refund.getId())
                .orderId(refund.getOrder() != null ? refund.getOrder().getId() : null)
                .reason(refund.getReason())
                .amount(refund.getAmount())
                .paymentType(refund.getPaymentType())
                .cashierName(refund.getCashier() != null ? refund.getCashier().getFullName() : null)
                .branchId(refund.getBranch() != null ? refund.getBranch().getId() : null)
                .shiftReportId(refund.getShiftReport() != null ? refund.getShiftReport().getId() : null)
                .createdAt(refund.getCreatedAt())
                .build();
    }
}