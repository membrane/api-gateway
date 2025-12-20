package com.predic8.membrane.core.router;

/**
 * Exception that signals that the startup failed and that Membrane should exit.
 * It doesn't have a context or message cause it just signals that the program should exit.
 * By using the exception, it is possible to exit at only one place. That makes debugging and testing
 * of the Router class easier.
 * The exception is in this package instead of the cli package not to introduce a dependency to cli.
 *
 * TODO: Move exception to router subpackage together with Router.
 */
public class ExitException extends RuntimeException {
}
