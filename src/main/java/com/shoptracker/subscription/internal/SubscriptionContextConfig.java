package com.shoptracker.subscription.internal;

import com.shoptracker.shared.SubscriptionContext;
import com.shoptracker.subscription.application.port.inbound.SubscriptionQueryPort;
import com.shoptracker.subscription.domain.model.Subscription;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

@Configuration
public class SubscriptionContextConfig {

    @Bean
    @Scope(value = "request", proxyMode = ScopedProxyMode.NO)
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
