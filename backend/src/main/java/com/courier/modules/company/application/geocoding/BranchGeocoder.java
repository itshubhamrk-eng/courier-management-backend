package com.courier.modules.company.application.geocoding;

import com.courier.modules.company.domain.Branch;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Builds a {@link GeocodingPort.Query} from a branch's address fields and applies a
 * result back onto it — the one piece of logic shared by every place a branch's location
 * gets filled in: {@code BranchServiceImpl.create/update} (only when the caller left both
 * fields blank) and {@code AddressDistanceService} (on demand, the moment a distance is
 * asked for and the branch turns out to have no location yet).
 */
@Slf4j
public final class BranchGeocoder {

    private BranchGeocoder() {
    }

    /**
     * Geocodes and sets {@code branch}'s latitude/longitude if the lookup finds a match.
     * Does nothing (including no call to {@code port}) if the branch already has both.
     *
     * @return true if the branch's coordinates were just set
     */
    public static boolean fillIfMissing(GeocodingPort port, Branch branch) {
        if (branch.getLatitude() != null && branch.getLongitude() != null) {
            return false;
        }
        GeocodingPort.Query query = new GeocodingPort.Query(
                branch.getAddressLine1(), branch.getTaluka(), branch.getCity(),
                branch.getDistrict(), branch.getState(), branch.getPostalCode(), branch.getCountry());
        return port.geocode(query).map(coordinates -> {
            // The column is DECIMAL(9,6); Nominatim can return more decimal places than that.
            BigDecimal latitude = coordinates.latitude().setScale(6, RoundingMode.HALF_UP);
            BigDecimal longitude = coordinates.longitude().setScale(6, RoundingMode.HALF_UP);
            branch.setLatitude(latitude);
            branch.setLongitude(longitude);
            log.info("Branch {} geocoded to {}, {}", branch.getBranchCode(), latitude, longitude);
            return true;
        }).orElse(false);
    }
}
