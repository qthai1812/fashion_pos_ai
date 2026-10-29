package com.zosh.service.impl;

import com.zosh.domain.StoreStatus;
import com.zosh.domain.UserRole;
import com.zosh.exceptions.UserException;
import com.zosh.mapper.StoreMapper;
import com.zosh.modal.Store;
import com.zosh.modal.StoreContact;
import com.zosh.modal.User;
import com.zosh.payload.dto.StoreDTO;
import com.zosh.repository.StoreRepository;
import com.zosh.repository.UserRepository;
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
        Store existing = getStoreByAdmin();
        if (existing == null && id != null && currentUser.getRole() == UserRole.ROLE_ADMIN) {
            existing = storeRepository.findById(id).orElse(null);
        }
        if (existing == null) {
            throw new Exception("store not found");
        }
        if (id != null && !existing.getId().equals(id) && currentUser.getRole() != UserRole.ROLE_ADMIN) {
            throw new Exception("You do not have permission to update this store");
        }

        existing.setBrand(storeDTO.getBrand());
        existing.setDescription(storeDTO.getDescription());
        if (storeDTO.getStoreType() != null) {
            existing.setStoreType(storeDTO.getStoreType());
        }
        if (storeDTO.getContact() != null) {
            StoreContact contact = StoreContact.builder()
                    .address(storeDTO.getContact().getAddress())
                    .phone(storeDTO.getContact().getPhone())
                    .email(storeDTO.getContact().getEmail())
                    .build();
            existing.setContact(contact);
        }
        Store updatedStore = storeRepository.save(existing);
        return StoreMapper.toDTO(updatedStore);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStore(Long id) throws UserException {
        Store store = getStoreByAdmin();
        if (store != null) {
            storeRepository.delete(store);
        }
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