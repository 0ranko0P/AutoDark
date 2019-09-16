package me.ranko.autodark.Exception;

/**
 * Signals that an error occurred during execute command.
 *
 * @author  0ranko0P
 * @see     Runtime#exec(String)
 */
public final class CommandExecuteError extends Exception {
    static final long serialVersionUID = -418375825643090127L;

    /**
     * Constructs an {@code CommandExecuteError} with {@code null}
     * as its error detail message.
     */
    public CommandExecuteError() {
        super();
    }

    /**
     * Constructs a new CommandExecuteError with the specified detail message.  The
     * cause is not initialized, and may subsequently be initialized by
     * a call to {@link #initCause}.
     *
     * @param   message   the detail message. The detail message is saved for
     *          later retrieval by the {@link #getMessage()} method.
     */
    public CommandExecuteError(String message) {
        super(message);
    }

    /**
     * Constructs an {@code CommandExecuteError} with the specified detail message
     * and cause.
     *
     * <p> Note that the detail message associated with {@code cause} is
     * <i>not</i> automatically incorporated into this exception's detail
     * message.
     *
     * @param message
     *        The detail message (which is saved for later retrieval
     *        by the {@link #getMessage()} method)
     *
     * @param cause
     *        The cause (which is saved for later retrieval by the
     *        {@link #getCause()} method).  (A null value is permitted,
     *        and indicates that the cause is nonexistent or unknown.)
     *
     */
    public CommandExecuteError(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an {@code CommandExecuteError} with the specified cause and a
     * detail message of {@code (cause==null ? null : cause.toString())}
     * (which typically contains the class and detail message of {@code cause}).
     * This constructor is useful for IO exceptions that are little more
     * than wrappers for other throwables.
     *
     * @param cause
     *        The cause (which is saved for later retrieval by the
     *        {@link #getCause()} method).  (A null value is permitted,
     *        and indicates that the cause is nonexistent or unknown.)
     *
     */
    public CommandExecuteError(Throwable cause) {
        super(cause);
    }
}
