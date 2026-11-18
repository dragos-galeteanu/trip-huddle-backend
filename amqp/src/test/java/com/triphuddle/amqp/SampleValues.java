package com.triphuddle.amqp;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds a message with a distinct, non-default value in every field, nested records and lists included.
 */
final class SampleValues {

    private final AtomicInteger seq = new AtomicInteger(1);

    Object of(Class<?> type) {
        try {
            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
                Object[] values = Arrays.stream(components).map(c -> value(c.getType(), c.getGenericType())).toArray();
                Constructor<?> constructor = type.getDeclaredConstructor(types);
                constructor.setAccessible(true);
                return constructor.newInstance(values);
            }
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();
            for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        field.set(instance, value(field.getType(), field.getGenericType()));
                    }
                }
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build a sample " + type.getName(), e);
        }
    }

    private Object value(Class<?> type, Type generic) {
        int n = seq.getAndIncrement();
        if (type == String.class || type == Object.class) return "value-" + n;
        if (type == UUID.class) return new UUID(n, n);
        if (type == Instant.class) return Instant.parse("2026-11-16T10:00:00Z").plusSeconds(n);
        if (type == LocalDate.class) return LocalDate.of(2026, 1, 1).plusDays(n);
        if (type == LocalDateTime.class) return LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(n);
        if (type == OffsetDateTime.class) return OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusMinutes(n);
        if (type == int.class || type == Integer.class) return n;
        if (type == long.class || type == Long.class) return (long) n * 1000;
        if (type == short.class || type == Short.class) return (short) n;
        if (type == double.class || type == Double.class) return n + 0.5;
        if (type == float.class || type == Float.class) return n + 0.25f;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == BigDecimal.class) return new BigDecimal(n + ".25");
        if (type == byte[].class) return ("bytes-" + n).getBytes();
        if (type == float[].class) return new float[]{n + 0.5f, -n};
        if (type == double[].class) return new double[]{n + 0.5, -n};
        if (type == int[].class) return new int[]{n, -n};
        if (type.isEnum()) return type.getEnumConstants()[type.getEnumConstants().length - 1];
        if (List.class.isAssignableFrom(type) || Collection.class == type) return List.of(element(generic, 0));
        if (Set.class.isAssignableFrom(type)) return Set.of(element(generic, 0));
        if (Map.class.isAssignableFrom(type)) return Map.of(element(generic, 0), element(generic, 1));
        return of(type);
    }

    private Object element(Type generic, int index) {
        Type argument = ((ParameterizedType) generic).getActualTypeArguments()[index];
        Class<?> raw = argument instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) argument;
        return value(raw, argument);
    }
}
