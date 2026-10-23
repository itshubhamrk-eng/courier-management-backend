package com.courier.modules.customerpricing.domain;

/**
 * Which destinations a location-less "catch-all" pricing zone covers: every destination
 * inside the home state that no other zone of the customer covers, or every destination
 * outside it.
 */
public enum FallbackScope {
    REST_OF_STATE,
    OUT_OF_STATE
}
