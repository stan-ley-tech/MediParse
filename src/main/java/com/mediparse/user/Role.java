package com.mediparse.user;

/**
 * Coarse-grained roles used for both authentication claims and RBAC checks.
 * ADMIN can manage users and see everything; CLINICIAN has broad read access
 * to patient records; STAFF is restricted to documents they themselves uploaded.
 */
public enum Role {
    ADMIN,
    CLINICIAN,
    STAFF
}
