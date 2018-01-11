package com.farmaprom.rundeck.plugin.state;

public final class Tuple<T1, T2> {
    public final T1 _1;
    public final T2 _2;

    public Tuple(final T1 v1, final T2 v2) {
        _1 = v1;
        _2 = v2;
    }

    public static <T1, T2> Tuple<T1, T2> create(final T1 v1, final T2 v2) {
        return new Tuple<>(v1, v2);
    }
}