package com.codeclog.api.error;

import java.io.Serial;
import java.util.List;

/**
 * Base class for errors the API deliberately returns. Anything else that escapes a controller is a
 * bug and becomes a 500 with a generic message.
 */
public class ApiException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    private final transient ErrorCode code;
    private final transient List<ApiErrorResponse.Detail> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ApiErrorResponse.Detail> details) {
        super(message);
        this.code = code;
        this.details = List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiErrorResponse.Detail> details() {
        return details;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
