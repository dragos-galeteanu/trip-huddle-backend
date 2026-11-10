package com.triphuddle.platform.controller;

import com.triphuddle.common.controller.BaseJpaController;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.platform.dto.CreateCurrencyRequest;
import com.triphuddle.platform.dto.CurrencyResponse;
import com.triphuddle.platform.dto.UpdateCurrencyRequest;
import com.triphuddle.platform.entity.Currency;
import com.triphuddle.platform.service.CurrencyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/currencies")
@RequiredArgsConstructor
@Tag(name = "Currencies")
public class CurrencyController extends BaseJpaController<Currency, CreateCurrencyRequest, UpdateCurrencyRequest, CurrencyResponse> {

    private final CurrencyService currencyService;

    @Override
    protected BaseJpaService<Currency, CreateCurrencyRequest, UpdateCurrencyRequest, CurrencyResponse> getService() {
        return currencyService;
    }

    @Override
    @PostMapping
    @PreAuthorize("@CurrencyPolicy.canCreate(authentication, #createCurrencyRequest)")
    public ResponseEntity<CurrencyResponse> create(@Valid @RequestBody CreateCurrencyRequest createCurrencyRequest) {
        return super.create(createCurrencyRequest);
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@CurrencyPolicy.canGetById(authentication, #id)")
    public ResponseEntity<CurrencyResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@CurrencyPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<CurrencyResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    @Override
    @PutMapping
    @PreAuthorize("@CurrencyPolicy.canUpdate(authentication, #updateCurrencyRequest)")
    public ResponseEntity<CurrencyResponse> update(@Valid @RequestBody UpdateCurrencyRequest updateCurrencyRequest) {
        return super.update(updateCurrencyRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@CurrencyPolicy.canDisable(authentication)")
    public ResponseEntity<CurrencyResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@CurrencyPolicy.canDelete(authentication, #id)")
    public ResponseEntity<CurrencyResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
