package com.smartlib.exception;

/** Pengguna sudah login tetapi role-nya tidak berhak menjalankan operasi. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) { super(message); }
}
