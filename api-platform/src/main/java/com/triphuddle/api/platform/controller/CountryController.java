package com.triphuddle.api.platform.controller;

import com.triphuddle.common.controller.BaseJpaController;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.api.platform.dto.CreateCountryRequest;
import com.triphuddle.api.platform.dto.CountryResponse;
import com.triphuddle.api.platform.dto.UpdateCountryRequest;
import com.triphuddle.api.platform.entity.Country;
import com.triphuddle.api.platform.service.CountryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/countries")
@RequiredArgsConstructor
@Tag(name = "Countries")
public class CountryController extends BaseJpaController<Country, CreateCountryRequest, UpdateCountryRequest, CountryResponse> {

    private final CountryService countryService;

    @Override
    protected BaseJpaService<Country, CreateCountryRequest, UpdateCountryRequest, CountryResponse> getService() {
        return countryService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@CountryPolicy.canCreate(authentication, #createCountryRequest)")
    public ResponseEntity<CountryResponse> create(@Valid @RequestBody CreateCountryRequest createCountryRequest) {
        return super.create(createCountryRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@CountryPolicy.canGetById(authentication, #id)")
    public ResponseEntity<CountryResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@CountryPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<CountryResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    @Override
    @PutMapping
    @PreAuthorize("@CountryPolicy.canUpdate(authentication, #updateCountryRequest)")
    public ResponseEntity<CountryResponse> update(@Valid @RequestBody UpdateCountryRequest updateCountryRequest) {
        return super.update(updateCountryRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@CountryPolicy.canDisable(authentication)")
    public ResponseEntity<CountryResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@CountryPolicy.canDelete(authentication, #id)")
    public ResponseEntity<CountryResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
