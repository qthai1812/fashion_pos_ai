package com.zosh.service.impl;

import com.zosh.domain.StoreStatus;
import com.zosh.domain.UserRole;
import com.zosh.exceptions.UserException;
import com.zosh.mapper.StoreMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.StoreDTO;
import com.zosh.repository.*;
import com.zosh.service.StoreService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CouponRepository couponRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final RefundRepository refundRepository;
    private final ShiftReportRepository shiftReportRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreDTO createStore(StoreDTO storeDTO, User user) {
        Store existing = storeRepository.findByStoreAdminId(user.getId());
        if (existing != null) {
            return StoreMapper.toDTO(existing);
        }
        Store store = StoreMapper.toEntity(storeDTO, user);
        Store savedStore = storeRepository.save(store);

        // Liên kết ngược Store vào User để phân quyền đồng nhất ở mọi luồng
        user.setStore(savedStore);
        userRepository.save(user);

        return StoreMapper.toDTO(savedStore);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDTO getStoreById(Long id) throws Exception {
        Store store = storeRepository.findById(id).orElseThrow(
                () -> new Exception("store not found...")
        );
        return StoreMapper.toDTO(store);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoreDTO> getAllStores(StoreStatus status) {
        List<Store> stores = (status != null)
                ? storeRepository.findByStatus(status)
                : storeRepository.findAll();
        return stores.stream().map(StoreMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Store getStoreByAdmin() throws UserException {
        User currentUser = userService.getCurrentUser();
        Store store = storeRepository.findByStoreAdminId(currentUser.getId());
        if (store == null && currentUser.getStore() != null) {
            store = currentUser.getStore();
        }
        return store;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreDTO updateStore(Long id, StoreDTO storeDTO) throws Exception {
        User currentUser = userService.getCurrentUser();
        Store existing = null;

        if (currentUser.getRole() == UserRole.ROLE_ADMIN && id != null) {
            existing = storeRepository.findById(id).orElse(null);
        }
        if (existing == null) {
            existing = getStoreByAdmin();
        }
        if (existing == null && id != null) {
            Store candidate = storeRepository.findById(id).orElse(null);
            if (candidate != null && currentUser.getStore() != null
                    && candidate.getId().equals(currentUser.getStore().getId())
                    && (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN
                    || currentUser.getRole() == UserRole.ROLE_STORE_MANAGER)) {
                existing = candidate;
            }
        }
        if (existing == null) {
            throw new Exception("store not found");
        }
        if (id != null && !existing.getId().equals(id) && currentUser.getRole() != UserRole.ROLE_ADMIN) {
            throw new Exception("You do not have permission to update this store");
        }

        if (storeDTO.getBrand() != null) {
            existing.setBrand(storeDTO.getBrand());
        }
        if (storeDTO.getDescription() != null) {
            existing.setDescription(storeDTO.getDescription());
        }
        if (storeDTO.getStoreType() != null) {
            existing.setStoreType(storeDTO.getStoreType());
        }
        if (storeDTO.getCommissionRate() != null) {
            existing.setCommissionRate(storeDTO.getCommissionRate());
        }
        if (storeDTO.getStatus() != null && currentUser.getRole() == UserRole.ROLE_ADMIN) {
            existing.setStatus(storeDTO.getStatus());
        }
        if (storeDTO.getContact() != null) {
            StoreContact currentContact = existing.getContact() != null ? existing.getContact() : new StoreContact();
            StoreContact contact = StoreContact.builder()
                    .address(storeDTO.getContact().getAddress() != null ? storeDTO.getContact().getAddress() : currentContact.getAddress())
                    .phone(storeDTO.getContact().getPhone() != null ? storeDTO.getContact().getPhone() : currentContact.getPhone())
                    .email(storeDTO.getContact().getEmail() != null ? storeDTO.getContact().getEmail() : currentContact.getEmail())
                    .build();
            existing.setContact(contact);
        }
        Store updatedStore = storeRepository.save(existing);
        return StoreMapper.toDTO(updatedStore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStore(Long id) throws UserException {
        User currentUser = userService.getCurrentUser();
        Store store;
        if (id != null) {
            store = storeRepository.findById(id).orElseThrow(
                    () -> new UserException("Store not found with id: " + id)
            );
            boolean isOwner = store.getStoreAdmin() != null && store.getStoreAdmin().getId().equals(currentUser.getId());
            if (currentUser.getRole() != UserRole.ROLE_ADMIN && !isOwner) {
                throw new UserException("You do not have permission to delete this store");
            }
        } else {
            store = getStoreByAdmin();
            if (store == null) {
                throw new UserException("Store not found");
            }
        }

        Long storeId = store.getId();

        // 1. Unlink users referencing this store or its branches
        List<User> storeUsers = userRepository.findByStore(store);
        for (User u : storeUsers) {
            u.setStore(null);
            u.setBranch(null);
            userRepository.save(u);
        }
        if (store.getStoreAdmin() != null) {
            User admin = store.getStoreAdmin();
            admin.setStore(null);
            admin.setBranch(null);
            userRepository.save(admin);
            store.setStoreAdmin(null);
            storeRepository.save(store);
        }

        // 2. Clean up branches and their dependent records
        List<Branch> branches = branchRepository.findByStoreId(storeId);
        for (Branch branch : branches) {
            Long branchId = branch.getId();
            List<User> branchUsers = userRepository.findByBranchId(branchId);
            for (User bu : branchUsers) {
                bu.setBranch(null);
                userRepository.save(bu);
            }
            branch.setManager(null);
            branchRepository.save(branch);

            List<Refund> branchRefunds = refundRepository.findByBranchId(branchId);
            if (!branchRefunds.isEmpty()) {
                refundRepository.deleteAll(branchRefunds);
            }

            List<ShiftReport> branchShifts = shiftReportRepository.findByBranchId(branchId);
            for (ShiftReport sr : branchShifts) {
                List<Refund> srRefunds = refundRepository.findByShiftReportId(sr.getId());
                if (!srRefunds.isEmpty()) {
                    refundRepository.deleteAll(srRefunds);
                }
            }
            if (!branchShifts.isEmpty()) {
                shiftReportRepository.deleteAll(branchShifts);
            }

            List<Order> branchOrders = orderRepository.findByBranchId(branchId);
            for (Order order : branchOrders) {
                List<Refund> orderRefunds = refundRepository.findByOrderId(order.getId());
                if (!orderRefunds.isEmpty()) {
                    refundRepository.deleteAll(orderRefunds);
                }
            }
            if (!branchOrders.isEmpty()) {
                orderRepository.deleteAll(branchOrders);
            }

            List<Inventory> branchInventories = inventoryRepository.findByBranchId(branchId);
            if (!branchInventories.isEmpty()) {
                inventoryRepository.deleteAll(branchInventories);
            }

            branchRepository.delete(branch);
        }

        // 3. Clean up products and categories
        List<Product> products = productRepository.findByStoreId(storeId);
        for (Product product : products) {
            List<Inventory> prodInv = inventoryRepository.findByProductId(product.getId());
            if (!prodInv.isEmpty()) {
                inventoryRepository.deleteAll(prodInv);
            }
            List<OrderItem> orderItems = orderItemRepository.findByProductId(product.getId());
            for (OrderItem item : orderItems) {
                item.setProduct(null);
                orderItemRepository.save(item);
            }
            productRepository.delete(product);
        }

        List<Category> categories = categoryRepository.findByStoreId(storeId);
        if (!categories.isEmpty()) {
            categoryRepository.deleteAll(categories);
        }

        // 4. Clean up coupons and subscriptions
        List<Coupon> coupons = couponRepository.findByStoreId(storeId);
        if (!coupons.isEmpty()) {
            couponRepository.deleteAll(coupons);
        }

        List<Subscription> subscriptions = subscriptionRepository.findByStoreId(storeId);
        if (!subscriptions.isEmpty()) {
            subscriptionRepository.deleteAll(subscriptions);
        }

        storeRepository.delete(store);
    }

    @Override
    @Transactional(readOnly = true)
    public StoreDTO getStoreByEmployee() throws UserException {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new UserException("you don't have permission to access this store");
        }
        if (currentUser.getStore() != null) {
            return StoreMapper.toDTO(currentUser.getStore());
        }
        if (currentUser.getBranch() != null && currentUser.getBranch().getStore() != null) {
            return StoreMapper.toDTO(currentUser.getBranch().getStore());
        }
        Store adminStore = storeRepository.findByStoreAdminId(currentUser.getId());
        return StoreMapper.toDTO(adminStore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreDTO moderateStore(Long id, StoreStatus status) throws Exception {
        Store store = storeRepository.findById(id).orElseThrow(
                () -> new Exception("store not found...")
        );
        store.setStatus(status);
        Store updatedStore = storeRepository.save(store);
        return StoreMapper.toDTO(updatedStore);
    }
}
