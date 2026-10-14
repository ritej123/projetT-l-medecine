package com.telemed.platform.user.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record AdminPageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> AdminPageResponse<T> from(Page<T> page) {
        return new AdminPageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
