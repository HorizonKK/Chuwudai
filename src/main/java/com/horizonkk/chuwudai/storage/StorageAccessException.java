package com.horizonkk.chuwudai.storage;

public class StorageAccessException extends RuntimeException {
    public enum Reason { NOT_READY, IN_USE, UNAVAILABLE, LEASE_LOST, CORRUPT, NOT_FOUND }
    private final Reason reason;
    public StorageAccessException(Reason reason, String message) { super(message); this.reason = reason; }
    public StorageAccessException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }
    public Reason getReason() { return reason; }
}
