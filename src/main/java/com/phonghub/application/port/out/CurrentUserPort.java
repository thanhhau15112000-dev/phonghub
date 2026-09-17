package com.phonghub.application.port.out;

import java.util.Optional;

public interface CurrentUserPort {
    CurrentUser getCurrentUser();
    void setCurrentUser(CurrentUser user);
    void clear();
}
