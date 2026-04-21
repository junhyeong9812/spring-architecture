package com.shoptracker.tracking.adapter.inbound.web;

import com.shoptracker.shared.exception.EntityNotFoundException;
import com.shoptracker.tracking.domain.port.outbound.TrackingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {
    private final TrackingRepository trackingRepository;

    public TrackingController(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @GetMapping("/order/{orderId}")
    public TrackingResponse getByOrderId(@PathVariable UUID orderId) {
        return trackingRepository.findByOrderId(orderId)
                .map(TrackingResponse::from)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tracking not found for order: " + orderId));
    }

    @GetMapping("/order/{orderId}/timeline")
    public TrackingTimelineResponse getTimeline(@PathVariable UUID orderId) {
        return trackingRepository.findByOrderId(orderId)
                .map(TrackingTimelineResponse::from)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tracking not found for order: " + orderId));
    }
}
