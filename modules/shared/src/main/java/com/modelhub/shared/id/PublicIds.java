package com.modelhub.shared.id;

import java.util.UUID;

/**
 * 外部标识统一使用不可枚举 UUID publicId（04 §1），不复用。
 */
public final class PublicIds {

    private PublicIds() {}

    public static UUID next() {
        return UUID.randomUUID();
    }

    public static String nextString() {
        return UUID.randomUUID().toString();
    }
}
