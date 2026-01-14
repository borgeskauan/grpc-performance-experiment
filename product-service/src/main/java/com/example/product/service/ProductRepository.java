package com.example.product.service;

import com.example.product.model.Product;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProductRepository {

    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @PostConstruct
    public void init() {
        // Initialize with 1000 products
        String[] categories = {"Electronics", "Clothing", "Books", "Food", "Toys"};
        
        for (int i = 1; i <= 1000; i++) {
            String productId = "PROD-" + String.format("%04d", i);
            String category = categories[random.nextInt(categories.length)];
            String name = category + " Item " + i;
            double price = 10.0 + (random.nextDouble() * 990.0);
            int stock = random.nextInt(500);
            
            products.put(productId, new Product(productId, name, price, stock, category));
        }
    }

    public List<Product> getAllProducts() {
        return new ArrayList<>(products.values());
    }

    public List<Product> getProductsByCategory(String category) {
        if (category == null || category.isEmpty()) {
            return getAllProducts();
        }
        return products.values().stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(category))
                .toList();
    }

    public Product getProduct(String productId) {
        return products.get(productId);
    }

    public void updateStock(String productId, int newStock) {
        Product product = products.get(productId);
        if (product != null) {
            product.setStockQuantity(newStock);
        }
    }

    public List<String> getAllProductIds() {
        return new ArrayList<>(products.keySet());
    }
}
