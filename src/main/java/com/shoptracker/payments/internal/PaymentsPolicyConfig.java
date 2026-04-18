package com.shoptracker.payments.internal;

import com.shoptracker.payments.domain.model.policy.NoDiscountPolicy;
import com.shoptracker.payments.domain.model.policy.SubscriptionDiscountPolicy;
import com.shoptracker.payments.domain.port.outbound.DiscountPolicy;
import com.shoptracker.shared.SubscriptionContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

import java.math.BigDecimal;

@Configuration
public class PaymentsPolicyConfig {

    @Bean
    @RequestScope
    DiscountPolicy discountPolicy(SubscriptionContext subCtx) {
        return switch (subCtx.tier()) {
            case "premium" -> new SubscriptionDiscountPolicy(
                    new BigDecimal("0.10"), "premium_subscription");
            case "basic" -> new SubscriptionDiscountPolicy(
                    new BigDecimal("0.05"), "basic_subscription");
            default -> new NoDiscountPolicy();
        };
    }
}
