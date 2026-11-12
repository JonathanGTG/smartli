package com.smartlib.exception;

/** Login gagal atau pengguna belum login. */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
}
