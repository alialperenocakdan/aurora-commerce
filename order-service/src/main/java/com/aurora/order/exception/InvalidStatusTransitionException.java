package com.aurora.order.exception;

// Geçersiz durum geçişi (ör. teslim edilmiş siparişi "hazırlanıyor" yapmak).
// Hangi durumdan hangisine gidilmek istendiği mesajda taşınır ki yönetim
// paneli kullanıcıya nedenini gösterebilsin.
public class InvalidStatusTransitionException extends RuntimeException {

    private final String from;
    private final String to;

    public InvalidStatusTransitionException(String from, String to) {
        super("invalid_status_transition");
        this.from = from;
        this.to = to;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }
}
