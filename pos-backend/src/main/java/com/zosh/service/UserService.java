package com.zosh.service;

import com.zosh.exceptions.UserException;
import com.zosh.modal.User;
import com.zosh.payload.dto.UserDto;

import java.util.List;

public interface UserService {

    User getUserFromJwtToken(String token) throws UserException;

    User getCurrentUser() throws UserException;

    User getUserByEmail(String email) throws UserException;

    User getUserById(long id) throws UserException, Exception;

    List<User> getAllUsers();

    User updateProfile(UserDto userDto) throws UserException;

    void changePassword(String currentPassword, String newPassword) throws UserException;
}