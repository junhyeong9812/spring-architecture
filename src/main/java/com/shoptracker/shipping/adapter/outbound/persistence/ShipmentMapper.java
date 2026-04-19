package com.shoptracker.shipping.adapter.outbound.persistence;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.shipping.domain.model.*;

public class ShipmentMapper {

    public static Shipment toDomain(ShipmentJpaEntity e) {
        return new Shipment(
                new ShipmentId(e.getId()),
                e.getOrderId(),
                ShipmentStatus.valueOf(e.getStatus()),
                new Address(e.getStreet(), e.getCity(), e.getZipCode()),
                new Money(e.getShippingFee()),
                new Money(e.getOriginalFee()),
                e.getFeeDiscountType(),
                e.getTrackingNumber(),
                e.getEstimatedDelivery(),
                e.getCreatedAt()
        );
    }

    public static ShipmentJpaEntity toJpa(Shipment d) {
        ShipmentJpaEntity e = new ShipmentJpaEntity();
        e.setId(d.getId().value());
        e.setOrderId(d.getOrderId());
        e.setStatus(d.getStatus().name());
        Address a = d.getAddress();
        e.setStreet(a.street());
        e.setCity(a.city());
        e.setZipCode(a.zipCode());
        e.setShippingFee(d.getShippingFee().amount());
        e.setOriginalFee(d.getOriginalFee().amount());
        e.setFeeDiscountType(d.getFeeDiscountType());
        e.setTrackingNumber(d.getTrackingNumber());
        e.setEstimatedDelivery(d.getEstimatedDelivery());
        e.setCreatedAt(d.getCreatedAt());
        return e;
    }
}
