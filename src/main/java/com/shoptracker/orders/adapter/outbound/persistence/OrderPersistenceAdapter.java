package com.shoptracker.orders.adapter.outbound.persistence;

import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderId;
import com.shoptracker.orders.domain.port.outbound.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrderPersistenceAdapter implements OrderRepository {
    private final SpringDataOrderRepository jpaRepository;

    public OrderPersistenceAdapter(SpringDataOrderRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Order order) {
        jpaRepository.save(OrderMapper.toJpa(order));
    }

    @Override
    public Optional<Order> findById(OrderId id) {
        return jpaRepository.findById(id.value())
                .map(OrderMapper::toDomain);
    }

    @Override
    public Page<Order> findAllByCustomerName(String customerName, Pageable pageable) {
        return jpaRepository.findAllByCustomerName(customerName, pageable)
                .map(OrderMapper::toDomain);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable)
                .map(OrderMapper::toDomain);
    }
}
