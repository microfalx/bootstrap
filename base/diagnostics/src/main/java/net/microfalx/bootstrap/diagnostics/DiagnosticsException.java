package net.microfalx.bootstrap.diagnostics;

/**
 * Root exception for all diagnostics failures.
 */
public class DiagnosticsException extends RuntimeException {

    public DiagnosticsException(String message) {
        super(message);
    }

    public DiagnosticsException(String message, Throwable cause) {
        super(message, cause);
    }
}
