package com.phonghub.application.port.in;

import com.phonghub.application.port.out.CurrentUser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface DemoActorPort {
    Optional<CurrentUser> findDemoUser(UUID userId);
    void switchActor(UUID userId);
    Map<UUID, CurrentUser> getAllDemoUsers();
    void setCurrentUser(CurrentUser user);
    void clear();
}
