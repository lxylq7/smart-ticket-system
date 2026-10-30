package com.example.ticket.common.response;

import java.util.List;

public record PageResponse<T>(long page, long size, long total, long pages, List<T> records) {

    public PageResponse {
        records = List.copyOf(records);
    }
}
