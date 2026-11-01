package com.pos.identityaccess.application;

import java.security.SecureRandom;
import java.util.UUID;

public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    public static UUID uuidV7() {
        long timestamp = System.currentTimeMillis() & 0x0000FFFFFFFFFFFFL;
        long randomA = RANDOM.nextLong() & 0x0FFFL;
        long randomB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long mostSignificantBits = (timestamp << 16) | 0x7000L | randomA;
        long leastSignificantBits = 0x8000000000000000L | randomB;
        return new UUID(mostSignificantBits, leastSignificantBits);
    }
}
