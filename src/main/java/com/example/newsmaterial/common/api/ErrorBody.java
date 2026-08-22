package com.example.newsmaterial.common.api;

import java.util.List;

public record ErrorBody(String code, String message, String requestId, List<String> details) {
}
