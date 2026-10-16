package br.com.lsk.inventory.web;

import br.com.lsk.inventory.application.dto.SaleEvent;
import br.com.lsk.inventory.application.port.ErpGateway;
import br.com.lsk.inventory.application.service.ProcessSaleService;
import br.com.lsk.inventory.application.service.ProcessSaleService.Result;
import br.com.lsk.inventory.application.service.ProductService;
import br.com.lsk.inventory.application.service.SapRetryService;
import br.com.lsk.inventory.infrastructure.persistence.DashboardReadRepository;
import br.com.lsk.inventory.web.dto.DashboardViews.ProductView;
import br.com.lsk.inventory.web.dto.OperationsDtos.CreateProductRequest;
import br.com.lsk.inventory.web.dto.OperationsDtos.RetryResponse;
import br.com.lsk.inventory.web.dto.OperationsDtos.SaleRequest;
import br.com.lsk.inventory.web.dto.OperationsDtos.SaleResponse;
import br.com.lsk.inventory.web.dto.OperationsDtos.SystemStatus;
import br.com.lsk.inventory.web.dto.OperationsDtos.UpdateProductRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OperationsController {

    private final ProcessSaleService sales;
    private final ProductService products;
    private final SapRetryService sapRetry;
    private final DashboardReadRepository reads;
    private final ErpGateway erp;

    /** Same pipeline as the RabbitMQ listener: idempotency, FEFO picking, minimum stock check. */
    @PostMapping("/sales")
    public ResponseEntity<SaleResponse> sale(@Valid @RequestBody SaleRequest request) {
        UUID eventId = request.idempotencyKey() != null ? request.idempotencyKey() : UUID.randomUUID();
        String order = request.orderRef() == null || request.orderRef().isBlank()
                ? "MANUAL-" + eventId.toString().substring(0, 8)
                : request.orderRef().trim();
        var items = request.items().stream()
                .map(item -> new SaleEvent.SaleItem(item.sku().trim(), item.quantity(), item.unitPrice()))
                .toList();

        Result result = sales.process(new SaleEvent(eventId, order, OffsetDateTime.now(), items));

        String reference = "SALE-" + eventId;
        boolean duplicate = result == Result.DUPLICATE;
        return ResponseEntity.status(duplicate ? HttpStatus.OK : HttpStatus.CREATED)
                .body(new SaleResponse(reference, duplicate ? "DUPLICATE" : "PROCESSED",
                        reads.movementsByReference(reference)));
    }

    @PostMapping("/products")
    public ResponseEntity<ProductView> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(products.create(request.toCommand()));
    }

    @PutMapping("/products/{sku}")
    public ProductView updateProduct(@PathVariable String sku, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(sku, request.toCommand());
    }

    @DeleteMapping("/products/{sku}")
    public ResponseEntity<Void> deactivateProduct(@PathVariable String sku) {
        products.deactivate(sku);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return reads.categories();
    }

    @PostMapping("/movements/{id}/sap-retry")
    public RetryResponse retrySap(@PathVariable long id) {
        String reference = sapRetry.retry(id);
        return new RetryResponse(reference, reads.movementsByReference(reference));
    }

    @GetMapping("/system/status")
    public SystemStatus status() {
        return new SystemStatus(erp.enabled());
    }
}
