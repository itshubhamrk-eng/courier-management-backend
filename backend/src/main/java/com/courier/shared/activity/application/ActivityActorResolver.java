package com.courier.shared.activity.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Port from the activity log to whatever owns users and branches, so the shared log
 * can show a person's name and branch without importing those modules. The log row only
 * stores the login email at write time; the name and branch are looked up when read.
 */
public interface ActivityActorResolver {

    /** @return actors keyed by user id; ids that no longer resolve are simply absent. */
    Map<UUID, ActivityActor> resolve(Collection<UUID> userIds);

    record ActivityActor(String fullName, String branchName) {
    }
}
