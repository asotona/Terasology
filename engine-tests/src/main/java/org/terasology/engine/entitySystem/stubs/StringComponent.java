// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.engine.entitySystem.stubs;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.terasology.engine.entitySystem.Component;


public final class StringComponent implements Component {
    public static String staticValue = "Test";
    public String value;

    public StringComponent() {
    }

    public StringComponent(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StringComponent that = (StringComponent) o;
        return Objects.equal(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(value)
                .toString();
    }
}
