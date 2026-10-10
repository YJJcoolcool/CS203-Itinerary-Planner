package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    public TripService(TripRepository tripRepository, UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }

    // ---------------------------------------------------------------
    // Authorization
    // ---------------------------------------------------------------

    /**
     * Loads the trip and verifies the authenticated user holds at least the
     * required role on it. Throws 403 otherwise. Reused by ActivityService.
     */
    @Transactional(readOnly = true)
    public Trip requireTripRole(Long tripId, String clerkUserId, TripRole required) {
        User actor = currentUser(clerkUserId);
        Trip trip = loadTrip(tripId);

        TripRole actual = trip.findMember(actor).map(TripMember::getRole).orElse(null);
        if (actual == null || !actual.atLeast(required)) {
            log.warn("Forbidden: user id={} (role={}) needs {} on trip id={}",
                    actor.getId(), actual, required, tripId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action on this trip");
        }
        return trip;
    }

    // ---------------------------------------------------------------
    // Trips
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Trip> getTripsForUser(String clerkUserId) {
        return tripRepository.findByMembers_User_Id(currentUser(clerkUserId).getId());
    }

    @Transactional(readOnly = true)
    public Trip getTrip(Long tripId, String clerkUserId) {
        return requireTripRole(tripId, clerkUserId, TripRole.VIEWER);
    }

    @Transactional
    public Trip createTrip(String name, LocalDate startDate, LocalDate endDate, String clerkUserId) {
        validateDates(startDate, endDate);
        User owner = currentUser(clerkUserId);

        Trip trip = new Trip(name, startDate, endDate, owner);
        trip.addMember(owner, TripRole.OWNER);

        Trip saved = tripRepository.save(trip);
        log.info("Created trip id={} (ownerId={})", saved.getId(), owner.getId());
        return saved;
    }

    @Transactional
    public Trip updateTrip(Long tripId, String clerkUserId, String name, LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);
        Trip trip = requireTripRole(tripId, clerkUserId, TripRole.OWNER);

        trip.setName(name);
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);

        Trip saved = tripRepository.save(trip);
        log.info("Updated trip id={}", tripId);
        return saved;
    }

    @Transactional
    public void deleteTrip(Long tripId, String clerkUserId) {
        Trip trip = requireTripRole(tripId, clerkUserId, TripRole.OWNER);
        tripRepository.delete(trip);
        log.info("Deleted trip id={}", tripId);
    }

    // ---------------------------------------------------------------
    // Members (owner only)
    // ---------------------------------------------------------------

    @Transactional
    public Trip addMember(Long tripId, String clerkUserId, Long userId, TripRole role) {
        Trip trip = requireTripRole(tripId, clerkUserId, TripRole.OWNER);
        TripRole assigned = (role == null) ? TripRole.VIEWER : role;

        if (assigned == TripRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A trip can only have one owner");
        }

        User target = findUser(userId);
        if (trip.findMember(target).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User is already a member of this trip; change their role instead");
        }

        trip.addMember(target, assigned);
        Trip saved = tripRepository.save(trip);
        log.info("Added user id={} to trip id={} as {}", userId, tripId, assigned);
        return saved;
    }

    @Transactional
    public Trip changeMemberRole(Long tripId, String clerkUserId, Long userId, TripRole role) {
        Trip trip = requireTripRole(tripId, clerkUserId, TripRole.OWNER);

        if (role == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role is required");
        }
        if (role == TripRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A trip can only have one owner");
        }

        User target = findUser(userId);
        if (trip.isOwner(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The owner's role cannot be changed");
        }

        TripMember member = trip.findMember(target).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a member of this trip"));
        member.setRole(role);

        Trip saved = tripRepository.save(trip);
        log.info("Changed user id={} to {} on trip id={}", userId, role, tripId);
        return saved;
    }

    @Transactional
    public Trip removeMember(Long tripId, String clerkUserId, Long userId) {
        Trip trip = requireTripRole(tripId, clerkUserId, TripRole.OWNER);
        User target = findUser(userId);

        if (trip.isOwner(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The trip owner cannot be removed");
        }
        if (trip.findMember(target).isEmpty()) {
            log.warn("User id={} is not a member of trip id={}", userId, tripId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a member of this trip");
        }

        trip.removeMember(target);
        Trip saved = tripRepository.save(trip);
        log.info("Removed user id={} from trip id={}", userId, tripId);
        return saved;
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Trip loadTrip(Long tripId) {
        return tripRepository.findById(tripId).orElseThrow(() -> {
            log.warn("Trip not found: id={}", tripId);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found");
        });
    }

    private User currentUser(String clerkUserId) {
        return userRepository.findByClerkUserId(clerkUserId).orElseThrow(() -> {
            log.warn("Authenticated user has no profile: clerkUserId={}", clerkUserId);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        });
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> {
            log.warn("User not found: id={}", userId);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        });
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            log.warn("Rejected trip: endDate {} is before startDate {}", endDate, startDate);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip end date cannot be before start date");
        }
    }
}