package com.shoptracker.shared.config;

import com.shoptracker.shared.SubscriptionContext;
import com.shoptracker.subscription.application.port.inbound.SubscriptionQueryPort;
import com.shoptracker.subscription.domain.model.Subscription;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class SubscriptionContextConfig {

    @Bean
    @RequestScope
    public SubscriptionContext subscriptionContext(
            HttpServletRequest request,
            SubscriptionQueryPort subscriptionQueryPort
    ) {
        String customerName = request.getHeader("X-Customer-Name");
        if (customerName == null || customerName.isBlank()) {
            return SubscriptionContext.none("guest");
        }

        return subscriptionQueryPort.findActiveByCustomer(customerName)
                .filter(Subscription::isActive)
                .map(sub -> new SubscriptionContext(
                        customerName, sub.getTier().getValue(), true))
                .orElse(SubscriptionContext.none(customerName));
    }
}
