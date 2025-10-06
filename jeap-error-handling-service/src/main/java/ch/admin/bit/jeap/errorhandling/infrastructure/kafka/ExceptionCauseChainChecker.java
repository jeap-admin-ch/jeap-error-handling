package ch.admin.bit.jeap.errorhandling.infrastructure.kafka;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for checking if a throwable's cause chain contains a cause of one or more given types.
 */
public class ExceptionCauseChainChecker {

    private ExceptionCauseChainChecker() {
        // Utility class
    }

    /**
     * Checks if one of a throwable's nested causes is an instance of a given list of types.
     * Searches from top to bottom through the cause chain and stops at the first match.
     *
     * @param throwable the throwable to check
     * @param causeTypes the list of types to check for
     * @return true if a cause in the chain matches one of the given types, false otherwise
     */
    public static boolean containsCauseType(Throwable throwable, List<Class<? extends Throwable>> causeTypes) {
        if (throwable == null || causeTypes == null || causeTypes.isEmpty()) {
            return false;
        }

        Set<Throwable> visited = new HashSet<>();
        Throwable current = throwable;

        while (current != null && !visited.contains(current)) {
            visited.add(current);

            for (Class<? extends Throwable> exceptionClass : causeTypes) {
                if (exceptionClass.isInstance(current)) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

}
