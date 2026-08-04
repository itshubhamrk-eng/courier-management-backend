package com.courier.modules.master.application.command;

/**
 * Input to create or update a country.
 *
 * <p>One record for both, unlike the Branch module's Create/Update pair, because a master
 * row's create and update differ in exactly two fields and twenty-four near-identical
 * records would hide that rather than express it:
 *
 * <ul>
 *   <li>{@code code} is <b>create-only</b>. It is immutable once set — shipments and rate
 *       cards quote it — so on update the mapper passes null and the service keeps the
 *       stored value. Nothing is silently dropped: the update DTO has no code field to
 *       send in the first place.</li>
 *   <li>{@code expectedVersion} is <b>update-only</b>: the version last read, rejected
 *       with 409 if stale. Null on create.</li>
 * </ul>
 */
public record CountryCommand(
        String code,
        String name,
        String description,
        Integer displayOrder,
        String isoCode2,
        String isoCode3,
        String dialCode,
        String currencyCode,
        Long expectedVersion
) {
}
