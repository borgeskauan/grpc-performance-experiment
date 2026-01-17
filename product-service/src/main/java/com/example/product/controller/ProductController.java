package com.example.product.controller;

import com.example.product.model.Product;
import com.example.product.service.RecordGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final RecordGenerator recordGenerator;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

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

    @GetMapping("/events")
    public SseEmitter getProductEvents() {
        // Set timeout to 60 seconds for SSE connections
        SseEmitter emitter = new SseEmitter(60000L);
        
        // Process events asynchronously to avoid blocking the request thread
        executorService.execute(() -> {
            try {
                // Serialize the product once outside the loop
                String productJson = objectMapper.writeValueAsString(recordGenerator.getProduct());
                var event = SseEmitter.event()
                            .name("product")
                            .data(productJson)
                            .build();
                
                // Emit events continuously until client disconnects or timeout occurs
                while (true) {
                    emitter.send(event);
                }
            } catch (IOException e) {
                // Client disconnected
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
}
