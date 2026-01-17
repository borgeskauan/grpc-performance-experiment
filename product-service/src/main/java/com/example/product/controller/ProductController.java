package com.example.product.controller;

import com.example.product.model.Product;
import com.example.product.service.RecordGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final RecordGenerator recordGenerator;
    private final ObjectMapper objectMapper;

    public ProductController(RecordGenerator recordGenerator, ObjectMapper objectMapper) {
        this.recordGenerator = recordGenerator;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<StreamingResponseBody> getProducts() {
        
        StreamingResponseBody stream = outputStream -> {
            // Return a single record per request (true long polling)
            Product product = recordGenerator.getProduct();
            String jsonRecord = objectMapper.writeValueAsString(product);
            byte[] recordBytes = (jsonRecord + "\n").getBytes(StandardCharsets.UTF_8);
            
            outputStream.write(recordBytes);
            outputStream.flush();
        };
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(stream);
    }
}
