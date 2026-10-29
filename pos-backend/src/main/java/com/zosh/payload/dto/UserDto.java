package com.zosh.payload.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zosh.domain.UserRole;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDto {

    private Long id;

    private String fullName;

    @Email(message = "Email should be valid")
    private String email;

    private String phone;

    private UserRole role;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private Long branchId;
    private Long storeId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLogin;
}
