package com.shoptracker.subscription.adapter.inbound.web;

import com.shoptracker.shared.exception.EntityNotFoundException;
import com.shoptracker.subscription.application.port.inbound.CreateSubscriptionUseCase;
import com.shoptracker.subscription.application.port.inbound.SubscriptionQueryPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {
    private final CreateSubscriptionUseCase createUseCase;
    private final SubscriptionQueryPort queryPort;

    public SubscriptionController(CreateSubscriptionUseCase createUseCase,
                                  SubscriptionQueryPort queryPort) {
        this.createUseCase = createUseCase;
        this.queryPort = queryPort;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateSubscriptionRequest request) {
        UUID id = createUseCase.create(request.customerName(), request.tier());
        return ResponseEntity
                .created(URI.create("/api/v1/subscriptions/" + id))
                .body(Map.of("id", id));
    }

    @GetMapping("/{id}")
    public SubscriptionResponse getById(@PathVariable UUID id) {
        return queryPort.findById(id)
                .map(SubscriptionResponse::from)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription not found: " + id));
    }

    @GetMapping("/customer/{customerName}")
    public SubscriptionResponse getByCustomer(@PathVariable String customerName) {
        return queryPort.findActiveByCustomer(customerName)
                .map(SubscriptionResponse::from)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active subscription for: " + customerName));
    }
}
