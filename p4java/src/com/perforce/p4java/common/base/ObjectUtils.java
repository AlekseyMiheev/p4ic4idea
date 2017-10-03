package com.perforce.p4java.common.base;

import java.util.Properties;

public class ObjectUtils {
    /**
     * Returns {@code true} if the provided reference is {@code null} otherwise
     * returns {@code false}.
     *
     * @param obj a reference to be checked against {@code null}
     * @return {@code true} if the provided reference is {@code null} otherwise
     * {@code false}
     * @apiNote It will be replaced by Objects.nonNull after jdk1.8
     */
    public static boolean nonNull(Object obj) {
        return obj != null;
    }

    /**
     * Returns {@code true} if the provided reference is {@code null} otherwise
     * returns {@code false}.
     *
     * @param obj a reference to be checked against {@code null}
     * @return {@code true} if the provided reference is {@code null} otherwise
     * {@code false}
     * @apiNote It will be replaced by Objects.nonNull after jdk1.8
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    // p4ic4idea: compatibility method
    public static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("No non-null argument");
    }

    public static String[] addAll(String[] s1, String[] s2) {
        String[] ret = new String[s1.length + s2.length];
        System.arraycopy(s1, 0, ret, 0, s1.length);
        System.arraycopy(s2, 0, ret, s1.length, s2.length);
        return ret;
    }
}
