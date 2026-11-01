package com.pos.identityaccess.application.exception;

public class LastOwnerProtectedException extends ConflictException {

    private static final long serialVersionUID = 1L;

    public LastOwnerProtectedException() {
        super("last_owner_protected", "The last active owner cannot be removed");
    }
}
