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
import com.smu.cs203_g1_1.itinerary_planner.geo.dto.AddressDto;
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
        Location existing = new Location("Existing", "cafe", 1.0, 2.0, true);
        existing.setId("loc00001");
        existing.setCategoryGroup("food");
        existing.setIndoorClass("INDOOR");
        existing.setIndoorSource("MANUAL");
        existing.setIndoorConfidence(0.99);
        when(locationRepository.findByOsmTypeAndOsmId("node", 123L)).thenReturn(Optional.of(existing));

        Location result = locationService.findOrCreateFromOsm("node", 123L);

        assertThat(result).isSameAs(existing);
        assertThat(result.getCategoryGroup()).isEqualTo("food");
        assertThat(result.getIndoorClass()).isEqualTo("INDOOR");
        assertThat(result.getIndoorSource()).isEqualTo("MANUAL");
        assertThat(result.getIndoorConfidence()).isEqualTo(0.99);
        assertThat(result.getIsIndoor()).isTrue();
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
        assertThat(saved.getCategoryGroup()).isEqualTo("attraction");
        assertThat(saved.getIndoorClass()).isEqualTo("UNKNOWN");
        assertThat(saved.getIndoorSource()).isNull();
        assertThat(saved.getIsIndoor()).isNull();
    }

    @Test
    void copiesCountryCodeFromOsmAddress() {
        when(locationRepository.findByOsmTypeAndOsmId("node", 123L)).thenReturn(Optional.empty());
        AddressDto address = new AddressDto(null, "Shibuya", "Tokyo", "Tokyo", "150-0001", "Japan", "jp");
        PlaceDto place = new PlaceDto("node", 123L, "Shibuya Crossing",
                "Shibuya Crossing, Tokyo, Japan", "tourism", "attraction",
                35.6595, 139.7005, address, null, null, 0.8);
        when(geoService.lookup("node", 123L)).thenReturn(Optional.of(place));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location saved = locationService.findOrCreateFromOsm("node", 123L);

        assertThat(saved.getCountryCode()).isEqualTo("jp");
    }

    @Test
    void classifiesMuseumAsCultureIndoor() {
        Location saved = importPlace("tourism", "museum");

        assertThat(saved.getCategoryGroup()).isEqualTo("culture");
        assertThat(saved.getIndoorClass()).isEqualTo("INDOOR");
        assertThat(saved.getIndoorSource()).isEqualTo("OSM_TAG");
        assertThat(saved.getIndoorConfidence()).isEqualTo(0.9);
        assertThat(saved.getIsIndoor()).isTrue();
    }

    @Test
    void classifiesParkAsNatureOutdoor() {
        Location saved = importPlace("leisure", "park");

        assertThat(saved.getCategoryGroup()).isEqualTo("nature");
        assertThat(saved.getIndoorClass()).isEqualTo("OUTDOOR");
        assertThat(saved.getIndoorSource()).isEqualTo("OSM_TAG");
        assertThat(saved.getIndoorConfidence()).isEqualTo(0.85);
        assertThat(saved.getIsIndoor()).isFalse();
    }

    @Test
    void classifiesRestaurantAsFoodIndoor() {
        Location saved = importPlace("amenity", "restaurant");

        assertThat(saved.getCategoryGroup()).isEqualTo("food");
        assertThat(saved.getIndoorClass()).isEqualTo("INDOOR");
        assertThat(saved.getIndoorSource()).isEqualTo("OSM_TAG");
        assertThat(saved.getIsIndoor()).isTrue();
    }

    @Test
    void classifiesMallAsShoppingIndoor() {
        Location saved = importPlace("shop", "mall");

        assertThat(saved.getCategoryGroup()).isEqualTo("shopping");
        assertThat(saved.getIndoorClass()).isEqualTo("INDOOR");
        assertThat(saved.getIndoorSource()).isEqualTo("OSM_TAG");
        assertThat(saved.getIsIndoor()).isTrue();
    }

    @Test
    void classifiesAttractionAsUnknownGroupAttraction() {
        Location saved = importPlace("tourism", "attraction");

        assertThat(saved.getCategoryGroup()).isEqualTo("attraction");
        assertThat(saved.getIndoorClass()).isEqualTo("UNKNOWN");
        assertThat(saved.getIndoorSource()).isNull();
        assertThat(saved.getIndoorConfidence()).isEqualTo(0.3);
        assertThat(saved.getIsIndoor()).isNull();
    }

    @Test
    void infersKeyWhenCategoryIsMissing() {
        Location saved = importPlace(null, "museum");

        assertThat(saved.getCategoryGroup()).isEqualTo("culture");
        assertThat(saved.getIndoorClass()).isEqualTo("INDOOR");
        assertThat(saved.getIndoorSource()).isEqualTo("OSM_TAG");
        assertThat(saved.getIsIndoor()).isTrue();
    }

    @Test
    void leavesUnknownOrMissingTagsUnclassified() {
        Location saved = importPlace(null, null);

        assertThat(saved.getCategoryGroup()).isNull();
        assertThat(saved.getIndoorClass()).isEqualTo("UNKNOWN");
        assertThat(saved.getIndoorSource()).isNull();
        assertThat(saved.getIndoorConfidence()).isEqualTo(0.0);
        assertThat(saved.getIsIndoor()).isNull();
    }

    @Test
    void leavesUnknownTagPairUnclassified() {
        Location saved = importPlace("highway", "bus_stop");

        assertThat(saved.getCategoryGroup()).isNull();
        assertThat(saved.getIndoorClass()).isEqualTo("UNKNOWN");
        assertThat(saved.getIndoorSource()).isNull();
        assertThat(saved.getIsIndoor()).isNull();
    }

    @Test
    void throwsGeoNotFoundWhenLookupEmpty() {
        when(locationRepository.findByOsmTypeAndOsmId("node", 999L)).thenReturn(Optional.empty());
        when(geoService.lookup("node", 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.findOrCreateFromOsm("node", 999L))
                .isInstanceOf(GeoNotFoundException.class);

        verify(locationRepository, never()).save(any(Location.class));
    }

    @Test
    void createLocationIgnoresClientSentTrustFields() {
        Location forged = new Location("Fake museum", "museum", 1.28, 103.86, true);
        forged.setSeeded(true);
        forged.setOsmType("node");
        forged.setOsmId(123L);
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Location saved = locationService.createLocation(forged);

        assertThat(saved.isSeeded()).isFalse();
        assertThat(saved.getOsmType()).isNull();
        assertThat(saved.getOsmId()).isNull();
    }

    private Location importPlace(String category, String type) {
        when(locationRepository.findByOsmTypeAndOsmId("node", 123L)).thenReturn(Optional.empty());
        PlaceDto place = new PlaceDto("node", 123L, "Test Place",
                "Test Place, Singapore", category, type,
                1.0, 2.0, null, null, null, 0.5);
        when(geoService.lookup("node", 123L)).thenReturn(Optional.of(place));
        when(locationRepository.save(any(Location.class))).thenAnswer(invocation -> invocation.getArgument(0));

        return locationService.findOrCreateFromOsm("node", 123L);
    }
}
