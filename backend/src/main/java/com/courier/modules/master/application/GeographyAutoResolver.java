package com.courier.modules.master.application;

import com.courier.modules.master.application.port.PincodePostalLookupProvider.PostOffice;
import com.courier.modules.master.domain.Area;
import com.courier.modules.master.domain.AreaRepository;
import com.courier.modules.master.domain.City;
import com.courier.modules.master.domain.CityRepository;
import com.courier.modules.master.domain.Country;
import com.courier.modules.master.domain.CountryRepository;
import com.courier.modules.master.domain.District;
import com.courier.modules.master.domain.DistrictRepository;
import com.courier.modules.master.domain.GlobalMasters;
import com.courier.modules.master.domain.MasterDataEntity;
import com.courier.modules.master.domain.State;
import com.courier.modules.master.domain.StateRepository;
import com.courier.modules.master.infrastructure.MasterTable;
import com.courier.modules.master.infrastructure.MasterUniquenessChecker;
import com.courier.shared.company.CompanyContext;
import com.courier.shared.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns a postal-lookup match into a real Area, creating whatever ancestors are missing.
 *
 * <p>Deliberately <b>not</b> a call through {@code CountryService}/{@code StateService}/
 * etc: those are {@code SUPER_ADMIN}-only writers (decision in {@code
 * AI_CONTEXT.md}'s Global masters section), and this resolver runs for a {@code
 * COMPANY_ADMIN} caller too — the same "onboarding routinely needs a row the platform
 * catalogue does not have yet" reasoning {@link PincodeServiceImpl} already documents for
 * Pincode itself, extended one step further up the chain because a fresh company's map is
 * genuinely empty above the pincode too. It writes the same tables, through the same
 * repositories, applying the same {@code applyInvariants()} every service create does —
 * only the {@code @PreAuthorize} boundary is skipped, because this is reached only from
 * {@link PincodeServiceImpl#lookupPostalArea}, which is itself gated.
 *
 * <p>Matching is by name, case-insensitive, within the parent — the postal directory has
 * no stable id this module could key on. A miss creates a new row with a code derived
 * from the name, de-duplicated against {@code (company_id, code)} the same way a human
 * typing one twice would collide and retype it.
 */
@Service
@RequiredArgsConstructor
public class GeographyAutoResolver {

    private final CountryRepository countries;
    private final StateRepository states;
    private final DistrictRepository districts;
    private final CityRepository cities;
    private final AreaRepository areas;
    private final MasterUniquenessChecker uniqueness;

    @Transactional
    public GeographyMatch resolveArea(PostOffice postOffice) {
        return CompanyContext.runAs(GlobalMasters.PLATFORM_COMPANY_ID, () -> {
            UUID companyId = GlobalMasters.PLATFORM_COMPANY_ID;

            Country country = findOrCreateCountry(companyId, require(postOffice.country(), "country"));
            State state = findOrCreateState(companyId, country.getId(), require(postOffice.state(), "state"));
            District district = findOrCreateDistrict(companyId, state.getId(),
                    require(postOffice.district(), "district"));
            String cityName = blankOr(postOffice.division(), postOffice.district());
            City city = findOrCreateCity(companyId, district.getId(), require(cityName, "city"));
            Area area = findOrCreateArea(companyId, city.getId(), require(postOffice.name(), "area"));
            return new GeographyMatch(area, city, district, state, country);
        });
    }

    /** The full chain resolved for one postal match — the create form shows all of it,
     *  not just the leaf, so an operator can see what got auto-created. */
    public record GeographyMatch(Area area, City city, District district, State state, Country country) {
    }

    private Country findOrCreateCountry(UUID companyId, String name) {
        return countries.findByCompanyIdAndNameIgnoreCase(companyId, name).orElseGet(() -> {
            Country country = new Country();
            country.setCode(uniqueCode(MasterTable.COUNTRIES, companyId, name));
            country.setName(name);
            if ("INDIA".equalsIgnoreCase(name)) {
                country.setIsoCode2("IN");
                country.setIsoCode3("IND");
                country.setDialCode("+91");
                country.setCurrencyCode("INR");
            }
            country.applyInvariants();
            return countries.saveAndFlush(country);
        });
    }

    private State findOrCreateState(UUID companyId, UUID countryId, String name) {
        return states.findByCompanyIdAndCountryIdAndNameIgnoreCase(companyId, countryId, name)
                .orElseGet(() -> {
                    State state = new State();
                    state.setCode(uniqueCode(MasterTable.STATES, companyId, name));
                    state.setName(name);
                    state.setCountryId(countryId);
                    state.applyInvariants();
                    return states.saveAndFlush(state);
                });
    }

    private District findOrCreateDistrict(UUID companyId, UUID stateId, String name) {
        return districts.findByCompanyIdAndStateIdAndNameIgnoreCase(companyId, stateId, name)
                .orElseGet(() -> {
                    District district = new District();
                    district.setCode(uniqueCode(MasterTable.DISTRICTS, companyId, name));
                    district.setName(name);
                    district.setStateId(stateId);
                    district.applyInvariants();
                    return districts.saveAndFlush(district);
                });
    }

    private City findOrCreateCity(UUID companyId, UUID districtId, String name) {
        return cities.findByCompanyIdAndDistrictIdAndNameIgnoreCase(companyId, districtId, name)
                .orElseGet(() -> {
                    City city = new City();
                    city.setCode(uniqueCode(MasterTable.CITIES, companyId, name));
                    city.setName(name);
                    city.setDistrictId(districtId);
                    city.applyInvariants();
                    return cities.saveAndFlush(city);
                });
    }

    private Area findOrCreateArea(UUID companyId, UUID cityId, String name) {
        return areas.findByCompanyIdAndCityIdAndNameIgnoreCase(companyId, cityId, name)
                .orElseGet(() -> {
                    Area area = new Area();
                    area.setCode(uniqueCode(MasterTable.AREAS, companyId, name));
                    area.setName(name);
                    area.setCityId(cityId);
                    area.applyInvariants();
                    return areas.saveAndFlush(area);
                });
    }

    /** A code derived from the name, retried with a numeric suffix until it is free. */
    private String uniqueCode(String table, UUID companyId, String name) {
        String base = MasterDataEntity.normaliseCode(name);
        if (base.length() > 50) {
            base = base.substring(0, 50);
        }
        String candidate = base;
        int suffix = 2;
        while (uniqueness.isCodeTaken(table, companyId, null, candidate)) {
            String tail = "_" + suffix;
            int maxBase = 50 - tail.length();
            candidate = (base.length() > maxBase ? base.substring(0, maxBase) : base) + tail;
            suffix++;
        }
        return candidate;
    }

    private static String blankOr(String preferred, String fallback) {
        return (preferred == null || preferred.isBlank()) ? fallback : preferred;
    }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(
                    "Postal lookup returned no %s for this pincode — pick an Area manually."
                            .formatted(label));
        }
        return value.trim();
    }
}
