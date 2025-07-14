package com.chatapp.whatsapp.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Builder
@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(unique = true, nullable = false)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Column(nullable = false)
    private String password;

    @Email(message = "Please provide a valid email")
    @NotBlank(message = "Email is required")
    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "user_photo")
    private String userPhoto; // Store file path or URL



    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    // Add these fields to your existing User entity
    @Column(name = "display_name")
    private String displayName;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "bio")
    private String bio;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_online")
    @Builder.Default
    private Boolean isOnline = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "status")
    @Builder.Default
    private String status = "OFFLINE"; // ONLINE, OFFLINE, AWAY, BUSY

    @Column(name = "privacy_settings")
    @Builder.Default
    private String privacySettings = "PUBLIC"; // PUBLIC, FRIENDS, PRIVATE

    @Column(name = "notification_enabled")
    @Builder.Default
    private Boolean notificationEnabled = true;

    @Column(name = "two_factor_enabled")
    @Builder.Default
    private Boolean twoFactorEnabled = false;

    // Helper methods for User entity
    public void setOnline() {
        this.isOnline = true;
        this.status = "ONLINE";
        this.lastSeen = LocalDateTime.now();
    }

    public void setOffline() {
        this.isOnline = false;
        this.status = "OFFLINE";
        this.lastSeen = LocalDateTime.now();
    }

    public void setAway() {
        this.status = "AWAY";
        this.lastSeen = LocalDateTime.now();
    }

    public void setBusy() {
        this.status = "BUSY";
        this.lastSeen = LocalDateTime.now();
    }

    public boolean isCurrentlyOnline() {
        return Boolean.TRUE.equals(isOnline);
    }

    public boolean isCurrentlyActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public String getDisplayNameOrUsername() {
        return displayName != null && !displayName.isEmpty() ? displayName : username;
    }

    public boolean hasProfileImage() {
        return profileImage != null && !profileImage.isEmpty();
    }

    public boolean hasBio() {
        return bio != null && !bio.isEmpty();
    }

    public boolean hasPhoneNumber() {
        return phoneNumber != null && !phoneNumber.isEmpty();
    }

    public boolean isNotificationEnabled() {
        return Boolean.TRUE.equals(notificationEnabled);
    }

    public boolean isTwoFactorEnabled() {
        return Boolean.TRUE.equals(twoFactorEnabled);
    }

    public boolean isPrivacyPublic() {
        return "PUBLIC".equals(privacySettings);
    }

    public boolean isPrivacyFriends() {
        return "FRIENDS".equals(privacySettings);
    }

    public boolean isPrivacyPrivate() {
        return "PRIVATE".equals(privacySettings);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isOnline == null) isOnline = false;
        if (isActive == null) isActive = true;
        if (notificationEnabled == null) notificationEnabled = true;
        if (twoFactorEnabled == null) twoFactorEnabled = false;
        if (status == null) status = "OFFLINE";
        if (privacySettings == null) privacySettings = "PUBLIC";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", userPhoto='" + userPhoto + '\'' +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}