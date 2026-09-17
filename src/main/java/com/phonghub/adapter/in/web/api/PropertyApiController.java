package com.phonghub.adapter.in.web.api;

import com.phonghub.application.port.in.PropertyUseCase;
import com.phonghub.domain.model.Property;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
public class PropertyApiController {

    private final PropertyUseCase propertyUseCase;

    public PropertyApiController(PropertyUseCase propertyUseCase) {
        this.propertyUseCase = propertyUseCase;
    }

    @GetMapping
    public ResponseEntity<List<Property>> listProperties() {
        return ResponseEntity.ok(propertyUseCase.listAccessibleProperties());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Property> getProperty(@PathVariable UUID id) {
        return ResponseEntity.ok(propertyUseCase.getProperty(id));
    }

    @PostMapping
    public ResponseEntity<Property> createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        Property property = propertyUseCase.createProperty(new PropertyUseCase.CreatePropertyCommand(
            request.name(),
            request.address(),
            request.description(),
            request.totalRooms()
        ));
        return ResponseEntity.created(URI.create("/api/properties/" + property.id())).body(property);
    }

    public record CreatePropertyRequest(
        @NotBlank(message = "Property name is required")
        String name,

        @NotBlank(message = "Property address is required")
        String address,

        String description,

        @Min(value = 0, message = "Total rooms must be non-negative")
        int totalRooms
    ) {}
}
