package com.shoptracker.unit;

import com.shoptracker.subscription.domain.model.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.*;

public class SubscriptionTest {

    @Test
    void activeSubscription_isActive() {
        Subscription sub = new Subscription(
                SubscriptionId.generate(), "홍길동", SubscriptionTier.PREMIUM,
                SubscriptionStatus.ACTIVE, Instant.now(),
                Instant.now().plus(15, ChronoUnit.DAYS)
        );
        assertThat(sub.isActive()).isTrue();
    }

    @Test
    void expiredSubscription_isNotActive() {
        Subscription sub = new Subscription(
                SubscriptionId.generate(), "홍길동", SubscriptionTier.PREMIUM,
                SubscriptionStatus.ACTIVE, Instant.now().minus(31, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS)
        );
        assertThat(sub.isActive()).isFalse();
    }

    @Test
    void cancelledSubscription_isNotActive() {
        Subscription sub = new Subscription(
                SubscriptionId.generate(), "홍길동", SubscriptionTier.BASIC,
                SubscriptionStatus.CANCELLED, Instant.now(),
                Instant.now().plus(15, ChronoUnit.DAYS)
        );
        assertThat(sub.isActive()).isFalse();
    }

    @Test
    void createSubscription_factoryMethod() {
        Subscription sub = Subscription.create("테스트", SubscriptionTier.BASIC);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(sub.isActive()).isTrue();
    }
}
