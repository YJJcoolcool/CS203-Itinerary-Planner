package com.smu.cs203_g1_1.itinerary_planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@DataJpaTest
@Import(UserService.class)
class CurrentUserTest {

    @Autowired
    private UserService userService;

    @Test
    void resolvesTokenToInternalUser() {
        User created = userService.findOrCreateCurrentUser(tokenFor("clerk-abc"), "Abc");

        assertEquals(created.getId(), userService.getCurrentUserId(tokenFor("clerk-abc")));
    }

    @Test
    void missingTokenIsUnauthorized() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> userService.getCurrentUser(null));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void userWithoutProfileIsNotFound() {
        assertThrows(UserNotFoundException.class, () -> userService.getCurrentUser(tokenFor("clerk-new")));
        assertTrue(userService.findCurrentUser(tokenFor("clerk-new")).isEmpty());
    }

    private Jwt tokenFor(String clerkUserId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(clerkUserId);
        return jwt;
    }
}
