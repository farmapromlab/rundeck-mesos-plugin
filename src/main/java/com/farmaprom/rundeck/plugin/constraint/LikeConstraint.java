package com.farmaprom.rundeck.plugin.constraint;

import org.apache.mesos.v1.Protos;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class LikeConstraint extends Constraint {

    public LikeConstraint(String key, String value) {
        super(key, value);
    }

    @Override
    public boolean matches(List<Protos.Attribute> attributes) {
        for (Protos.Attribute attribute : attributes) {
            if (!Objects.equals(attribute.getName(), this.getKey())) {
                continue;
            }
            switch (attribute.getType()) {
                case TEXT:
                    return attribute.getText().getValue().matches(this.getValue());
                case SCALAR:
                    return Pattern.compile(this.getValue()).matcher(Double.toString(attribute.getScalar().getValue())).matches();
                case SET:
                    for (String string : attribute.getSet().getItemList()) {
                        if(string.matches(this.getValue())){
                            return true;
                        }
                    }
                case RANGES:
                    break;
                default:
            }
        }

        return false;
    }
}
