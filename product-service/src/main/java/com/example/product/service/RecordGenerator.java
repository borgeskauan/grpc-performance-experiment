package com.example.product.service;

import com.example.product.model.Product;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * Shared record generator that produces deterministic, constant-size JSON records.
 * Used by both HTTP and gRPC endpoints for throughput benchmarking.
 */
@Service
public class RecordGenerator {

    private static final String FIXED_PRODUCT_ID = "PROD-0001";
    private static final String FIXED_NAME = "BenchmarkProduct_FixedWidth_PaddedTo50Chars";
    private static final double FIXED_PRICE = 999.99;
    private static final int FIXED_STOCK = 500;
    private static final String FIXED_CATEGORY = "Electronics_FixedWidth";

    private final ObjectMapper objectMapper;
    private Product template;
    private String templateJson;
    private int recordSizeBytes;

    public RecordGenerator() {
        // Configure ObjectMapper for stable, non-pretty JSON
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        // Create a template product with fixed-width fields
        template = new Product(
                FIXED_PRODUCT_ID,
                FIXED_NAME,
                FIXED_PRICE,
                FIXED_STOCK,
                FIXED_CATEGORY
        );

        // Pre-serialize to ensure deterministic JSON
        try {
            templateJson = objectMapper.writeValueAsString(template);
            recordSizeBytes = templateJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize template product", e);
        }
    }

    /**
     * Get the template product (same instance every time)
     */
    public Product getProduct() {
        return template;
    }

    /**
     * Get the pre-serialized JSON string (same content every time)
     */
    public String getJsonString() {
        return templateJson;
    }

    /**
     * Get the byte size of each record
     */
    public int getRecordSizeBytes() {
        return recordSizeBytes;
    }
}
