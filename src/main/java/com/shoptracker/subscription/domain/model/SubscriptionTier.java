package com.shoptracker.subscription.domain.model;

public enum SubscriptionTier {
    NONE("none"),
    BASIC("basic"),
    PREMIUM("premium");

    private final String value;
    SubscriptionTier(String value) {this.value = value;}
    public String getValue() { return value; }

    public static SubscriptionTier fromString(String value) {
        for (SubscriptionTier tier: values()) {
            if (tier.value.equalsIgnoreCase(value)) return tier;
        }
        throw new IllegalArgumentException("Unknown tier: " + value);
    }
}
