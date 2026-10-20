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
import com.smu.cs203_g1_1.itinerary_planner.util.ID;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    // constructor
    public TripService( TripRepository tripRepository, UserRepository userRepository) {
        this.tripRepository = tripRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Trip> getTripsForUser(String userId) {
        return tripRepository.findDistinctByOwnerIdOrUsersId(userId, userId);
    }

    @Transactional(readOnly = true)
    public Trip getTrip(String tripId) {
        return tripRepository
                .findById(tripId)
                .orElseThrow(() -> {
                    log.warn("Trip not found: id={}", tripId);
                    return new ResponseStatusException( HttpStatus.NOT_FOUND, "Trip not found");
                });
    }

    /*
     * Loads the trip and verifies the user holds at least the required role on it.
     * 404 if the trip doesn't exist, 403 if the user is not on it or their role is too low.
     * Used by ActivityService too.
     */
    @Transactional(readOnly = true)
    public Trip requireTripRole(String tripId, String userId, TripRole required) {
        Trip trip = getTrip(tripId);

        TripRole actual = trip.findRole(userId).orElse(null);
        if (actual == null || !actual.atLeast(required)) {
            log.warn("Forbidden: user id={} (role={}) needs {} on trip id={}", userId, actual, required, tripId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to perform this action on this trip");
        }

        return trip;
    }

    // return trip only if the user is on it (any role). 404 if doesnt exist, 403 if user not part of trip
    @Transactional(readOnly = true)
    public Trip getTripForMember(String tripId, String userId) {
        return requireTripRole(tripId, userId, TripRole.VIEWER);
    }

    @Transactional
    public Trip createTrip(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String ownerId,
            List<String> countries) {

        validateDates(startDate, endDate);

        User owner = findUser(ownerId);

        String id = ID.generateUniqueID(tripRepository::existsById);

        Trip trip = new Trip(name, startDate, endDate, owner);
        trip.setId(id);
        trip.setCountries(countries);

        // The owner is also a member of the trip.
        trip.addMember(owner);

        Trip saved = tripRepository.save(trip);
        log.info("Created trip id={} (ownerId={})", saved.getId(), ownerId);

        return saved;
    }

    // Owner & Editor
    @Transactional
    public Trip updateTrip(
            String userId,
            String tripId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            List<String> countries) {

        validateDates(startDate, endDate);

        // Owner has a higher role than Editor, so they will still have access
        Trip trip = requireTripRole(tripId, userId, TripRole.EDITOR);

        trip.setName(name);
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);

        // Omitted/null means "leave countries unchanged".
        // An explicitly empty list is rejected by Trip.setCountries().
        if (countries != null) {
            try {
                trip.setCountries(countries);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage()
                );
            }
        }

        Trip saved = tripRepository.save(trip);
        log.info("Updated trip id={}", tripId);
        return saved;
    }

    // OWNER only
    @Transactional
    public void deleteTrip(String userId, String tripId) {
        tripRepository.delete(requireTripRole(tripId, userId, TripRole.OWNER));
        log.info("Deleted trip id={}", tripId);
    }

    // ---------------------------------------------------------------
    // Members (OWNER only)
    // ---------------------------------------------------------------

    // Original signature kept for existing callers; new members default to VIEWER.
    @Transactional
    public Trip addMember(String requesterId, String tripId, String userId) {
        return addMember(requesterId, tripId, userId, null);
    }

    @Transactional
    public Trip addMember(String requesterId, String tripId, String userId, TripRole role) {
        Trip trip = requireTripRole(tripId, requesterId, TripRole.OWNER);
        TripRole assigned = (role == null) ? TripRole.VIEWER : role;

        if (assigned == TripRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A trip can only have one owner");
        }

        User target = findUser(userId);
        if (trip.hasMember(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User is already a member of this trip; change their role instead");
        }

        trip.addMember(target, assigned);
        Trip saved = tripRepository.save(trip);
        log.info("Added user id={} to trip id={} as {}", userId, tripId, assigned);
        return saved;
    }

    @Transactional
    public Trip changeMemberRole(String requesterId, String tripId, String userId, TripRole role) {
        Trip trip = requireTripRole(tripId, requesterId, TripRole.OWNER);

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
        if (!trip.hasMember(target)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a member of this trip");
        }

        trip.setMemberRole(target, role);
        Trip saved = tripRepository.save(trip);
        log.info("Changed user id={} to {} on trip id={}", userId, role, tripId);
        return saved;
    }

    @Transactional
    public Trip removeMember(String requesterId, String tripId, String userId) {
        Trip trip = requireTripRole(tripId, requesterId, TripRole.OWNER);
        User user = findUser(userId);

        if (trip.isOwner(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The trip owner cannot be removed");
        }
        if (!trip.hasMember(user)) {
            log.warn("User id={} is not a member of trip id={}", userId, tripId);
            throw new ResponseStatusException( HttpStatus.NOT_FOUND, "User is not a member of this trip");
        }

        trip.removeMember(user);
        Trip saved = tripRepository.save(trip);
        log.info("Removed user id={} from trip id={}", userId, tripId);
        return saved;
    }

    private User findUser(String userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found: id={}", userId);
                    return new ResponseStatusException( HttpStatus.NOT_FOUND, "User not found");
                });
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {

            log.warn("Rejected trip: endDate {} is before startDate {}", endDate, startDate);
            throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Trip end date cannot be before start date");
        }
    }
}