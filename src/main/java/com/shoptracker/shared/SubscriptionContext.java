package com.shoptracker.shared;

/**
 * 구독 상태를 요약한 불변 DTO.
 * payments, Shipping 등 다른 모듈은 이것만 알면 된다.
 * Subscription 엔티티 자체는 모른다.
 * @param customerName
 * @param tier
 * @param isActive
 * 
 * FastAPI의 @dataclass(frozen=True)에 대응 - java record는 자동으로 불편
 */
public record SubscriptionContext(
    String customerName,
    String tier,    // "none",
    boolean isActive
) {
    public static SubscriptionContext none(String customerName) {
        return new SubscriptionContext(customerName, "none", false);
    }
}
