package com.smu.cs203_g1_1.itinerary_planner.trips;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smu.cs203_g1_1.itinerary_planner.user.User;
import com.smu.cs203_g1_1.itinerary_planner.user.UserRepository;

@Service
public class TripService {

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
    public Trip getTrip(Long tripId) {
        return tripRepository
                .findById(tripId)
                .orElseThrow(() -> new ResponseStatusException( HttpStatus.NOT_FOUND, "Trip not found"));
    }

    @Transactional
    public Trip createTrip( String name, LocalDate startDate, LocalDate endDate, Long ownerId) {

        validateDates(startDate, endDate);

        User owner = findUser(ownerId);

        Trip trip = new Trip(name, startDate, endDate, owner);

        // The owner is a participant too, so owned trips show up
        // through User.trips (mappedBy = "users").
        trip.addMember(owner);

        return tripRepository.save(trip);
    }

    @Transactional
    public Trip updateTrip( Long tripId, String name, LocalDate startDate, LocalDate endDate) {

        validateDates(startDate, endDate);

        Trip trip = getTrip(tripId);

        trip.setName(name);
        trip.setStartDate(startDate);
        trip.setEndDate(endDate);

        return tripRepository.save(trip);
    }

    @Transactional
    public void deleteTrip(Long tripId) {
        tripRepository.delete(getTrip(tripId));
    }

    @Transactional
    public Trip addMember(Long tripId, Long userId) {
        Trip trip = getTrip(tripId);
        trip.addMember(findUser(userId));
        return tripRepository.save(trip);
    }

    @Transactional
    public Trip removeMember(Long tripId, Long userId) {
        Trip trip = getTrip(tripId);
        User user = findUser(userId);

        if (!trip.getUsers().contains(user)) {
            throw new ResponseStatusException( HttpStatus.NOT_FOUND, "User is not a member of this trip");
        }

        trip.removeMember(user);
        return tripRepository.save(trip);
    }

    private User findUser(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException( HttpStatus.NOT_FOUND, "User not found"));
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {

            throw new ResponseStatusException( HttpStatus.BAD_REQUEST, "Trip end date cannot be before start date");
        }
    }
}
