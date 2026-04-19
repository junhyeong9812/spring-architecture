package com.shoptracker.shipping.internal;

import com.shoptracker.shared.SubscriptionContext;
import com.shoptracker.shipping.domain.model.policy.*;
import com.shoptracker.shipping.domain.port.outbound.ShippingFeePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
class ShippingPolicyConfig {

    @Bean
    @RequestScope
    ShippingFeePolicy shippingFeePolicy(SubscriptionContext subCtx) {
        return switch (subCtx.tier()) {
            case "premium" -> new PremiumShippingFeePolicy();
            case "basic"   -> new BasicShippingFeePolicy();
            default        -> new StandardShippingFeePolicy();
        };
    }
}
