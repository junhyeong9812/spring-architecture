package com.shoptracker.shipping.application.eventhandler;

import com.shoptracker.shared.events.PaymentApprovedEvent;
import com.shoptracker.shipping.application.service.ShipmentCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class ShippingEventHandler {
    private static final Logger log = LoggerFactory.getLogger(ShippingEventHandler.class);
    private final ShipmentCommandService shipmentCommandService;

    public ShippingEventHandler(ShipmentCommandService shipmentCommandService) {
        this.shipmentCommandService = shipmentCommandService;
    }

    @ApplicationModuleListener
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("Payment approved for order {}, creating shipment...", event.orderId());
        shipmentCommandService.createShipment(event.orderId(), event.finalAmount());
    }
}
