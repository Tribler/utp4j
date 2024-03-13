package net.utp4j.data.bytes.exceptions;

import java.io.Serial;

/**
 * Exception that indicates that an arithmetic overflow happened.
 *
 * @author Ivan Iljkic (i.iljkic@gmail.com)
 */

public class ByteOverflowException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -2545130698255742704L;

    public ByteOverflowException(String message) {
        super(message);
    }

    public ByteOverflowException() {
        super("Overflow happened.");
    }
}
