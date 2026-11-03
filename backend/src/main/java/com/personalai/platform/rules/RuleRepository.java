package com.personalai.platform.rules;

import java.util.List;
import java.util.Optional;

/** Storage seam for future authorized rule management, independent of providers. */
public interface RuleRepository {
    List<RuleDocument> findAll();

    Optional<RuleDocument> findById(String id);

    /** Writes only the caller's user-owned preset namespace; packaged rules remain immutable. */
    default RuleDocument saveUserOwned(RuleDocument rule) {
        throw new UnsupportedOperationException("This repository does not support editable rules.");
    }
}
