package com.zosh.service.impl;

import com.zosh.domain.UserRole;
import com.zosh.exceptions.UserException;
import com.zosh.mapper.BranchMapper;
import com.zosh.modal.Branch;
import com.zosh.modal.Store;
import com.zosh.modal.User;
import com.zosh.payload.dto.BranchDTO;
import com.zosh.repository.BranchRepository;
import com.zosh.repository.StoreRepository;
import com.zosh.service.BranchService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;

    private void validateBranchAccess(User user, Branch branch) throws Exception {
        if (user == null || branch == null || branch.getStore() == null) {
            throw new Exception("You do not have permission to manage this branch");
        }
        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return;
        }
        Store store = branch.getStore();
        boolean isStoreOwner = store.getStoreAdmin() != null && store.getStoreAdmin().getId().equals(user.getId());
        boolean isSameStoreManager = user.getStore() != null
                && user.getStore().getId().equals(store.getId())
                && (user.getRole() == UserRole.ROLE_STORE_ADMIN || user.getRole() == UserRole.ROLE_STORE_MANAGER);
        boolean isThisBranchManager = user.getBranch() != null
                && user.getBranch().getId().equals(branch.getId())
                && user.getRole() == UserRole.ROLE_BRANCH_MANAGER;

        if (!isStoreOwner && !isSameStoreManager && !isThisBranchManager) {
            throw new Exception("You do not have permission to manage this branch");
        }
    }

    @Override
    public BranchDTO createBranch(BranchDTO branchDTO) throws UserException {
        User currentUser = userService.getCurrentUser();
        Store store = storeRepository.findByStoreAdminId(currentUser.getId());
        if (store == null && currentUser.getStore() != null) {
            store = currentUser.getStore();
        }
        Branch branch = BranchMapper.toEntity(branchDTO, store);
        Branch savedBranch = branchRepository.save(branch);
        return BranchMapper.toDTO(savedBranch);
    }

    @Override
    public BranchDTO updateBranch(Long id, BranchDTO branchDTO) throws Exception {
        Branch existing = branchRepository.findById(id).orElseThrow(
                () -> new Exception("branch not exist...")
        );
        validateBranchAccess(userService.getCurrentUser(), existing);

        existing.setName(branchDTO.getName());
        existing.setWorkingDays(branchDTO.getWorkingDays());
        existing.setEmail(branchDTO.getEmail());
        existing.setPhone(branchDTO.getPhone());
        existing.setAddress(branchDTO.getAddress());
        existing.setOpenTime(branchDTO.getOpenTime());
        existing.setCloseTime(branchDTO.getCloseTime());
        existing.setUpdatedAt(LocalDateTime.now());

        Branch updatedBranch = branchRepository.save(existing);
        return BranchMapper.toDTO(updatedBranch);
    }

    @Override
    public void deleteBranch(Long id) throws Exception {
        Branch existing = branchRepository.findById(id).orElseThrow(
                () -> new Exception("branch not exist...")
        );
        validateBranchAccess(userService.getCurrentUser(), existing);
        branchRepository.delete(existing);
    }

    @Override
    public List<BranchDTO> getAllBranchesByStoreId(Long storeId) {
        List<Branch> branches = branchRepository.findByStoreId(storeId);
        return branches.stream().map(BranchMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public BranchDTO getBranchById(Long id) throws Exception {
        Branch existing = branchRepository.findById(id).orElseThrow(
                () -> new Exception("branch not exist...")
        );
        return BranchMapper.toDTO(existing);
    }
}