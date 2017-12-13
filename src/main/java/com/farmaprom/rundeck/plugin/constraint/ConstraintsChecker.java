package com.farmaprom.rundeck.plugin.constraint;

import org.apache.commons.lang.StringUtils;
import org.apache.mesos.v1.Protos;

import java.util.ArrayList;
import java.util.List;

public class ConstraintsChecker {

    private final String[] constraintsPairs;

    public ConstraintsChecker(String constraints) {
        if (!StringUtils.isBlank(constraints)) {
            this.constraintsPairs = constraints.split(",");
        } else {
            this.constraintsPairs = new String[0];
        }

    }

    public boolean constraintsAllow(Protos.Offer offer) {

        ArrayList<Constraint> constraints = this.getMatchConstraints();

        if (this.constraintsPairs.length > 0 && constraintsPairs.length != constraints.size()) {
            return false;
        }

        if (!constraints.isEmpty()) {
            List<Protos.Attribute> attributes = this.margeAttribute(offer);
            for (Constraint constraint : constraints) {
                boolean found = constraint.matches(attributes);

                if (!found) {
                    return false;
                }
            }
        }

        return true;
    }

    private ArrayList<Constraint> getMatchConstraints() {
        ArrayList<Constraint> constraintList = new ArrayList<>();
        if (this.constraintsPairs.length > 0) {
            for (String pair : this.constraintsPairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 3) {
                    switch (Constraint.Operator.get(keyValue[1])) {
                        case EQUALS:
                            constraintList.add(new EqualsConstraint(keyValue[0], keyValue[2]));
                            break;
                        case LIKE:
                            constraintList.add(new LikeConstraint(keyValue[0], keyValue[2]));
                            break;
                        case UNLIKE:
                            constraintList.add(new UnlikeConstraint(keyValue[0], keyValue[2]));
                            break;
                    }
                }
            }
        }

        return constraintList;
    }

    private List<Protos.Attribute> margeAttribute(Protos.Offer offer) {

        Protos.Value.Text hostnameText = Protos.Value.Text.newBuilder().setValue(offer.getHostname()).build();
        Protos.Attribute hostnameAttribute = Protos.Attribute.newBuilder().setName("hostname").setText(hostnameText).setType(Protos.Value.Type.TEXT).build();

        List<Protos.Attribute> mergeAttributes = new ArrayList<>(offer.getAttributesList());

        if (!contains(mergeAttributes, "hostname")) {
            mergeAttributes.add(hostnameAttribute);
        }

        return mergeAttributes;
    }

    private boolean contains(List<Protos.Attribute> attributes, String name) {
        for (Protos.Attribute attribute : attributes) {
            if (attribute.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
