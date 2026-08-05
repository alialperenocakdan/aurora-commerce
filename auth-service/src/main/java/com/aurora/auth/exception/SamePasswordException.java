package com.aurora.auth.exception;

// Yeni şifre mevcut şifreyle aynı — sessizce kabul etmek yerine uyarıyoruz.
public class SamePasswordException extends RuntimeException {
    public SamePasswordException() { super("same_password"); }
}
