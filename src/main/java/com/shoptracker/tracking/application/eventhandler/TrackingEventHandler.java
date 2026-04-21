package com.shoptracker.tracking.application.eventhandler;

import com.shoptracker.shared.events.*;
import com.shoptracker.tracking.domain.model.*;
import com.shoptracker.tracking.domain.port.outbound.TrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TrackingEventHandler {
    private static final Logger log = LoggerFactory.getLogger(TrackingEventHandler.class);
    private final TrackingRepository trackingRepository;

    public TrackingEventHandler(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    // ── 주문 생성 ──
    @ApplicationModuleListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[Tracking] Order created: {}", event.orderId());

        OrderTracking tracking = OrderTracking.create(
                event.orderId(), event.customerName(), "unknown"); // 구독 tier는 별도 조회 가능

        trackingRepository.save(tracking);
    }

    // ── 결제 승인 ──
    @ApplicationModuleListener
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("[Tracking] Payment approved for order: {}", event.orderId());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("payment.approved", "payments", Map.of(
                    "paymentId", event.paymentId().toString(),
                    "originalAmount", event.originalAmount().toString(),
                    "discountAmount", event.discountAmount().toString(),
                    "finalAmount", event.finalAmount().toString(),
                    "discountType", event.appliedDiscountType(),
                    "method", event.method()
            ));
            tracking.updatePhase(TrackingPhase.PAYMENT_COMPLETED);
            trackingRepository.save(tracking);
        });
    }

    // ── 결제 거절 ──
    @ApplicationModuleListener
    public void onPaymentRejected(PaymentRejectedEvent event) {
        log.info("[Tracking] Payment rejected for order: {}", event.orderId());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("payment.rejected", "payments", Map.of(
                    "reason", event.reason()
            ));
            tracking.updatePhase(TrackingPhase.FAILED);
            trackingRepository.save(tracking);
        });
    }

    // ── 배송 생성 ──
    @ApplicationModuleListener
    public void onShipmentCreated(ShipmentCreatedEvent event) {
        log.info("[Tracking] Shipment created for order: {}", event.orderId());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("shipment.created", "shipping", Map.of(
                    "shipmentId", event.shipmentId().toString(),
                    "shippingFee", event.shippingFee().toString(),
                    "feeDiscountType", event.feeDiscountType(),
                    "trackingNumber", event.trackingNumber() != null
                            ? event.trackingNumber() : ""
            ));
            tracking.updatePhase(TrackingPhase.SHIPPING);
            trackingRepository.save(tracking);
        });
    }

    // ── 배송 상태 변경 ──
    @ApplicationModuleListener
    public void onShipmentStatusChanged(ShipmentStatusChangedEvent event) {
        log.info("[Tracking] Shipment status changed for order: {} → {}",
                event.orderId(), event.newStatus());

        trackingRepository.findByOrderId(event.orderId()).ifPresent(tracking -> {
            tracking.addEvent("shipment.status_changed", "shipping", Map.of(
                    "newStatus", event.newStatus()
            ));
            if ("DELIVERED".equals(event.newStatus())) {
                tracking.updatePhase(TrackingPhase.DELIVERED);
            }
            trackingRepository.save(tracking);
        });
    }

    // ── 구독 활성화 ──
    @ApplicationModuleListener
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        log.info("[Tracking] Subscription activated: {} ({})",
                event.customerName(), event.tier());
        // 구독은 특정 주문에 종속되지 않으므로, 별도 로그만 남기거나
        // 고객 기준으로 관련 tracking을 업데이트할 수 있음
    }
}
