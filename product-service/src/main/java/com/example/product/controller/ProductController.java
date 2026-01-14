package com.example.product.controller;

import com.example.product.model.Product;
import com.example.product.service.RecordGenerator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final int DEFAULT_BATCH_SIZE = 100;

    private final RecordGenerator recordGenerator;

    public ProductController(RecordGenerator recordGenerator) {
        this.recordGenerator = recordGenerator;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getProducts(
            @RequestParam(required = false, defaultValue = "100") int batchSize) {
        
        // Return batch of identical records (max-speed, no throttling)
        int size = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        List<Product> batch = new ArrayList<>(size);
        Product template = recordGenerator.getProduct();
        
        for (int i = 0; i < size; i++) {
            batch.add(template);
        }

        return ResponseEntity.ok(batch);
    }
}
