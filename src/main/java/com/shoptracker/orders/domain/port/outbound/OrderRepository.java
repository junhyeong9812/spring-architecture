package com.shoptracker.orders.domain.port.outbound;

import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface OrderRepository {
    void save(Order order);
    Optional<Order> findById(OrderId id);
    Page<Order> findAllByCustomerName(String customerName, Pageable pageable);
    Page<Order> findAll(Pageable pageable);
}
