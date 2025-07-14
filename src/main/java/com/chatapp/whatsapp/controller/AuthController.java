package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.AuthResponse;
import com.chatapp.whatsapp.dto.LoginRequest;
import com.chatapp.whatsapp.dto.SignUpRequest;
import com.chatapp.whatsapp.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private com.chatapp.whatsapp.service.AuthService authService;

    @PostMapping(value = "/signup", consumes = {"multipart/form-data"})
    public ResponseEntity<?> signUp(
            @Valid @RequestPart(value = "user", required = true) SignUpRequest request,
            @RequestParam(value = "userPhoto", required = false) MultipartFile userPhoto) {

        try {
            UserResponse user = authService.signUp(request, userPhoto);
            AuthResponse response = new AuthResponse("User registered successfully", user);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("File upload failed: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping(value = "/signup", consumes = {"application/octet-stream"})
    public ResponseEntity<?> signUpOctetStream(
            @Valid @RequestBody SignUpRequest request) {

        try {
            // For application/octet-stream, we don't support file upload
            UserResponse user = authService.signUp(request, null);
            AuthResponse response = new AuthResponse("User registered successfully", user);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Registration failed: " + e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            UserResponse user = authService.login(request);
            AuthResponse response = new AuthResponse("Login successful", user);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        try {
            UserResponse user = authService.getUserById(id);
            return ResponseEntity.ok(user);

        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    // Error response class
    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
