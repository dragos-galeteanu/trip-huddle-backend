package com.triphuddle.testsupport.security;

import org.junit.jupiter.params.provider.Arguments;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Who may do what to a resource. A caller not listed for an operation must be refused it.
 */
public final class AuthorizationMatrix {

    private final Map<Operation, Set<Caller>> allowed = new EnumMap<>(Operation.class);

    private AuthorizationMatrix() {
    }

    public static AuthorizationMatrix create() {
        return new AuthorizationMatrix();
    }

    public AuthorizationMatrix allow(Operation operation, Caller... callers) {
        allowed.computeIfAbsent(operation, o -> EnumSet.noneOf(Caller.class)).addAll(List.of(callers));
        return this;
    }

    public AuthorizationMatrix allowEveryone(Operation operation) {
        return allow(operation, Caller.values());
    }

    public boolean isAllowed(Operation operation, Caller caller) {
        return allowed.getOrDefault(operation, Set.of()).contains(caller);
    }

    /**
     * Every operation with every caller, for parameterized tests.
     */
    public static Stream<Arguments> everyOperationAndCaller() {
        return Arrays.stream(Operation.values())
                .flatMap(operation -> Arrays.stream(Caller.values()).map(caller -> Arguments.of(operation, caller)));
    }
}
