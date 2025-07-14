package com.chatapp.whatsapp.controller;

import com.chatapp.whatsapp.dto.UserResponse;
import com.chatapp.whatsapp.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserSearchController {

    private final AuthService authService;

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(
            @RequestParam("username") String username,
            Pageable pageable) {

        try {
            List<UserResponse> users = authService.searchUsersByUsername(username, pageable);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/search/exact")
    public ResponseEntity<UserResponse> findUserByExactUsername(
            @RequestParam("username") String username) {

        try {
            UserResponse user = authService.findByExactUsername(username);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}