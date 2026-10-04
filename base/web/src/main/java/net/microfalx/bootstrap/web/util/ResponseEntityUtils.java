package net.microfalx.bootstrap.web.util;

import net.microfalx.argus.api.Failure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Various utility methods to create {@link ResponseEntity} objects.
 */
public class ResponseEntityUtils {

    /**
     * Converts a {@link Failure} into a {@link ResponseEntity.BodyBuilder} with the appropriate HTTP status code.
     *
     * @param failure the failure
     * @return a non-null instance
     */
    public static ResponseEntity.BodyBuilder fromFailure(Failure failure) {
        requireNonNull(failure);
        return switch (failure.getType()) {
            case AUTHENTICATION -> ResponseEntity.status(HttpStatus.UNAUTHORIZED);
            case AUTHORIZATION -> ResponseEntity.status(HttpStatus.FORBIDDEN);
            case CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT);
            case TIMED_OUT, OVERLOAD, RESOURCE_BUSY -> ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT);
            case RESOURCE_NOT_FOUND, RESOURCE_UNAVAILABLE -> ResponseEntity.status(HttpStatus.NOT_FOUND);
            case CONNECTIVITY, NETWORK, SERVICE_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);
            case ILLEGAL_INPUT, ILLEGAL_OUTPUT -> ResponseEntity.badRequest();
            default -> ResponseEntity.internalServerError();
        };
    }

    /**
     * Returns a user-friendly error message for the given failure.
     *
     * @param throwable         the failure
     * @param withExceptionType {@code true} to include the exception type in the message, {@code false} otherwise
     * @return a non-null string
     */
    public static String getErrorMessage(Throwable throwable, boolean withExceptionType) {
        return getErrorMessage(Failure.of(throwable), withExceptionType);
    }

    /**
     * Returns a user-friendly error message for the given failure.
     *
     * @param failure           the failure
     * @param withExceptionType {@code true} to include the exception type in the message, {@code false} otherwise
     * @return a non-null string
     */
    public static String getErrorMessage(Failure failure, boolean withExceptionType) {
        requireNonNull(failure);
        String message = withExceptionType ? failure.getRootCauseDescription() : failure.getRootCauseMessage();
        String name = failure.getRootCauseName();
        return switch (failure.getType()) {
            case AUTHENTICATION -> "authentication failed: " + message;
            case AUTHORIZATION -> "authorization failed: " + message;
            case CONFLICT -> "conflict occurred";
            case TIMED_OUT, OVERLOAD, RESOURCE_BUSY -> "request timed out: " + name;
            case RESOURCE_NOT_FOUND, RESOURCE_UNAVAILABLE -> "resource not found: " + message;
            case CONNECTIVITY, NETWORK, SERVICE_UNAVAILABLE -> "service unavailable: " + name;
            case ILLEGAL_INPUT, ILLEGAL_OUTPUT -> "illegal input/output: " + name;
            default -> "internal server error: " + name;
        };
    }
}
