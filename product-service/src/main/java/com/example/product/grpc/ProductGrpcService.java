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
        
        try {
            ServerCallStreamObserver<ProductResponse> serverObserver = 
                    (ServerCallStreamObserver<ProductResponse>) responseObserver;
            
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
            
            // Stream with backpressure control
            while (!serverObserver.isCancelled()) {
                try {
                    // Only write if client is ready to receive
                    if (serverObserver.isReady()) {
                        serverObserver.onNext(response);
                        count++;
                    } else {
                        // Client buffer is full, wait a bit before retrying
                        Thread.sleep(100);
                    }

                } catch (Exception e) {
                    // Client disconnected
                    log.info("Client disconnected after {} products: {}", count, e.getMessage());
                    return;
                }
            }
            
            log.info("Stream completed after sending {} products", count);
            serverObserver.onCompleted();
            
        } catch (Exception e) {
            log.error("Error in streamProducts", e);
            responseObserver.onError(e);
        }
    }
}
