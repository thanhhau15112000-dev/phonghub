package com.phonghub.application.port.out;

import java.util.UUID;

/** Removes an unsubmitted check-in attached to a cancelled draft contract. */
public interface DraftHandoverCleanupPort {
    void cancelDraftCheckIn(UUID contractId, UUID actorId);
}
