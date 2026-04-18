package com.shoptracker.payments.application.port.inbound;

import com.shoptracker.payments.application.command.ProcessPaymentCommand;
import java.util.UUID;

public interface ProcessPaymentUseCase {
    UUID processPayment(ProcessPaymentCommand command);
}
