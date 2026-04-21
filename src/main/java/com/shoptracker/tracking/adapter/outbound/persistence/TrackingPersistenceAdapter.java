package com.shoptracker.tracking.adapter.outbound.persistence;

import com.shoptracker.tracking.domain.model.OrderTracking;
import com.shoptracker.tracking.domain.port.outbound.TrackingRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class TrackingPersistenceAdapter implements TrackingRepository {
    private final SpringDataTrackingRepository jpaRepository;

    public TrackingPersistenceAdapter(SpringDataTrackingRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OrderTracking tracking) {
        jpaRepository.save(TrackingMapper.toJpa(tracking));
    }

    @Override
    public Optional<OrderTracking> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId)
                .map(TrackingMapper::toDomain);
    }
}