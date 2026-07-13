package org.hvtra.commonservice.kafka.outbox.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * Debezium operation code in change-event envelope.
 * c = create/insert, u = update, d = delete, r = snapshot read.
 */
public enum DebeziumOp {

    CREATE("c"),
    UPDATE("u"),
    DELETE("d"),
    READ("r");

    private final String value;

    DebeziumOp(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean matches(String op) {
        return value.equals(op);
    }

    public static Optional<DebeziumOp> fromValue(String value) {
        return Arrays.stream(values())
                .filter(op -> op.value.equals(value))
                .findFirst();
    }

    public static boolean isCreate(String value) {
        return CREATE.matches(value);
    }

    public static boolean isOutboxRowEvent(String value) {
        return CREATE.matches(value) || READ.matches(value);
    }
}
