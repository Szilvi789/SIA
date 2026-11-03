package com.personalai.platform.writing.email;

import java.util.Set;

/** Public account metadata only; OAuth secrets belong in a future encrypted server-side token store. */
public record EmailAccount(String id, String name, String email, String provider, boolean connected,
        Set<EmailPermission> permissions, boolean mock) {
    public EmailAccount { permissions = Set.copyOf(permissions); }
}
