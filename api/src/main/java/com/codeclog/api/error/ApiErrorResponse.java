package com.codeclog.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * The single error envelope for every non-2xx response (§10):
 *
 * <pre>{ "error": { "code": "...", "message": "...", "details": [ ... ] } }</pre>
 */
@Schema(name = "ApiErrorResponse", description = "Error envelope returned by every failing endpoint")
public record ApiErrorResponse(Error error) {

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return new ApiErrorResponse(new Error(code.name(), message, List.of()));
    }

    public static ApiErrorResponse of(ErrorCode code, String message, List<Detail> details) {
        return new ApiErrorResponse(new Error(code.name(), message, details));
    }

    @Schema(name = "ApiError")
    public record Error(
            @Schema(description = "Stable machine-readable code", example = "VALIDATION_FAILED") String code,
            @Schema(description = "Human-readable summary, safe to surface") String message,
            @JsonInclude(JsonInclude.Include.NON_EMPTY)
                    @Schema(description = "Field-level detail; empty for errors that are not field-scoped")
                    List<Detail> details) {}

    @Schema(name = "ApiErrorDetail")
    public record Detail(
            @Schema(description = "Offending field path", example = "handle") String field,
            @Schema(description = "What was wrong with it") String message) {}
}
