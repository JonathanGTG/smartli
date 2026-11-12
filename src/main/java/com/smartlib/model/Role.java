package com.smartlib.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static com.smartlib.model.Permission.*;

/**
 * Role pengguna sekaligus pemetaan hak aksesnya (RBAC).
 * Nama konstanta sama dengan kolom roles.name di database.
 */
public enum Role {
    ADMIN("Admin", EnumSet.complementOf(EnumSet.of(VIEW_OWN_HISTORY, REQUEST_QUEUE, VIEW_OWN_FINE))),
    PETUGAS("Petugas", EnumSet.of(MANAGE_COLLECTION, MANAGE_MEMBERS, VIEW_CATALOG, PROCESS_LOAN,
            PROCESS_RETURN, VIEW_ALL_TRANSACTIONS, RECORD_FINE_PAYMENT, VIEW_REPORTS)),
    ANGGOTA("Anggota", EnumSet.of(VIEW_CATALOG, VIEW_OWN_HISTORY, REQUEST_QUEUE, VIEW_OWN_FINE));

    private final String label;
    private final Set<Permission> permissions;

    Role(String label, Set<Permission> permissions) {
        this.label = label;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public String getLabel() { return label; }

    public Set<Permission> getPermissions() { return permissions; }

    public boolean can(Permission permission) { return permissions.contains(permission); }

    public static Role fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Nilai Role tidak boleh kosong.");
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Role '" + value + "' tidak dikenal.", e);
        }
    }

    @Override
    public String toString() { return label; }
}
