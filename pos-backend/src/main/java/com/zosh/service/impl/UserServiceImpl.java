package com.zosh.service.impl;

import com.zosh.configuration.JwtProvider;
import com.zosh.domain.StoreStatus;
import com.zosh.domain.UserRole;
import com.zosh.exceptions.UserException;
import com.zosh.modal.Store;
import com.zosh.modal.User;
import com.zosh.payload.dto.UserDto;
import com.zosh.repository.UserRepository;
import com.zosh.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User getUserFromJwtToken(String token) throws UserException {
        String email = jwtProvider.getEmailFromToken(token);
        if (email == null) {
            throw new UserException("Invalid token");
        }
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UserException("Invalid token");
        }
        return user;
    }

    @Override
    public User getCurrentUser() throws UserException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UserException("user not found");
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UserException("user not found");
        }
        // JWT có hiệu lực 24h: phải kiểm tra lại trạng thái tài khoản/cửa hàng ở mỗi request
        // để việc khóa tài khoản hoặc BLOCK cửa hàng có hiệu lực ngay lập tức.
        if (Boolean.FALSE.equals(user.getLoginAccess())) {
            throw new UserException("Login access for this account has been disabled by your manager.");
        }
        if (user.getRole() != UserRole.ROLE_ADMIN) {
            Store effectiveStore = user.getStore();
            if (effectiveStore == null && user.getBranch() != null) {
                effectiveStore = user.getBranch().getStore();
            }
            if (effectiveStore != null && effectiveStore.getStatus() == StoreStatus.BLOCKED) {
                throw new UserException("Store \"" + effectiveStore.getBrand()
                        + "\" has been BLOCKED by Super Admin. Access is suspended for all accounts in this store.");
            }
        }
        return user;
    }

    @Override
    public User getUserByEmail(String email) throws UserException {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new UserException("user not found");
        }
        return user;
    }

    @Override
    public User getUserById(long id) throws UserException, Exception {
        return userRepository.findById(id).orElseThrow(
                () -> new Exception("user not found")
        );
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(UserDto userDto) throws UserException {
        User currentUser = getCurrentUser();
        if (userDto.getFullName() != null && !userDto.getFullName().trim().isEmpty()) {
            currentUser.setFullName(userDto.getFullName().trim());
        }
        if (userDto.getPhone() != null) {
            currentUser.setPhone(userDto.getPhone().trim());
        }
        return userRepository.save(currentUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(String currentPassword, String newPassword) throws UserException {
        User currentUser = getCurrentUser();
        if (currentPassword == null || currentPassword.trim().isEmpty()) {
            throw new UserException("Current password is required");
        }
        if (!passwordEncoder.matches(currentPassword, currentUser.getPassword())) {
            throw new UserException("Current password does not match");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new UserException("New password must be at least 6 characters");
        }
        currentUser.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentUser);
    }
}
