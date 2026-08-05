package com.aurora.auth.exception;

// Şifre değiştirmede "mevcut şifre" hatalı. Bilinçli olarak 401 DEĞİL:
// 401 istemcide oturumu kapattırır, oysa kullanıcının oturumu geçerli;
// sadece formda yanlış bir değer girmiş.
public class WrongPasswordException extends RuntimeException {
    public WrongPasswordException() { super("wrong_password"); }
}
