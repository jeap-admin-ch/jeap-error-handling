package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionCauseChainCheckerTest {

    @Test
    void containsCauseType_shouldReturnTrueWhenDirectMatch() {
        IOException exception = new IOException("test");
        List<Class<? extends Throwable>> exceptionClasses = List.of(IOException.class);

        assertTrue(ExceptionCauseChainChecker.containsCauseType(exception, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldReturnTrueWhenCauseMatches() {
        IllegalArgumentException cause = new IllegalArgumentException("cause");
        RuntimeException exception = new RuntimeException("wrapper", cause);
        List<Class<? extends Throwable>> exceptionClasses = List.of(IllegalArgumentException.class);

        assertTrue(ExceptionCauseChainChecker.containsCauseType(exception, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldReturnTrueWhenDeepCauseMatches() {
        IOException deepCause = new IOException("deep cause");
        IllegalStateException middleCause = new IllegalStateException("middle", deepCause);
        RuntimeException exception = new RuntimeException("top", middleCause);
        List<Class<? extends Throwable>> exceptionClasses = List.of(IOException.class);

        assertTrue(ExceptionCauseChainChecker.containsCauseType(exception, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldReturnTrueWhenOneOfMultipleClassesMatches() {
        IllegalArgumentException exception = new IllegalArgumentException("test");
        List<Class<? extends Throwable>> exceptionClasses = List.of(
                IOException.class,
                IllegalArgumentException.class,
                NullPointerException.class
        );

        assertTrue(ExceptionCauseChainChecker.containsCauseType(exception, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldReturnTrueWhenMatchingSubclass() {
        IllegalArgumentException exception = new IllegalArgumentException("test");
        List<Class<? extends Throwable>> exceptionClasses = List.of(RuntimeException.class);

        assertTrue(ExceptionCauseChainChecker.containsCauseType(exception, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldReturnFalseWhenNoMatch() {
        IOException exception = new IOException("test");
        List<Class<? extends Throwable>> exceptionClasses = List.of(IllegalArgumentException.class);

        assertFalse(ExceptionCauseChainChecker.containsCauseType(exception, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldReturnFalseWhenThrowableIsNull() {
        List<Class<? extends Throwable>> exceptionClasses = List.of(IOException.class);

        assertFalse(ExceptionCauseChainChecker.containsCauseType(null, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldReturnFalseWhenExceptionClassesIsNull() {
        IOException exception = new IOException("test");

        assertFalse(ExceptionCauseChainChecker.containsCauseType(exception, null));
    }

    @Test
    void containsCauseType_shouldReturnFalseWhenExceptionClassesIsEmpty() {
        IOException exception = new IOException("test");

        assertFalse(ExceptionCauseChainChecker.containsCauseType(exception, Collections.emptyList()));
    }

    @Test
    void containsCauseType_shouldHandleCircularCauseChainWithoutInfiniteLoop() {
        // Create a custom exception with a self-referencing cause
        class CircularException extends Exception {
            CircularException(String message) {
                super(message);
                // Set itself as the cause to create a circular reference
                try {
                    initCause(this);
                } catch (IllegalArgumentException | IllegalStateException ignored) {
                    // initCause might throw if already set, which is fine for this test
                }
            }
        }

        CircularException circularException = new CircularException("circular");
        List<Class<? extends Throwable>> exceptionClasses = List.of(IOException.class);

        // Should not loop infinitely and should return false (no match)
        assertFalse(ExceptionCauseChainChecker.containsCauseType(circularException, exceptionClasses));

        // Should return true if we search for the actual type
        exceptionClasses = List.of(CircularException.class);
        assertTrue(ExceptionCauseChainChecker.containsCauseType(circularException, exceptionClasses));
    }

    @Test
    void containsCauseType_shouldFinaAllExceptionsInAChain() {
        // Create a chain: RuntimeException -> IllegalStateException -> IOException
        IOException deepCause = new IOException("deep");
        IllegalStateException middleCause = new IllegalStateException("middle", deepCause);
        RuntimeException topException = new RuntimeException("top", middleCause);

        // Should find IOException in the chain
        List<Class<? extends Throwable>> exceptionClasses = List.of(IOException.class);
        assertTrue(ExceptionCauseChainChecker.containsCauseType(topException, exceptionClasses));

        // Should find IllegalStateException in the chain
        exceptionClasses = List.of(IllegalStateException.class);
        assertTrue(ExceptionCauseChainChecker.containsCauseType(topException, exceptionClasses));

        // Should find RuntimeException at the top
        exceptionClasses = List.of(RuntimeException.class);
        assertTrue(ExceptionCauseChainChecker.containsCauseType(topException, exceptionClasses));
    }

}
