package com.chatapp.whatsapp.dto;

import lombok.*;


@NoArgsConstructor
@Data
@Setter
@Getter
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String userPhoto;
    private Boolean isActive;
    private String createdAt;

}
