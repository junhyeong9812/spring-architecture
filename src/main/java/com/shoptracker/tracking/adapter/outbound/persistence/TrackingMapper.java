package com.shoptracker.tracking.adapter.outbound.persistence;

import com.shoptracker.tracking.domain.model.OrderTracking;
import com.shoptracker.tracking.domain.model.TrackingEvent;
import com.shoptracker.tracking.domain.model.TrackingId;
import com.shoptracker.tracking.domain.model.TrackingPhase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TrackingMapper {

    public static OrderTracking toDomain(TrackingJpaEntity entity) {
        List<TrackingEvent> events = entity.getEvents().stream()
                .map(e -> new TrackingEvent(
                        e.getEventType(),
                        e.getTimestamp(),
                        e.getModule(),
                        e.getDetail()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        return new OrderTracking(
                new TrackingId(entity.getId()),
                entity.getOrderId(),
                entity.getCustomerName(),
                entity.getSubscriptionTier(),
                events,
                TrackingPhase.valueOf(entity.getCurrentPhase()),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    public static TrackingJpaEntity toJpa(OrderTracking domain) {
        TrackingJpaEntity entity = new TrackingJpaEntity();
        entity.setId(domain.getId().value());
        entity.setOrderId(domain.getOrderId());
        entity.setCustomerName(domain.getCustomerName());
        entity.setSubscriptionTier(domain.getSubscriptionTier());
        entity.setCurrentPhase(domain.getCurrentPhase().name());
        entity.setStartedAt(domain.getStartedAt());
        entity.setCompletedAt(domain.getCompletedAt());

        for (TrackingEvent e : domain.getEvents()) {
            entity.addEvent(new TrackingEventJpaEntity(
                    UUID.randomUUID(),
                    e.eventType(),
                    e.timestamp(),
                    e.module(),
                    e.detail()
            ));
        }
        return entity;
    }
}