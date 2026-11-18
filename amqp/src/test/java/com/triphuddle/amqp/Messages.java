package com.triphuddle.amqp;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Finds the classes in this module by kind, so tests cover every message and contract, including ones added later.
 */
final class Messages {

    private static final Set<String> SUFFIXES_OF_NON_MESSAGES = Set.of("Contract", "Queues", "Exchanges");

    private Messages() {
    }

    /**
     * Every message a service sends: the records and classes that are not contracts, queue lists or enums.
     */
    static List<Class<?>> all() {
        return scan().stream()
                .filter(type -> !type.isEnum() && !type.isInterface() && !Modifier.isAbstract(type.getModifiers()))
                .filter(type -> type.getPackageName().startsWith("com.triphuddle.amqp."))
                .filter(type -> SUFFIXES_OF_NON_MESSAGES.stream().noneMatch(type.getSimpleName()::endsWith))
                .toList();
    }

    /**
     * Every class whose name ends with the suffix, e.g. "Contract".
     */
    static List<Class<?>> endingWith(String suffix) {
        return scan().stream().filter(type -> type.getSimpleName().endsWith(suffix)).toList();
    }

    private static List<Class<?>> scan() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent();
            }
        };
        TypeFilter everything = (reader, factory) -> true;
        scanner.addIncludeFilter(everything);
        return scanner.findCandidateComponents("com.triphuddle.amqp").stream()
                .<Class<?>>map(definition -> load(definition.getBeanClassName()))
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
