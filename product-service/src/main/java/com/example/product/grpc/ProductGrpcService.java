package com.example.product.grpc;

import com.example.product.model.Product;
import com.example.product.service.RecordGenerator;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ProductGrpcService.class);
    
    private final RecordGenerator recordGenerator;

    public ProductGrpcService(RecordGenerator recordGenerator) {
        this.recordGenerator = recordGenerator;
    }

    @Override
    public void streamProducts(ProductStreamRequest request, StreamObserver<ProductResponse> responseObserver) {
        log.info("StreamProducts called - streaming at max speed with backpressure");
        
        try {
            Product template = recordGenerator.getProduct();
            
            // Stream continuously until client cancels (backpressure handled by gRPC)
            // No artificial delays, no limits - pure throughput test
            long count = 0;
            
            while (!Thread.currentThread().isInterrupted()) {
                ProductResponse response = ProductResponse.newBuilder()
                        .setProductId(template.getProductId())
                        .setName(template.getName())
                        .setPrice(template.getPrice())
                        .setStockQuantity(template.getStockQuantity())
                        .setCategory(template.getCategory())
                        .build();

                responseObserver.onNext(response);
                count++;
                
                // Log progress periodically
                if (count % 10000 == 0) {
                    log.debug("Streamed {} products", count);
                }
            }
            
            responseObserver.onCompleted();
            log.info("StreamProducts completed, sent {} products", count);
            
        } catch (Exception e) {
            log.error("Error in streamProducts", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void watchStock(StockWatchRequest request, StreamObserver<StockUpdate> responseObserver) {
        log.info("WatchStock called - streaming at max speed with backpressure");
        
        try {
            Product template = recordGenerator.getProduct();
            
            // Stream continuously until client cancels (backpressure handled by gRPC)
            long count = 0;
            
            while (!Thread.currentThread().isInterrupted()) {
                StockUpdate update = StockUpdate.newBuilder()
                        .setProductId(template.getProductId())
                        .setStockQuantity(template.getStockQuantity())
                        .setTimestamp(System.currentTimeMillis())
                        .setChangeReason("BENCHMARK")
                        .build();

                responseObserver.onNext(update);
                count++;
                
                // Log progress periodically
                if (count % 10000 == 0) {
                    log.debug("Streamed {} stock updates", count);
                }
            }
            
            responseObserver.onCompleted();
            log.info("WatchStock completed, sent {} updates", count);
            
        } catch (Exception e) {
            log.error("Error in watchStock", e);
            responseObserver.onError(e);
        }
    }
}
