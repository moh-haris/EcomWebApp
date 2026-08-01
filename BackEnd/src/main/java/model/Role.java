package com.haris.SpringEcom.model;

/**
 * RBAC - Role Definitions
 *
 * We use an Enum instead of a plain String to:
 *  1. Prevent typos (e.g., "ADMin" vs "ADMIN") at compile time.
 *  2. Make the code self-documenting — you can immediately see all valid roles.
 *
 * Spring Security will look for authorities prefixed with "ROLE_"
 * (e.g., "ROLE_ADMIN", "ROLE_USER"). We add that prefix inside User.getAuthorities().
 */
public enum Role {
    USER,   // Standard customer: can browse, add to cart, buy
    ADMIN   // Privileged user: can also add, update, delete products
}
