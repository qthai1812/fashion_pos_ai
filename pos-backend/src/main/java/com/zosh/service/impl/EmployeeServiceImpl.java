package com.zosh.service.impl;

import com.zosh.domain.UserRole;
import com.zosh.mapper.UserMapper;
import com.zosh.modal.*;
import com.zosh.payload.dto.UserDto;
import com.zosh.repository.*;
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
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OrderRepository orderRepository;
    private final ShiftReportRepository shiftReportRepository;
    private final RefundRepository refundRepository;

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

    private void clearPreviousManagedBranches(Long userId, Long keepBranchId) {
        List<Branch> managed = branchRepository.findByManagerId(userId);
        for (Branch b : managed) {
            if (keepBranchId == null || !b.getId().equals(keepBranchId)) {
                b.setManager(null);
                branchRepository.save(b);
            }
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
            if (branch.getStore() != null && !branch.getStore().getId().equals(store.getId())) {
                throw new Exception("Branch does not belong to this store");
            }
        }

        User user = UserMapper.toEntity(employee);
        user.setStore(store);
        user.setBranch(branch);
        user.setPassword(passwordEncoder.encode(employee.getPassword()));

        User savedEmployee = userRepository.save(user);
        if (employee.getRole() == UserRole.ROLE_BRANCH_MANAGER && branch != null) {
            clearPreviousManagedBranches(savedEmployee.getId(), branch.getId());
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
                clearPreviousManagedBranches(savedEmployee.getId(), branch.getId());
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
        if (employeeDetails.getLoginAccess() != null) {
            User currentUser = userService.getCurrentUser();
            if (Boolean.FALSE.equals(employeeDetails.getLoginAccess())) {
                if (currentUser != null && currentUser.getId().equals(existingEmployee.getId())) {
                    throw new Exception("You cannot disable your own login access");
                }
                if (currentUser != null
                        && currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER
                        && existingEmployee.getRole() != UserRole.ROLE_BRANCH_CASHIER) {
                    throw new Exception("Branch Manager can only disable login access for Branch Cashiers");
                }
            }
            existingEmployee.setLoginAccess(employeeDetails.getLoginAccess());
        }
        existingEmployee.setBranch(branch);

        User saved = userRepository.save(existingEmployee);
        if (saved.getRole() == UserRole.ROLE_BRANCH_MANAGER && branch != null) {
            clearPreviousManagedBranches(saved.getId(), branch.getId());
            branch.setManager(saved);
            branchRepository.save(branch);
        } else {
            clearPreviousManagedBranches(saved.getId(), null);
        }
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEmployee(Long employeeId) throws Exception {
        User employee = userRepository.findById(employeeId).orElseThrow(
                () -> new Exception("employee not found")
        );
        User currentUser = userService.getCurrentUser();
        if (currentUser != null && currentUser.getId().equals(employee.getId())) {
            throw new Exception("You cannot delete your own account");
        }
        if (employee.getBranch() != null) {
            validateBranchPermission(employee.getBranch());
        } else if (employee.getStore() != null) {
            validateStorePermission(employee.getStore());
        }

        // Gỡ liên kết manager trong bảng Branch trước khi xóa để tránh lỗi khóa ngoại
        clearPreviousManagedBranches(employee.getId(), null);

        // Xóa PasswordResetToken nếu có
        passwordResetTokenRepository.deleteByUserId(employee.getId());

        // Gỡ liên kết cashier trên Order, ShiftReport, Refund để tránh lỗi khóa ngoại
        List<Order> cashierOrders = orderRepository.findByCashierId(employee.getId());
        for (Order o : cashierOrders) {
            o.setCashier(null);
            orderRepository.save(o);
        }

        List<ShiftReport> cashierShifts = shiftReportRepository.findByCashierId(employee.getId());
        for (ShiftReport sr : cashierShifts) {
            sr.setCashier(null);
            shiftReportRepository.save(sr);
        }

        List<Refund> cashierRefunds = refundRepository.findByCashierId(employee.getId());
        for (Refund r : cashierRefunds) {
            r.setCashier(null);
            refundRepository.save(r);
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
