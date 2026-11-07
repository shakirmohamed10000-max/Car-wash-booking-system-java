package exception;

/**
 * Base type for every error the program expects and can describe to the user.
 * The message of an AppException is always safe to print on the terminal.
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
