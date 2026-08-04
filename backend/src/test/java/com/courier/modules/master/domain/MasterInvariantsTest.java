package com.courier.modules.master.domain;

import com.courier.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The per-list rules that {@code applySpecificInvariants} enforces. */
class MasterInvariantsTest {

    @Nested
    @DisplayName("country")
    class Countries {

        @Test
        @DisplayName("ISO and currency codes are uppercased and length checked")
        void isoCodes() {
            Country country = new Country();
            country.setCode("IN");
            country.setName("India");
            country.setIsoCode2("in");
            country.setIsoCode3("ind");
            country.setCurrencyCode("inr");
            country.applyInvariants();

            assertThat(country.getIsoCode2()).isEqualTo("IN");
            assertThat(country.getIsoCode3()).isEqualTo("IND");
            assertThat(country.getCurrencyCode()).isEqualTo("INR");

            country.setIsoCode2("IND");
            assertThatThrownBy(country::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("exactly 2 characters");
        }
    }

    @Nested
    @DisplayName("hierarchy parents")
    class Parents {

        @Test
        @DisplayName("every level below country refuses a missing parent")
        void parentRequired() {
            assertThatThrownBy(() -> named(new State(), "MH").applyInvariants())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("belong to a country");
            assertThatThrownBy(() -> named(new District(), "PUNE_D").applyInvariants())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("belong to a state");
            assertThatThrownBy(() -> named(new City(), "PUNE").applyInvariants())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("belong to a district");
            assertThatThrownBy(() -> named(new Area(), "KOTHRUD").applyInvariants())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("belong to a city");

            Pincode pincode = named(new Pincode(), "411038");
            assertThatThrownBy(pincode::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("belong to an area");
        }
    }

    @Nested
    @DisplayName("city tier")
    class Tiers {

        @Test
        @DisplayName("a tier is uppercased and must be one of the four")
        void tierValidated() {
            City city = named(new City(), "PUNE");
            city.setDistrictId(UUID.randomUUID());
            city.setCityTier("tier_1");
            city.applyInvariants();
            assertThat(city.getCityTier()).isEqualTo("TIER_1");

            city.setCityTier("TIER_9");
            assertThatThrownBy(city::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid city tier");
        }
    }

    @Nested
    @DisplayName("pincode")
    class Pincodes {

        @Test
        @DisplayName("the code must be digits, unlike every other master list")
        void digitsOnly() {
            Pincode pincode = pincode("PUNE_411038");
            assertThatThrownBy(pincode::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("4 to 10 digits");

            Pincode valid = pincode("411038");
            valid.applyInvariants();
            assertThat(valid.getCode()).isEqualTo("411038");
        }

        @Test
        @DisplayName("marking a pincode unserviceable folds every availability flag down")
        void unserviceableFoldsFlags() {
            // A pincode nobody delivers to cannot offer cash on delivery. Folding rather
            // than refusing keeps "stop servicing this" a one-field edit.
            Pincode pincode = pincode("411038");
            pincode.setServiceable(false);
            pincode.applyInvariants();

            assertThat(pincode.isCodAvailable()).isFalse();
            assertThat(pincode.isPrepaidAvailable()).isFalse();
            assertThat(pincode.isPickupAvailable()).isFalse();
        }
    }

    @Nested
    @DisplayName("payment mode")
    class PaymentModes {

        @Test
        @DisplayName("collecting at both booking and delivery is refused")
        void cannotCollectTwice() {
            PaymentMode mode = named(new PaymentMode(), "PAID");
            mode.setCollectAtBooking(true);
            mode.setCollectAtDelivery(true);

            assertThatThrownBy(mode::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("both at booking and at delivery");
        }

        @Test
        @DisplayName("cash on delivery must collect at delivery")
        void codCollectsAtDelivery() {
            PaymentMode mode = named(new PaymentMode(), "COD");
            mode.setCashOnDelivery(true);

            assertThatThrownBy(mode::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must collect at delivery");
        }

        @Test
        @DisplayName("a billed mode cannot also take cash")
        void billedTakesNoCash() {
            PaymentMode mode = named(new PaymentMode(), "TBB");
            mode.setRequiresCreditAccount(true);
            mode.setCollectAtDelivery(true);

            assertThatThrownBy(mode::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("settles on an invoice");
        }

        @Test
        @DisplayName("the four canonical modes are all valid")
        void canonicalFour() {
            PaymentMode paid = named(new PaymentMode(), "PAID");
            paid.setCollectAtBooking(true);

            PaymentMode toPay = named(new PaymentMode(), "TO_PAY");
            toPay.setCollectAtDelivery(true);

            PaymentMode tbb = named(new PaymentMode(), "TBB");
            tbb.setRequiresCreditAccount(true);

            PaymentMode cod = named(new PaymentMode(), "COD");
            cod.setCollectAtDelivery(true);
            cod.setCashOnDelivery(true);

            paid.applyInvariants();
            toPay.applyInvariants();
            tbb.applyInvariants();
            cod.applyInvariants();
        }
    }

    @Nested
    @DisplayName("route")
    class Routes {

        @Test
        @DisplayName("the two ends must differ")
        void endsDiffer() {
            UUID branch = UUID.randomUUID();
            Route route = named(new Route(), "SELF");
            route.setBookingBranchId(branch);
            route.setDeliveryBranchId(branch);

            assertThatThrownBy(route::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("must be different");
        }

        @Test
        @DisplayName("negative distance and transit are refused; a null transit defaults to one day")
        void numbersValidated() {
            Route route = route();
            route.setDistanceKm(new BigDecimal("-1.00"));
            assertThatThrownBy(route::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Distance cannot be negative");

            Route negativeTransit = route();
            negativeTransit.setTransitDays(-1);
            assertThatThrownBy(negativeTransit::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Transit days cannot be negative");

            Route defaulted = route();
            defaulted.setTransitDays(null);
            defaulted.applyInvariants();
            assertThat(defaulted.getTransitDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("zero transit days is allowed - that is a same-day lane")
        void sameDayIsValid() {
            Route route = route();
            route.setTransitDays(0);
            route.applyInvariants();

            assertThat(route.getTransitDays()).isZero();
        }
    }

    @Nested
    @DisplayName("vehicle and package types")
    class Catalogues {

        @Test
        @DisplayName("a zero or negative capacity is refused, but null is allowed")
        void capacities() {
            VehicleType vehicle = named(new VehicleType(), "TRUCK");
            vehicle.setCapacityKg(BigDecimal.ZERO);
            assertThatThrownBy(vehicle::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Capacity (kg)");

            VehicleType unspecified = named(new VehicleType(), "TRUCK");
            unspecified.applyInvariants();
            assertThat(unspecified.getCapacityKg()).isNull();

            VehicleType noWheels = named(new VehicleType(), "TRUCK");
            noWheels.setWheelCount(0);
            assertThatThrownBy(noWheels::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Wheel count");
        }

        @Test
        @DisplayName("a package type's dimensions must be positive when given")
        void dimensions() {
            PackageType box = named(new PackageType(), "BOX");
            box.setDefaultLengthCm(new BigDecimal("-1.00"));

            assertThatThrownBy(box::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Default length");
        }
    }

    @Nested
    @DisplayName("service type")
    class ServiceTypes {

        @Test
        @DisplayName("zero delivery days is same day and is allowed; negative is not")
        void deliveryDays() {
            ServiceType sameDay = named(new ServiceType(), "SAME_DAY");
            sameDay.setDeliveryDays(0);
            sameDay.applyInvariants();
            assertThat(sameDay.getDeliveryDays()).isZero();

            ServiceType impossible = named(new ServiceType(), "TIME_TRAVEL");
            impossible.setDeliveryDays(-1);
            assertThatThrownBy(impossible::applyInvariants)
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cannot be negative");
        }
    }

    private static <E extends MasterDataEntity> E named(E entity, String code) {
        entity.setCode(code);
        entity.setName(code);
        return entity;
    }

    private static Pincode pincode(String code) {
        Pincode pincode = named(new Pincode(), code);
        pincode.setAreaId(UUID.randomUUID());
        return pincode;
    }

    private static Route route() {
        Route route = named(new Route(), "PNQ_BOM");
        route.setBookingBranchId(UUID.randomUUID());
        route.setDeliveryBranchId(UUID.randomUUID());
        return route;
    }
}
