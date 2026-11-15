package edu.gcet.placementhub.utils;

/**
 * Generic result wrapper for service operations.
 *
 * @param <T> Payload type
 */
public class OperationResult<T> {
    public final boolean success;
    public final String message;
    public final T data;

    public OperationResult(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> OperationResult<T> ok(String msg, T data) {
        return new OperationResult<>(true, msg, data);
    }

    public static <T> OperationResult<T> ok(String msg) {
        return new OperationResult<>(true, msg, null);
    }

    public static <T> OperationResult<T> fail(String msg) {
        return new OperationResult<>(false, msg, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
