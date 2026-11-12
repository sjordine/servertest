package com.servertest.products.packaging;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.servertest.products.packaging.PackageDtos.PackageCreateRequest;
import com.servertest.products.packaging.PackageDtos.PackageResponse;
import com.servertest.products.packaging.PackageDtos.PackageUpdateRequest;

import jakarta.validation.Valid;

@RestController
public class PackageController {

    private static final String BASE = "/api/products/{productId}/packages";

    private final PackageService service;

    public PackageController(PackageService service) {
        this.service = service;
    }

    /** Lookup by package code (e.g. for the stock service). Returns archived packages too, flagged. */
    @GetMapping("/api/products/packages/{code}")
    public PackageResponse getByCode(@PathVariable String code) {
        return service.getByCode(code);
    }

    @GetMapping(BASE)
    public List<PackageResponse> list(@PathVariable UUID productId,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.list(productId, includeArchived);
    }

    @GetMapping(BASE + "/{packageId}")
    public PackageResponse get(@PathVariable UUID productId, @PathVariable UUID packageId) {
        return service.get(productId, packageId);
    }

    @PostMapping(BASE)
    public ResponseEntity<PackageResponse> create(@PathVariable UUID productId,
            @Valid @RequestBody PackageCreateRequest req) {
        PackageResponse created = service.create(productId, req);
        return ResponseEntity.created(URI.create("/api/products/" + productId + "/packages/" + created.id()))
                .body(created);
    }

    @PutMapping(BASE + "/{packageId}")
    public PackageResponse update(@PathVariable UUID productId, @PathVariable UUID packageId,
            @Valid @RequestBody PackageUpdateRequest req) {
        return service.update(productId, packageId, req);
    }

    @DeleteMapping(BASE + "/{packageId}")
    public ResponseEntity<Void> archive(@PathVariable UUID productId, @PathVariable UUID packageId) {
        service.archive(productId, packageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(BASE + "/{packageId}/restore")
    public PackageResponse restore(@PathVariable UUID productId, @PathVariable UUID packageId) {
        return service.restore(productId, packageId);
    }
}
