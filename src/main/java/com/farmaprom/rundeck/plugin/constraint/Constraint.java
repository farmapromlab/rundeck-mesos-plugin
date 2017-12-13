package com.farmaprom.rundeck.plugin.constraint;

import org.apache.mesos.v1.Protos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Constraint {

    abstract public boolean matches(List<Protos.Attribute> attributes);

    private final String key;

    private final String value;

    Constraint(String key, String value) {
        this.key = key;
        this.value = value;
    }

    String getKey()
    {
        return key;
    }

    String getValue()
    {
        return value;
    }

    public enum Operator {

        LIKE("LIKE"),
        UNLIKE("UNLIKE"),
        EQUALS("EQUALS");

        private String operator;

        Operator(String operator) {
            this.operator = operator;
        }

        private static Map<String, Operator> constants = new HashMap<>();

        static {
            for (Constraint.Operator c: values()) {
                constants.put(c.operator, c);
            }
        }

        public static Operator get(String value) {
            Operator constant = constants.get(value);
            if (constant == null) {
                throw new IllegalArgumentException(value);
            } else {
                return constant;
            }
        }
    }
}
