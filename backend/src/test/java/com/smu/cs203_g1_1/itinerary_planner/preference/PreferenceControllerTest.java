package com.smu.cs203_g1_1.itinerary_planner.preference;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

@ExtendWith(MockitoExtension.class)
class PreferenceControllerTest {

    @Mock
    private PreferenceService preferenceService;

    @Mock
    private UserService userService;

    @Mock
    private Jwt jwt;

    private PreferenceController controller;

    @BeforeEach
    void setUp() {
        controller = new PreferenceController(preferenceService, userService);
    }

    @Test
    void mineReturnsTheCallersOwnPreferenceIncludingNotes() {
        UserPreference preference = new UserPreference("User0001");
        preference.setTags(List.of("art"));
        preference.setNotes("quiet places");

        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(preferenceService.getOrCreateUser("User0001")).thenReturn(preference);

        PreferenceController.UserPreferenceResponse response = controller.mine(jwt);

        assertEquals("User0001", response.userId());
        assertEquals(List.of("art"), response.tags());
        assertEquals("quiet places", response.notes());
    }

    @Test
    void updateMinePassesEveryField() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(preferenceService.updateUser(
            "User0001", List.of("food"), new BigDecimal("1.50"), "vegetarian", "ANY", "note"))
            .thenReturn(new UserPreference("User0001"));

        controller.updateMine(jwt, new PreferenceController.UpdateUserPreferenceRequest(
            List.of("food"), new BigDecimal("1.50"), "vegetarian", "ANY", "note"));

        verify(preferenceService).updateUser(
            "User0001", List.of("food"), new BigDecimal("1.50"), "vegetarian", "ANY", "note");
    }

    @Test
    void anotherUsersPreferenceNeverExposesNotes() {
        UserPreference preference = new UserPreference("User0002");
        preference.setNotes("private note");

        when(preferenceService.readUser("User0002")).thenReturn(preference);

        assertNull(controller.byUser("User0002").notes());
    }

    @Test
    void tripPreferenceUsesTheResolvedUserId() {
        TripPreference preference = new TripPreference("Trip0001");
        preference.setGoals(List.of("MUSEUMS", "FOOD"));

        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(preferenceService.getOrCreateTrip("Trip0001", "User0001")).thenReturn(preference);

        PreferenceController.TripPreferenceResponse response = controller.trip(jwt, "Trip0001");

        assertEquals(List.of("MUSEUMS", "FOOD"), response.goals());
    }

    @Test
    void updateTripPassesGoalsAndTags() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(preferenceService.updateTrip(
            "Trip0001", "User0001", List.of("FOOD"), List.of("hawker")))
            .thenReturn(new TripPreference("Trip0001"));

        controller.updateTrip(jwt, "Trip0001", new PreferenceController.UpdateTripPreferenceRequest(
            List.of("FOOD"), List.of("hawker")));

        verify(preferenceService).updateTrip("Trip0001", "User0001", List.of("FOOD"), List.of("hawker"));
    }
    
    @Test
    void summariseMineUsesTheResolvedUserId() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(preferenceService.summariseUser("User0001")).thenReturn(new UserPreference("User0001"));

        controller.summariseMine(jwt);

        verify(preferenceService).summariseUser("User0001");
    }

    @Test
    void updateMySummaryUsesTheResolvedUserId() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(preferenceService.updateUserSummary("User0001", "Loves museums."))
            .thenReturn(new UserPreference("User0001"));

        controller.updateMySummary(jwt, new PreferenceController.UpdateSummaryRequest("Loves museums."));

        verify(preferenceService).updateUserSummary("User0001", "Loves museums.");
    }

    @Test
    void summariseUsesTheResolvedUserId() {
        when(userService.getCurrentUserId(jwt)).thenReturn("User0001");
        when(preferenceService.summariseTrip("Trip0001", "User0001"))
            .thenReturn(new TripPreference("Trip0001"));

        controller.summarise(jwt, "Trip0001");

        verify(preferenceService).summariseTrip("Trip0001", "User0001");
    }
}
