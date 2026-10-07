package com.smu.cs203_g1_1.itinerary_planner.trips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smu.cs203_g1_1.itinerary_planner.geo.GeoNotFoundException;
import com.smu.cs203_g1_1.itinerary_planner.geo.GeoService;
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.PlaceDto;

@ExtendWith(MockitoExtension.class)
class LocationServiceOsmTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private GeoService geoService;

    @InjectMocks
    private LocationService locationService;

    @Test
    void returnsExistingLocationWithoutCallingGeoOrSave() {
        Location existing = new Location("Existing", "cafe", 1.0, 2.0, null);
        existing.setId(7L);
        when(locationRepository.findByOsmTypeAndOsmId("node", 123L)).thenReturn(Optional.of(existing));

        Location result = locationService.findOrCreateFromOsm("node", 123L);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(geoService);
        verify(locationRepository, never()).save(any(Location.class));
    }

    @Test
    void importsAndSavesNewLocationFromOsmPlace() {
        when(locationRepository.findByOsmTypeAndOsmId("node", 123L)).thenReturn(Optional.empty());
        PlaceDto place = new PlaceDto("node", 123L, "Marina Bay Sands",
                "Marina Bay Sands, Singapore", "tourism", "attraction",
                1.2834, 103.8607, null, "24/7", null, 0.8);
        when(geoService.lookup("node", 123L)).thenReturn(Optional.of(place));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location result = locationService.findOrCreateFromOsm("node", 123L);

        ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
        verify(locationRepository).save(captor.capture());
        Location saved = captor.getValue();
        assertThat(result).isSameAs(saved);
        assertThat(saved.getName()).isEqualTo("Marina Bay Sands");
        assertThat(saved.getCategory()).isEqualTo("attraction");
        assertThat(saved.getLatitude()).isEqualTo(1.2834);
        assertThat(saved.getLongitude()).isEqualTo(103.8607);
        assertThat(saved.getOsmType()).isEqualTo("node");
        assertThat(saved.getOsmId()).isEqualTo(123L);
        assertThat(saved.getAddress()).isEqualTo("Marina Bay Sands, Singapore");
        assertThat(saved.getOpeningHours()).isEqualTo("24/7");
    }

    @Test
    void throwsGeoNotFoundWhenLookupEmpty() {
        when(locationRepository.findByOsmTypeAndOsmId("node", 999L)).thenReturn(Optional.empty());
        when(geoService.lookup("node", 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.findOrCreateFromOsm("node", 999L))
                .isInstanceOf(GeoNotFoundException.class);

        verify(locationRepository, never()).save(any(Location.class));
    }
}
