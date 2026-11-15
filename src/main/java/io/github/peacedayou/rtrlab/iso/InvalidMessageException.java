package io.github.peacedayou.rtrlab.iso;

/** Message rejeté avant tout traitement : XML illisible ou non conforme au schéma. */
public class InvalidMessageException extends RuntimeException {

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
