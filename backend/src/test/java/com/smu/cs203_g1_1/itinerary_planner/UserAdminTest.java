package com.smu.cs203_g1_1.itinerary_planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.trips.Trip;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRepository;
import com.smu.cs203_g1_1.itinerary_planner.trips.TripRole;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitation;
import com.smu.cs203_g1_1.itinerary_planner.trips.invitations.TripInvitationRepository;
import com.smu.cs203_g1_1.itinerary_planner.user.AdminUpdateUserRequest;
import com.smu.cs203_g1_1.itinerary_planner.user.AdminUserSummary;
import com.smu.cs203_g1_1.itinerary_planner.user.AppRole;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;
import com.smu.cs203_g1_1.itinerary_planner.user.UserService;

/** CGT-77: admin list / edit / delete and the admin authorization check. */
@ExtendWith(MockitoExtension.class)
class UserAdminTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 1);
    private static final LocalDate END = LocalDate.of(2026, 11, 5);

    @Mock
    private UserRepository userRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private TripInvitationRepository invitationRepository;

    private UserService userService;
    private User admin;
    private User regular;
    private User other;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, tripRepository, invitationRepository, "clerk-boot");

        admin = user("Admin001", "clerk-admin", AppRole.ADMIN);
        regular = user("User0001", "clerk-user", AppRole.USER);
        other = user("User0002", "clerk-other", AppRole.USER);

        lenient().when(userRepository.findById("Admin001")).thenReturn(Optional.of(admin));
        lenient().when(userRepository.findById("User0001")).thenReturn(Optional.of(regular));
        lenient().when(userRepository.findById("User0002")).thenReturn(Optional.of(other));
        lenient().when(userRepository.findById("Nobody01")).thenReturn(Optional.empty());
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static User user(String id, String clerkId, AppRole role) {
        User u = new User(clerkId, "Name " + id);
        u.setId(id);
        u.setEmail(id.toLowerCase() + "@example.com");
        u.setRole(role);
        return u;
    }

    private static void assertStatus(HttpStatus expected, Runnable action) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(expected, HttpStatus.valueOf(e.getStatusCode().value()));
    }

    // ----- admin role (CGT-65) / bootstrap -----

    @Test
    void newUsersDefaultToUserRole() {
        when(userRepository.findByClerkUserId("clerk-new")).thenReturn(Optional.empty());

        User created = userService.findOrCreateUser("clerk-new", "New", "new@example.com");

        assertEquals(AppRole.USER, created.getRole());
        assertFalse(created.isAdmin());
    }

    @Test
    void configuredClerkIdIsPromotedToAdminOnFirstLogin() {
        when(userRepository.findByClerkUserId("clerk-boot")).thenReturn(Optional.empty());

        User created = userService.findOrCreateUser("clerk-boot", "Boss", "boss@example.com");

        assertTrue(created.isAdmin());
    }

    @Test
    void configuredClerkIdIsPromotedWhenAnExistingUserLogsIn() {
        User existing = user("Boss0001", "clerk-boot", AppRole.USER);
        when(userRepository.findByClerkUserId("clerk-boot")).thenReturn(Optional.of(existing));

        User result = userService.findOrCreateUser("clerk-boot", "Boss", existing.getEmail());

        assertTrue(result.isAdmin());
        verify(userRepository).save(existing);
    }

    // ----- authorization check (CGT-76) -----

    @Test
    void nonAdminIsForbiddenFromEveryAdminOperation() {
        assertStatus(HttpStatus.FORBIDDEN, () -> userService.getAllUsers(regular));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> userService.adminUpdateUser(regular, "User0002", new AdminUpdateUserRequest("Hacked", null)));
        assertStatus(HttpStatus.FORBIDDEN, () -> userService.adminDeleteUser(regular, "User0002"));

        assertEquals("Name User0002", other.getDisplayName());
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).delete(any());
    }

    // ----- list (CGT-73) -----

    @Test
    void adminCanListAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(admin, regular, other));

        List<AdminUserSummary> users = userService.getAllUsers(admin);

        assertEquals(3, users.size());
        assertEquals("User0001", users.get(1).id());
        assertEquals("user0001@example.com", users.get(1).email());
        assertEquals(AppRole.USER, users.get(1).role());
    }

    // ----- edit (CGT-74) -----

    @Test
    void adminCanChangeDisplayNameAndRole() {
        AdminUserSummary result = userService.adminUpdateUser(admin, "User0001",
                new AdminUpdateUserRequest("  Renamed  ", AppRole.ADMIN));

        assertEquals("Renamed", result.displayName());
        assertEquals(AppRole.ADMIN, result.role());
        assertEquals("clerk-user", regular.getClerkUserId());
        assertEquals("user0001@example.com", regular.getEmail());
        verify(userRepository).save(regular);
    }

    @Test
    void nullFieldsAreLeftUnchanged() {
        userService.adminUpdateUser(admin, "User0001", new AdminUpdateUserRequest(null, null));

        assertEquals("Name User0001", regular.getDisplayName());
        assertEquals(AppRole.USER, regular.getRole());
    }

    @Test
    void blankDisplayNameIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> userService.adminUpdateUser(admin, "User0001", new AdminUpdateUserRequest("   ", null)));
        assertEquals("Name User0001", regular.getDisplayName());
    }

    @Test
    void editingAnUnknownUserIsNotFound() {
        assertThrows(UserNotFoundException.class,
                () -> userService.adminUpdateUser(admin, "Nobody01", new AdminUpdateUserRequest("X", null)));
    }

    @Test
    void adminCannotRemoveTheirOwnAdminRole() {
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> userService.adminUpdateUser(admin, "Admin001", new AdminUpdateUserRequest(null, AppRole.USER)));
        assertTrue(admin.isAdmin());
    }

    // ----- delete (CGT-75) -----

    @Test
    void adminCanDeleteAUserWithNoTrips() {
        when(tripRepository.findDistinctByOwnerIdOrUsersId("User0001", "User0001")).thenReturn(List.of());
        when(invitationRepository.findBySenderIdOrReceiverId("User0001", "User0001")).thenReturn(List.of());

        userService.adminDeleteUser(admin, "User0001");

        verify(userRepository).delete(regular);
    }

    @Test
    void deleteRemovesTheUsersMembershipsAndInvitations() {
        Trip trip = new Trip("Japan", START, END, other);
        trip.addMember(regular, TripRole.EDITOR);
        TripInvitation invite = trip.invite(other, regular, TripRole.VIEWER);
        when(tripRepository.findDistinctByOwnerIdOrUsersId("User0001", "User0001")).thenReturn(List.of(trip));
        when(invitationRepository.findBySenderIdOrReceiverId("User0001", "User0001")).thenReturn(List.of(invite));

        userService.adminDeleteUser(admin, "User0001");

        assertFalse(trip.hasMember(regular));
        assertFalse(trip.getInvitations().contains(invite));
        verify(invitationRepository).deleteAll(List.of(invite));
        verify(userRepository).delete(regular);
    }

    @Test
    void deleteIsRefusedWhileTheUserOwnsTrips() {
        Trip trip = new Trip("Japan", START, END, regular);
        when(tripRepository.findDistinctByOwnerIdOrUsersId("User0001", "User0001")).thenReturn(List.of(trip));

        assertStatus(HttpStatus.CONFLICT, () -> userService.adminDeleteUser(admin, "User0001"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void adminCannotDeleteThemselves() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> userService.adminDeleteUser(admin, "Admin001"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deletingAnUnknownUserIsNotFound() {
        assertThrows(UserNotFoundException.class, () -> userService.adminDeleteUser(admin, "Nobody01"));
    }
}