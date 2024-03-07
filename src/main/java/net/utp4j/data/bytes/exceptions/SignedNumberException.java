package net.utp4j.data.bytes.exceptions;

import java.io.Serial;

/**
 * Exception for the Case a signed number was given.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */

public class SignedNumberException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1339267381376290545L;

    public SignedNumberException(String message) {
        super(message);
    }

    public SignedNumberException() {
        super("No negative values allowed");
    }

}
