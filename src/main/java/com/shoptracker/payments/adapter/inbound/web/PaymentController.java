package com.shoptracker.payments.adapter.inbound.web;

import com.shoptracker.payments.domain.model.Payment;
import com.shoptracker.payments.domain.port.outbound.PaymentRepository;
import com.shoptracker.payments.domain.model.PaymentId;
import com.shoptracker.shared.exception.EntityNotFoundException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable UUID id) {
        return paymentRepository.findById(new PaymentId(id))
                .map(PaymentResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Payment not found: " + id));
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse getByOrderId(@PathVariable UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment not found for order: " + orderId));
    }
}
