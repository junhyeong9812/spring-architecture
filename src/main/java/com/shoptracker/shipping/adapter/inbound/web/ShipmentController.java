package com.shoptracker.shipping.adapter.inbound.web;

import com.shoptracker.shared.exception.EntityNotFoundException;
import com.shoptracker.shipping.domain.model.ShipmentId;
import com.shoptracker.shipping.domain.port.outbound.ShipmentRepository;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipping")
public class ShipmentController {
    private final ShipmentRepository shipmentRepository;

    public ShipmentController(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @GetMapping("/{id}")
    public ShipmentResponse getById(@PathVariable UUID id) {
        return shipmentRepository.findById(new ShipmentId(id))
                .map(ShipmentResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Shipment not found: " + id));
    }

    @GetMapping("/order/{orderId}")
    public ShipmentResponse getByOrderId(@PathVariable UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .map(ShipmentResponse::from)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Shipment not found for order: " + orderId));
    }
}
