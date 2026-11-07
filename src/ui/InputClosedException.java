package ui;

/** Standard input ended (Ctrl+D or a closed pipe). The program should exit cleanly. */
public class InputClosedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InputClosedException() {
        super("Input closed.", null, false, false);
    }
}
