package com.shoptracker.shipping.application.service;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shared.events.ShipmentCreatedEvent;
import com.shoptracker.shipping.domain.model.*;
import com.shoptracker.shipping.domain.port.outbound.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class ShipmentCommandService {
    private final ShipmentRepository shipmentRepository;
    private final ShippingFeePolicy shippingFeePolicy;
    private final ApplicationEventPublisher eventPublisher;

    public ShipmentCommandService(ShipmentRepository shipmentRepository,
                                  ShippingFeePolicy shippingFeePolicy,
                                  ApplicationEventPublisher eventPublisher) {
        this.shipmentRepository = shipmentRepository;
        this.shippingFeePolicy = shippingFeePolicy;
        this.eventPublisher = eventPublisher;
    }

    public UUID createShipment(UUID orderId, BigDecimal orderAmount) {
        // ★ shippingFeePolicy가 어떤 구현체인지 이 서비스는 모른다!
        ShippingFeeResult feeResult = shippingFeePolicy.calculateFee(
                new Money(orderAmount));

        Address defaultAddress = new Address("서울시 강남구", "서울", "06000");
        Shipment shipment = Shipment.create(orderId, defaultAddress, feeResult);

        shipmentRepository.save(shipment);

        eventPublisher.publishEvent(new ShipmentCreatedEvent(
                shipment.getId().value(), orderId,
                feeResult.fee().amount(), feeResult.discountType(),
                shipment.getTrackingNumber(), Instant.now()
        ));

        return shipment.getId().value();
    }
}
