package com.shoptracker.orders.adapter.inbound.web;

import com.shoptracker.orders.application.query.ListOrdersQuery;
import com.shoptracker.orders.application.query.OrderSummary;
import com.shoptracker.orders.application.service.OrderCommandService;
import com.shoptracker.orders.application.service.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "주문 관리 API")
public class OrderController {
    private final OrderCommandService commandService;
    private final OrderQueryService queryService;

    public OrderController(OrderCommandService orderCommandService, OrderQueryService queryService) {
        this.commandService = orderCommandService;
        this.queryService = queryService;
    }

    // Command
    @Operation(summary = "주문 생성",
            description = "새 주문을 생성합니다. X-Customer-Name 헤더로 구독 상태가 자동 반영됩니다.")
    @ApiResponse(responseCode = "201", description = "주문 생성 성공")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-Customer-Name") String customerName) {
        UUID id = commandService.createOrder(customerName, request.items());
        return Map.of("id", id);
    }

    // Query
    @GetMapping
    public Page<OrderSummary> list(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        return queryService.listOrders(new ListOrdersQuery(
                customerName, status, page, size, sort, dir));
    }

    @GetMapping("/{id}")
    public OrderSummary getById(@PathVariable UUID id) {
        return queryService.getOrder(id);
    }

    // Command
    @PostMapping("/{id}/cancel")
    public void cancel(@PathVariable UUID id) {
        commandService.cancelOrder(id);
    }
}