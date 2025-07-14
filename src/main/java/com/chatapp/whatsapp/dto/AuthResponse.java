package com.chatapp.whatsapp.dto;

import lombok.*;

// Authentication Response DTO
@NoArgsConstructor
@Data
@Setter
@Getter
@AllArgsConstructor
@ToString
@Builder
public class AuthResponse {
    // Getters and Setters
    private String message;
    private UserResponse user;
    private String token; // For future JWT implementation

    public AuthResponse(String userRegisteredSuccessfully, UserResponse user) {
        userRegisteredSuccessfully = "User registered successfully";
        this.message = userRegisteredSuccessfully;
        this.user = user;

    }
}
