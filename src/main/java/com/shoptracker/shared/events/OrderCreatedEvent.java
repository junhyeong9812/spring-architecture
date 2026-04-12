package com.shoptracker.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 주문 생성 이벤트.
 * ★ record로 정의 → 불변 + equals/hashCode/toString 자동 생성
 * ★ FastAPI의 @dataclass(frozen=True) OrderCreatedEvent에 대응
 */
public record OrderCreatedEvent(
        UUID orderId,
        String customerName,
        BigDecimal totalAmount,
        int itemsCount,
        Instant timestamp
) {}