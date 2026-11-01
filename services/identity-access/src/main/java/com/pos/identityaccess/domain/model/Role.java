package com.pos.identityaccess.domain.model;

public enum Role {
    OWNER,
    MANAGER,
    CASHIER,
    KITCHEN;

    public boolean requiresBranch() {
        return this != OWNER;
    }
}
