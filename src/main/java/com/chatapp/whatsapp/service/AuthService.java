package com.chatapp.whatsapp.service;

import com.chatapp.whatsapp.dto.SignUpRequest;
import com.chatapp.whatsapp.dto.LoginRequest;
import com.chatapp.whatsapp.dto.UserResponse;
import com.chatapp.whatsapp.entity.User;

import com.chatapp.whatsapp.respository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileUploadService fileUploadService;

    // Sign up user
    public UserResponse signUp(SignUpRequest request, MultipartFile userPhoto) throws IOException {
        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Handle user photo upload
        String photoPath = null;
        if (userPhoto != null && !userPhoto.isEmpty()) {
            // Validate image file
            if (!fileUploadService.isValidImageFile(userPhoto)) {
                throw new RuntimeException("Invalid image file format");
            }

            // Check file size (max 10MB)
            if (!fileUploadService.isValidFileSize(userPhoto, 10 * 1024 * 1024)) {
                throw new RuntimeException("File size exceeds 10MB limit");
            }

            // Upload file
            photoPath = fileUploadService.uploadFile(userPhoto, "profile-pictures");
        }

        // Create new user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword()); // In production, hash this password
        user.setEmail(request.getEmail());
        user.setUserPhoto(photoPath);
        user.setIsActive(true);

        // Save user
        User savedUser = userRepository.save(user);

        // Return user response
        return mapToUserResponse(savedUser);
    }

    // Login user
    public UserResponse login(LoginRequest request) {
        // Find user by username or email
        Optional<User> userOptional = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail());

        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        User user = userOptional.get();

        // Check if user is active
        if (!user.getIsActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        // Check password (in production, use password encoder)
        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        // Return user response
        return mapToUserResponse(user);
    }

    // Get user by ID
    public UserResponse getUserById(Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isEmpty()) {
            throw new RuntimeException("User not found");
        }

        return mapToUserResponse(userOptional.get());
    }

    // Helper method to map User entity to UserResponse DTO
    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setUserPhoto(user.getUserPhoto());
        response.setIsActive(user.getIsActive());
        response.setCreatedAt(user.getCreatedAt().toString());

        return response;
    }
}