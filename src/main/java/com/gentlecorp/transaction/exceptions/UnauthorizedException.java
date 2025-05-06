package com.gentlecorp.transaction.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exceptions thrown when an unauthorized access attempt is made.
 * <p>
 * This exceptions is used to signal that an operation or request could not be performed due to
 * lack of proper authorization. It results in an HTTP 401 (Unauthorized) response.
 * </p>
 *
 * @since 24.08.2024
 * @version 1.0
 * @author <a href="mailto:Caleb_G@outlook.de">Caleb Gyamfi</a>
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

  /**
   * Constructs a new {@code UnauthorizedException} with the specified detail message.
   *
   * @param message The detail message that explains the reason for the exceptions.
   *                This message is saved for later retrieval by the {@link #getMessage()} method.
   */
  public UnauthorizedException(final String message) {
    super(message);
  }
}
