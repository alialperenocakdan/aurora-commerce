package com.aurora.order.exception;

// 'pending' dışındaki bir siparişi iptal etmeye çalışınca (ör. 'shipped') fırlatılır.
public class NotCancellableException extends RuntimeException {
    public NotCancellableException() {
        super("not_cancellable");
    }
}
