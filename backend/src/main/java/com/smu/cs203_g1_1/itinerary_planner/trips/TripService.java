package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.util.ID;
import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

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
    public List<Trip> getAllTrips() {
        return tripRepository.findAll();
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

    // return trip only if userId is owner or member. 404 if doesnt exist, 403 if user not part of trip
    @Transactional(readOnly = true)
    public Trip getTripForMember(String tripId, String userId) {
        Trip trip = getTrip(tripId);

        if (!isMember(trip, userId)) {
            log.warn("User id={} denied access to trip id={}", userId, tripId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this trip");
        }

        return trip;
    }

    @Transactional
    public Trip createTrip(String name, LocalDate startDate, LocalDate endDate, String ownerId) {

        validateDates(startDate, endDate);

        User owner = findUser(ownerId);

        String id = ID.generateUniqueID(tripRepository::existsById);

        Trip trip = new Trip(name, startDate, endDate, owner);
        trip.setId(id);

        // The owner is a participant too, so owned trips show up
        // through User.trips (mappedBy = "users").
        trip.addMember(owner);

        Trip saved = tripRepository.save(trip);
        log.info("Created trip id={} (ownerId={})", saved.getId(), ownerId);
        return saved;
    }

    @Transactional
    public Trip updateTrip(String userId, String tripId, String name, LocalDate startDate, LocalDate endDate) {

        validateDates(startDate, endDate);

        Trip trip = getTripForMember(tripId, userId);

        trip.setName(name);
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);

        Trip saved = tripRepository.save(trip);
        log.info("Updated trip id={}", tripId);
        return saved;
    }

    @Transactional
    public void deleteTrip(String userId, String tripId) {
        tripRepository.delete(getTripForMember(tripId, userId));
        log.info("Deleted trip id={}", tripId);
    }

    @Transactional
    public Trip addMember(String requesterId, String tripId, String userId) {
        Trip trip = getTripForMember(tripId, requesterId);
        trip.addMember(findUser(userId));
        Trip saved = tripRepository.save(trip);
        log.info("Added user id={} to trip id={}", userId, tripId);
        return saved;
    }

    @Transactional
    public Trip removeMember(String requesterId, String tripId, String userId) {
        Trip trip = getTripForMember(tripId, requesterId);
        User user = findUser(userId);

        if (!trip.getUsers().contains(user)) {
            log.warn("User id={} is not a member of trip id={}", userId, tripId);
            throw new ResponseStatusException( HttpStatus.NOT_FOUND, "User is not a member of this trip");
        }

        trip.removeMember(user);
        Trip saved = tripRepository.save(trip);
        log.info("Removed user id={} from trip id={}", userId, tripId);
        return saved;
    }

    private boolean isMember(Trip trip, String userId) {
        if (trip.getOwner().getId().equals(userId)) {
            return true;
        }
        return trip.getUsers().stream().anyMatch(member -> member.getId().equals(userId));
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
