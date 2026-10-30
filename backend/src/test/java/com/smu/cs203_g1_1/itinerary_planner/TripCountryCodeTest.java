package com.smu.cs203_g1_1.itinerary_planner;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripService;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class TripCountryCodeTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 5);
    private static final String OWNER_ID = "OwnerUs1";

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserRepository userRepository;

    private TripService tripService;
    private User owner;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, userRepository);
        owner = new User("clerk-owner", "Owner");
        owner.setId(OWNER_ID);

        lenient().when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        lenient().when(tripRepository.save(any(Trip.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void omittedCountryDefaultsToSingapore() {
        Trip trip = tripService.createTrip("Trip", START, END, OWNER_ID, null);

        assertEquals("sg", trip.getCountryCode());
    }

    @Test
    void blankCountryDefaultsToSingapore() {
        Trip trip = tripService.createTrip("Trip", START, END, OWNER_ID, "   ");

        assertEquals("sg", trip.getCountryCode());
    }

    @Test
    void uppercaseCountryNormalisesToLowercase() {
        Trip trip = tripService.createTrip("Japan", START, END, OWNER_ID, "JP");

        assertEquals("jp", trip.getCountryCode());
    }

    @Test
    void invalidCountryCodesAreRejected() {
        Trip trip = new Trip("Trip", START, END, owner);

        for (String invalid : new String[] {"sgp", "1x", " ", ""}) {
            assertThrows(IllegalArgumentException.class,
                    () -> trip.setCountryCode(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> trip.setCountryCode(null));
    }

    @Test
    void updateWithNullLeavesTheCountryUnchanged() {
        Trip trip = tripService.createTrip("Japan", START, END, OWNER_ID, "jp");
        when(tripRepository.findById(trip.getId())).thenReturn(Optional.of(trip));

        Trip updated = tripService.updateTrip(
                OWNER_ID, trip.getId(), "Renamed", START, END, null);

        assertEquals("jp", updated.getCountryCode());
    }
}
