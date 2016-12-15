package com.farmaprom.constraint;

import org.apache.mesos.Protos;

import java.util.List;
import java.util.Objects;

public class EqualsConstraint extends Constraint {

    public EqualsConstraint(String key, String value) {
        super(key, value);
    }

    @Override
    public boolean matches(List<Protos.Attribute> attributes) {
        for (Protos.Attribute attribute : attributes) {
            if (Objects.equals(attribute.getName(), this.getKey())) {
                switch (attribute.getType()) {
                    case TEXT:
                        return attribute.getText().getValue().matches(this.getValue());
                    case SCALAR:
                        return Objects.equals(Double.toString(attribute.getScalar().getValue()), this.getValue());
                    case SET:
                        return attribute.getSet().getItemList().contains(this.getValue());
                    case RANGES:
                        break;
                    default:
                }

            }
        }

        return false;
    }
}
