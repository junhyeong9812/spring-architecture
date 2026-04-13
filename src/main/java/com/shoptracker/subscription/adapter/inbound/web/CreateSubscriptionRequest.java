package com.shoptracker.subscription.adapter.inbound.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateSubscriptionRequest(
        @NotBlank(message = "customerName is required")
        String customerName,

        @NotBlank(message = "tier is required")
        @Pattern(regexp = "basic|premium", message = "tier must be 'basic' or 'premium'")
        String tier
) {}
