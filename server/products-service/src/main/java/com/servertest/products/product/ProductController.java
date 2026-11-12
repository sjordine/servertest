package com.servertest.products.product;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.servertest.products.common.PageResponse;
import com.servertest.products.product.ProductDtos.ProductCreateRequest;
import com.servertest.products.product.ProductDtos.ProductDetail;
import com.servertest.products.product.ProductDtos.ProductSummary;
import com.servertest.products.product.ProductDtos.ProductUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(q, categoryId, includeArchived, page, size);
    }

    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/sku/{sku}")
    public ProductDetail getBySku(@PathVariable String sku) {
        return service.getBySku(sku);
    }

    @PostMapping
    public ResponseEntity<ProductDetail> create(@Valid @RequestBody ProductCreateRequest req) {
        ProductDetail created = service.create(req);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductDetail update(@PathVariable UUID id, @Valid @RequestBody ProductUpdateRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        service.archive(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ProductDetail restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}
