package com.shoptracker.shipping.adapter.outbound.persistence;

import com.shoptracker.shipping.domain.model.Shipment;
import com.shoptracker.shipping.domain.model.ShipmentId;
import com.shoptracker.shipping.domain.port.outbound.ShipmentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class ShipmentPersistenceAdapter implements ShipmentRepository{

    private final SpringDataShipmentRepository jpaRepository;

    public ShipmentPersistenceAdapter(SpringDataShipmentRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Shipment shipment) {
        jpaRepository.save(ShipmentMapper.toJpa(shipment));
    }

    @Override
    public Optional<Shipment> findById(ShipmentId id) {
        return jpaRepository.findById(id.value())
                .map(ShipmentMapper::toDomain);
    }

    @Override
    public Optional<Shipment> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId)
                .map(ShipmentMapper::toDomain);
    }
}
