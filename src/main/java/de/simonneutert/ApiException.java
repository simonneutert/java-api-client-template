package de.simonneutert;

/**
 * Thrown when the API client encounters an error, either at the network/transport
 * layer or when the remote endpoint returns a non-2xx HTTP status code.
 */
public class ApiException extends Exception {
    private static final long serialVersionUID = 1L;

    /** HTTP status code returned by the server, or {@code -1} if not applicable. */
    private final int httpStatus;

    /**
     * Creates an exception without an associated HTTP status (e.g., for network or
     * serialization errors).
     *
     * @param message detail message
     */
    public ApiException(String message) {
        this(-1, message);
    }

    /**
     * Creates an exception carrying the HTTP status code returned by the server.
     *
     * @param httpStatus the HTTP response status code
     * @param message    detail message
     */
    public ApiException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * Wraps a lower-level exception without an HTTP status code.
     *
     * @param message detail message
     * @param cause   the underlying exception
     */
    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    /**
     * Wraps a lower-level exception without an HTTP status code.
     *
     * @param cause the underlying exception
     */
    public ApiException(Throwable cause) {
        super(cause);
        this.httpStatus = -1;
    }

    /**
     * Returns the HTTP status code associated with this exception,
     * or {@code -1} if the error is not related to an HTTP response
     * (e.g., a network or JSON parsing error).
     *
     * @return HTTP status code, or {@code -1}
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns {@code true} if this exception carries an HTTP status code.
     *
     * @return {@code true} when an HTTP status code is available
     */
    public boolean hasHttpStatus() {
        return httpStatus != -1;
    }
}
