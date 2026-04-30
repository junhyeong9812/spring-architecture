package com.shoptracker.orders.adapter.outbound.persistence;

import com.shoptracker.orders.domain.model.Money;
import com.shoptracker.orders.domain.model.Order;
import com.shoptracker.orders.domain.model.OrderId;
import com.shoptracker.orders.domain.model.OrderItem;
import com.shoptracker.orders.domain.model.OrderStatus;

import java.util.List;

public class OrderMapper {

    public static Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.getProductName(), i.getQuantity(), new Money(i.getUnitPrice())))
                .toList();

        return new Order(
                new OrderId(entity.getId()),
                entity.getCustomerName(),
                items,
                OrderStatus.valueOf(entity.getStatus()),
                new Money(entity.getTotalAmount()),
                new Money(entity.getShippingFee()),
                new Money(entity.getDiscountAmount()),
                new Money(entity.getFinalAmount()),
                entity.getCreatedAt()
        );
    }

    public static OrderJpaEntity toJpa(Order domain) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setId(domain.getId().value());
        entity.setCustomerName(domain.getCustomerName());
        entity.setStatus(domain.getStatus().name());
        entity.setTotalAmount(domain.getTotalAmount().amount());
        entity.setShippingFee(domain.getShippingFee().amount());
        entity.setDiscountAmount(domain.getDiscountAmount().amount());
        entity.setFinalAmount(domain.getFinalAmount().amount());
        entity.setCreatedAt(domain.getCreatedAt());

        for (OrderItem item : domain.getItems()) {
            entity.addItem(new OrderItemJpaEntity(
                    item.productName(),
                    item.quantity(),
                    item.unitPrice().amount()
            ));
        }
        return entity;
    }
}
