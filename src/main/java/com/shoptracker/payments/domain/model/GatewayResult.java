package com.shoptracker.payments.domain.model;

public record GatewayResult(boolean success, String transactionId, String message) {}
