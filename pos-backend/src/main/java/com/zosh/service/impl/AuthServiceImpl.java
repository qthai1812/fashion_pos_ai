package com.zosh.service.impl;

import com.zosh.configuration.JwtProvider;
import com.zosh.domain.UserRole;
import com.zosh.exceptions.UserException;
import com.zosh.mapper.UserMapper;
import com.zosh.modal.PasswordResetToken;
import com.zosh.modal.User;
import com.zosh.payload.dto.UserDto;
import com.zosh.payload.response.ApiResponse;
import com.zosh.payload.response.AuthResponse;
import com.zosh.repository.PasswordResetTokenRepository;
import com.zosh.repository.UserRepository;
import com.zosh.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CustomUserImplementation customUserImplementation;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JavaMailSender mailSender;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse signup(UserDto userDto) throws UserException {
        User user = userRepository.findByEmail(userDto.getEmail());
        if (user != null) {
            throw new UserException("email id already registered");
        }

        // Chặn leo thang đặc quyền: không cho phép tự đăng ký ROLE_ADMIN qua endpoint công khai
        UserRole assignedRole = userDto.getRole() != null ? userDto.getRole() : UserRole.ROLE_STORE_ADMIN;
        if (assignedRole == UserRole.ROLE_ADMIN) {
            throw new UserException("Cannot self-register with ROLE_ADMIN privilege");
        }

        User newUser = new User();
        newUser.setEmail(userDto.getEmail());
        newUser.setPassword(passwordEncoder.encode(userDto.getPassword()));
        newUser.setRole(assignedRole);
        newUser.setFullName(userDto.getFullName());
        newUser.setPhone(userDto.getPhone());
        newUser.setLastLogin(LocalDateTime.now());
        newUser.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(newUser);

        UserDetails userDetails = customUserImplementation.loadUserByUsername(savedUser.getEmail());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtProvider.generateToken(authentication);

        AuthResponse authResponse = new AuthResponse();
        authResponse.setJwt(jwt);
        authResponse.setMessage("Registered Successfully");
        authResponse.setUser(UserMapper.toDTO(savedUser));

        return authResponse;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResponse login(UserDto userDto) throws UserException {
        String email = userDto.getEmail();
        String password = userDto.getPassword();
        Authentication authentication = authenticate(email, password);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtProvider.generateToken(authentication);
        User user = userRepository.findByEmail(email);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        AuthResponse authResponse = new AuthResponse();
        authResponse.setJwt(jwt);
        authResponse.setMessage("Login Successfully");
        authResponse.setUser(UserMapper.toDTO(user));

        return authResponse;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse forgotPassword(String email) throws Exception {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new Exception("No account found with email: " + email);
        }

        passwordResetTokenRepository.deleteByUserId(user.getId());

        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();
        passwordResetTokenRepository.save(resetToken);

        String resetLink = frontendUrl + "/auth/reset-password?token=" + tokenValue;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("POS Pro — Password Reset Request");
        message.setText(
                "Hello " + user.getFullName() + ",\n\n" +
                "We received a request to reset your password.\n\n" +
                "Click the link below to reset it (valid for 1 hour):\n" +
                resetLink + "\n\n" +
                "If you did not request a password reset, please ignore this email.\n\n" +
                "Best regards,\nPOS Pro Team"
        );
        mailSender.send(message);

        ApiResponse response = new ApiResponse();
        response.setMessage("Password reset link sent to " + email);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse resetPassword(String token, String newPassword) throws Exception {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new Exception("Invalid or expired reset token"));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new Exception("Reset token has expired. Please request a new one.");
        }
        if (resetToken.isUsed()) {
            throw new Exception("This reset token has already been used.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        ApiResponse response = new ApiResponse();
        response.setMessage("Password reset successfully. You can now log in.");
        return response;
    }

    private Authentication authenticate(String email, String password) throws UserException {
        UserDetails userDetails = customUserImplementation.loadUserByUsername(email);
        if (userDetails == null) {
            throw new UserException("email id doesn't exist " + email);
        }
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new UserException("password doesn't match");
        }
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
