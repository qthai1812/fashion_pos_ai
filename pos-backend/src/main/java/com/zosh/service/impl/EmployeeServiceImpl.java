package com.zosh.service.impl;

import com.zosh.domain.UserRole;
import com.zosh.mapper.UserMapper;
import com.zosh.modal.Branch;
import com.zosh.modal.Store;
import com.zosh.modal.User;
import com.zosh.payload.dto.UserDto;
import com.zosh.repository.BranchRepository;
import com.zosh.repository.StoreRepository;
import com.zosh.repository.UserRepository;
import com.zosh.service.EmployeeService;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;

    private void validateStorePermission(Store store) throws Exception {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) return;

        boolean isOwner = store.getStoreAdmin() != null && store.getStoreAdmin().getId().equals(currentUser.getId());
        boolean isSameStoreManager = currentUser.getStore() != null
                && currentUser.getStore().getId().equals(store.getId())
                && (currentUser.getRole() == UserRole.ROLE_STORE_ADMIN || currentUser.getRole() == UserRole.ROLE_STORE_MANAGER);

        if (!isOwner && !isSameStoreManager) {
            throw new Exception("You do not have permission to manage employees in this store");
        }
    }

    private void validateBranchPermission(Branch branch) throws Exception {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() == UserRole.ROLE_ADMIN) return;

        boolean isBranchManager = currentUser.getBranch() != null
                && currentUser.getBranch().getId().equals(branch.getId())
                && currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER;

        if (!isBranchManager) {
            validateStorePermission(branch.getStore());
        }
    }

    private void checkNotAdminRole(UserRole role) throws Exception {
        if (role == UserRole.ROLE_ADMIN) {
            throw new Exception("Cannot assign ROLE_ADMIN privilege to an employee");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto createStoreEmployee(UserDto employee, Long storeId) throws Exception {
        Store store = storeRepository.findById(storeId).orElseThrow(
                () -> new Exception("Store not found")
        );
        validateStorePermission(store);
        checkNotAdminRole(employee.getRole());

        if (userRepository.findByEmail(employee.getEmail()) != null) {
            throw new Exception("Email already registered: " + employee.getEmail());
        }

        Branch branch = null;
        if (employee.getRole() == UserRole.ROLE_BRANCH_MANAGER || employee.getBranchId() != null) {
            if (employee.getBranchId() == null) {
                throw new Exception("branch id is required to create branch manager");
            }
            branch = branchRepository.findById(employee.getBranchId()).orElseThrow(
                    () -> new Exception("branch not found")
            );
            if (!branch.getStore().getId().equals(store.getId())) {
                throw new Exception("Branch does not belong to this store");
            }
        }

        User user = UserMapper.toEntity(employee);
        user.setStore(store);
        user.setBranch(branch);
        user.setPassword(passwordEncoder.encode(employee.getPassword()));

        User savedEmployee = userRepository.save(user);
        if (employee.getRole() == UserRole.ROLE_BRANCH_MANAGER && branch != null) {
            branch.setManager(savedEmployee);
            branchRepository.save(branch);
        }
        return UserMapper.toDTO(savedEmployee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDto createBranchEmployee(UserDto employee, Long branchId) throws Exception {
        Branch branch = branchRepository.findById(branchId).orElseThrow(
                () -> new Exception("branch not found")
        );
        validateBranchPermission(branch);
        checkNotAdminRole(employee.getRole());

        if (userRepository.findByEmail(employee.getEmail()) != null) {
            throw new Exception("Email already registered: " + employee.getEmail());
        }

        if (employee.getRole() == UserRole.ROLE_BRANCH_CASHIER ||
                employee.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
            User user = UserMapper.toEntity(employee);
            user.setBranch(branch);
            user.setStore(branch.getStore());
            user.setPassword(passwordEncoder.encode(employee.getPassword()));
            User savedEmployee = userRepository.save(user);

            if (employee.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
                branch.setManager(savedEmployee);
                branchRepository.save(branch);
            }
            return UserMapper.toDTO(savedEmployee);
        }
        throw new Exception("branch role not supported");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateEmployee(Long employeeId, UserDto employeeDetails) throws Exception {
        User existingEmployee = userRepository.findById(employeeId).orElseThrow(
                () -> new Exception("employee doesn't exist with given id")
        );
        if (existingEmployee.getBranch() != null) {
            validateBranchPermission(existingEmployee.getBranch());
        } else if (existingEmployee.getStore() != null) {
            validateStorePermission(existingEmployee.getStore());
        }

        if (employeeDetails.getRole() != null) {
            checkNotAdminRole(employeeDetails.getRole());
        }

        if (employeeDetails.getEmail() != null && !employeeDetails.getEmail().equalsIgnoreCase(existingEmployee.getEmail())) {
            User duplicate = userRepository.findByEmail(employeeDetails.getEmail());
            if (duplicate != null && !duplicate.getId().equals(existingEmployee.getId())) {
                throw new Exception("Email already in use by another account");
            }
            existingEmployee.setEmail(employeeDetails.getEmail());
        }

        Branch branch = existingEmployee.getBranch();
        if (employeeDetails.getBranchId() != null) {
            branch = branchRepository.findById(employeeDetails.getBranchId()).orElseThrow(
                    () -> new Exception("branch not found")
            );
        }

        if (employeeDetails.getFullName() != null) {
            existingEmployee.setFullName(employeeDetails.getFullName());
        }
        if (employeeDetails.getPhone() != null) {
            existingEmployee.setPhone(employeeDetails.getPhone());
        }
        if (employeeDetails.getPassword() != null && !employeeDetails.getPassword().isEmpty()) {
            existingEmployee.setPassword(passwordEncoder.encode(employeeDetails.getPassword()));
        }
        if (employeeDetails.getRole() != null) {
            existingEmployee.setRole(employeeDetails.getRole());
        }
        existingEmployee.setBranch(branch);

        User saved = userRepository.save(existingEmployee);
        if (saved.getRole() == UserRole.ROLE_BRANCH_MANAGER && branch != null) {
            branch.setManager(saved);
            branchRepository.save(branch);
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmployee(Long employeeId) throws Exception {
        User employee = userRepository.findById(employeeId).orElseThrow(
                () -> new Exception("employee not found")
        );
        if (employee.getBranch() != null) {
            validateBranchPermission(employee.getBranch());
        } else if (employee.getStore() != null) {
            validateStorePermission(employee.getStore());
        }

        // Gỡ liên kết manager trong bảng Branch trước khi xóa để tránh lỗi khóa ngoại
        List<Branch> managedBranches = branchRepository.findByManagerId(employee.getId());
        for (Branch b : managedBranches) {
            b.setManager(null);
            branchRepository.save(b);
        }

        userRepository.delete(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto findEmployeeById(Long employeeId) throws Exception {
        User employee = userRepository.findById(employeeId).orElseThrow(
                () -> new Exception("employee not found with id " + employeeId)
        );
        return UserMapper.toDTO(employee);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findStoreEmployees(Long storeId, UserRole role) throws Exception {
        Store store = storeRepository.findById(storeId).orElseThrow(
                () -> new Exception("Store not found")
        );
        return userRepository.findByStore(store).stream()
                .filter(user -> role == null || user.getRole() == role)
                .map(UserMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> findBranchEmployees(Long branchId, UserRole role) throws Exception {
        branchRepository.findById(branchId).orElseThrow(
                () -> new Exception("branch not found")
        );
        return userRepository.findByBranchId(branchId).stream()
                .filter(user -> role == null || user.getRole() == role)
                .map(UserMapper::toDTO)
                .collect(Collectors.toList());
    }
}