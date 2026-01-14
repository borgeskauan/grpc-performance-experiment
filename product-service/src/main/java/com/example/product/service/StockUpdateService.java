package com.example.product.service;

import com.example.product.model.Product;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class StockUpdateService {

    private static final Logger log = LoggerFactory.getLogger(StockUpdateService.class);
    
    private final ProductRepository productRepository;
    private final Map<String, List<StockWatcher>> watchers = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public StockUpdateService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void addWatcher(Set<String> productIds, StreamObserver<com.example.product.grpc.StockUpdate> observer) {
        for (String productId : productIds) {
            watchers.computeIfAbsent(productId, k -> new CopyOnWriteArrayList<>())
                    .add(new StockWatcher(observer));
        }
        log.info("Added watcher for {} products", productIds.size());
    }

    public void removeWatcher(StreamObserver<com.example.product.grpc.StockUpdate> observer) {
        watchers.values().forEach(list -> 
            list.removeIf(watcher -> watcher.observer.equals(observer))
        );
        log.info("Removed watcher");
    }

    @Scheduled(fixedRate = 3000) // Generate updates every 3 seconds
    public void generateStockUpdates() {
        List<String> productIds = productRepository.getAllProductIds();
        if (productIds.isEmpty()) {
            return;
        }

        // Generate 5-10 random stock updates
        int updateCount = 5 + random.nextInt(6);
        for (int i = 0; i < updateCount; i++) {
            String productId = productIds.get(random.nextInt(productIds.size()));
            Product product = productRepository.getProduct(productId);
            
            if (product != null) {
                int change = random.nextInt(20) - 10; // -10 to +10
                int newStock = Math.max(0, product.getStockQuantity() + change);
                productRepository.updateStock(productId, newStock);
                
                String reason = change > 0 ? "RESTOCK" : change < 0 ? "SALE" : "ADJUSTMENT";
                notifyWatchers(productId, newStock, reason);
            }
        }
    }

    private void notifyWatchers(String productId, int newStock, String reason) {
        List<StockWatcher> productWatchers = watchers.get(productId);
        if (productWatchers == null || productWatchers.isEmpty()) {
            return;
        }

        com.example.product.grpc.StockUpdate update = com.example.product.grpc.StockUpdate.newBuilder()
                .setProductId(productId)
                .setStockQuantity(newStock)
                .setTimestamp(System.currentTimeMillis())
                .setChangeReason(reason)
                .build();

        List<StockWatcher> toRemove = new ArrayList<>();
        for (StockWatcher watcher : productWatchers) {
            try {
                watcher.observer.onNext(update);
            } catch (Exception e) {
                log.warn("Failed to send update to watcher, removing: {}", e.getMessage());
                toRemove.add(watcher);
            }
        }
        
        productWatchers.removeAll(toRemove);
    }

    private static class StockWatcher {
        final StreamObserver<com.example.product.grpc.StockUpdate> observer;

        StockWatcher(StreamObserver<com.example.product.grpc.StockUpdate> observer) {
            this.observer = observer;
        }
    }
}
