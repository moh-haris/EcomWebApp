package com.haris.SpringEcom.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDto {
    String jwt;
    Long userId;

    // RBAC: We send the role back to the frontend after login.
    // The frontend (React/Vite) stores this in localStorage/context
    // and uses it to show "Add Product" / "Delete" buttons for ADMIN only.
    String role;
}
