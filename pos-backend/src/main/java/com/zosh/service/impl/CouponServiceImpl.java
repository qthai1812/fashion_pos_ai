package com.zosh.service.impl;

import com.zosh.domain.UserRole;
import com.zosh.modal.Coupon;
import com.zosh.modal.Store;
import com.zosh.modal.User;
import com.zosh.payload.dto.CouponDTO;
import com.zosh.repository.CouponRepository;
import com.zosh.repository.StoreRepository;
import com.zosh.service.CouponService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;

    private CouponDTO mapToDto(Coupon coupon) {
        return CouponDTO.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .minOrderValue(coupon.getMinOrderValue())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .isActive(coupon.getIsActive())
                .storeId(coupon.getStore().getId())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponDTO createCoupon(CouponDTO dto) throws Exception {
        User user = userService.getCurrentUser();
        Store store = storeRepository.findById(dto.getStoreId())
                .orElseThrow(() -> new Exception("Store not found"));

        if (user.getRole() != UserRole.ROLE_STORE_ADMIN
                && user.getRole() != UserRole.ROLE_STORE_MANAGER
                && user.getRole() != UserRole.ROLE_ADMIN) {
            throw new Exception("Only store admin can create coupons");
        }

        Coupon coupon = Coupon.builder()
                .code(dto.getCode().trim().toUpperCase())
                .discountType(dto.getDiscountType())
                .discountValue(dto.getDiscountValue())
                .minOrderValue(dto.getMinOrderValue())
                .validFrom(dto.getValidFrom())
                .validUntil(dto.getValidUntil())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .store(store)
                .build();

        return mapToDto(couponRepository.save(coupon));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponDTO updateCoupon(Long id, CouponDTO dto) throws Exception {
        Coupon coupon = couponRepository.findById(id).orElseThrow(() -> new Exception("Coupon not found"));

        if (dto.getCode() != null) coupon.setCode(dto.getCode().trim().toUpperCase());
        if (dto.getDiscountType() != null) coupon.setDiscountType(dto.getDiscountType());
        if (dto.getDiscountValue() != null) coupon.setDiscountValue(dto.getDiscountValue());
        if (dto.getMinOrderValue() != null) coupon.setMinOrderValue(dto.getMinOrderValue());
        if (dto.getValidFrom() != null) coupon.setValidFrom(dto.getValidFrom());
        if (dto.getValidUntil() != null) coupon.setValidUntil(dto.getValidUntil());
        if (dto.getIsActive() != null) coupon.setIsActive(dto.getIsActive());

        return mapToDto(couponRepository.save(coupon));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCoupon(Long id) throws Exception {
        Coupon coupon = couponRepository.findById(id).orElseThrow(() -> new Exception("Coupon not found"));
        couponRepository.delete(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponDTO getCouponById(Long id) throws Exception {
        return mapToDto(couponRepository.findById(id).orElseThrow(() -> new Exception("Coupon not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CouponDTO> getCouponsByStoreId(Long storeId) throws Exception {
        return couponRepository.findByStoreId(storeId).stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CouponDTO getCouponByCode(String code, Long storeId) throws Exception {
        Coupon coupon = couponRepository.findByCodeAndStoreId(code.trim().toUpperCase(), storeId)
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

        return mapToDto(coupon);
    }
}
