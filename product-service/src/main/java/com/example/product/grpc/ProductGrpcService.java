package com.example.product.grpc;

import com.example.product.model.Product;
import com.example.product.service.RecordGenerator;
import io.grpc.stub.ServerCallStreamObserver;
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
        log.info("StreamProducts called - streaming with backpressure control");
        
        // Cast to ServerCallStreamObserver for backpressure control
        ServerCallStreamObserver<ProductResponse> serverCallStreamObserver = 
                (ServerCallStreamObserver<ProductResponse>) responseObserver;
        
        // Track if client has cancelled
        final boolean[] cancelled = {false};
        serverCallStreamObserver.setOnCancelHandler(() -> {
            cancelled[0] = true;
            log.info("Client cancelled stream");
        });
        
        try {
            Product template = recordGenerator.getProduct();
            
            // Pre-build response once for reuse
            ProductResponse response = ProductResponse.newBuilder()
                    .setProductId(template.getProductId())
                    .setName(template.getName())
                    .setPrice(template.getPrice())
                    .setStockQuantity(template.getStockQuantity())
                    .setCategory(template.getCategory())
                    .build();
            
            long count = 0;
            
            // Stream continuously until client cancels
            while (!cancelled[0]) {
                // Proper backpressure: only send when ready
                if (serverCallStreamObserver.isReady()) {
                    try {
                        responseObserver.onNext(response);
                        count++;
                        
                        // Small yield to prevent 100% CPU
                        if (count % 1000 == 0) {
                            Thread.sleep(1);
                        }

                    } catch (Exception e) {
                        // Client disconnected
                        log.info("Client disconnected after {} products: {}", count, e.getMessage());
                        return;
                    }
                } else {
                    // Backpressure: wait a bit for buffer to drain
                    Thread.sleep(1);
                }
            }
            
            log.info("StreamProducts completed, sent {} products", count);
            
        } catch (Exception e) {
            log.error("Error in streamProducts", e);
            responseObserver.onError(e);
        }
    }
}
