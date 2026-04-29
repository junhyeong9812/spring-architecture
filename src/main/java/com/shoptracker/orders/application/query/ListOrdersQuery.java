package com.shoptracker.orders.application.query;

public record ListOrdersQuery(
        String customerName,    // 선택: 고객별 필터
        String status,          // 선택: 상태 필터
        int page,
        int size,
        String sortBy,          // "createdAt", "totalAmount"
        String sortDir          // "asc", "desc"
) {
    public ListOrdersQuery {
        if (page < 0) page = 0;
        if (size <= 0 || size > 100) size = 20;
        if (sortBy == null) sortBy = "createdAt";
        if (sortDir == null) sortDir = "desc";
    }
}
