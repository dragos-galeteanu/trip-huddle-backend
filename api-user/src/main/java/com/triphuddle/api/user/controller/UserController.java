package com.triphuddle.api.user.controller;

import com.triphuddle.common.controller.BaseJpaController;
import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.api.user.dto.RegisterRequest;
import com.triphuddle.api.user.dto.UpdateUserRequest;
import com.triphuddle.api.user.dto.UserResponse;
import com.triphuddle.api.user.entity.User;
import com.triphuddle.api.user.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController extends BaseJpaController<User, RegisterRequest, UpdateUserRequest, UserResponse> {

    private final UserService userService;

    @Override
    protected BaseJpaService<User, RegisterRequest, UpdateUserRequest, UserResponse> getService() {
        return userService;
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@AuthenticationPrincipal Jwt jwt) {
        userService.resetPassword(jwt.getClaimAsString("email"));
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> verifyEmail(@RequestParam UUID token) {
        userService.verifyEmail(token);
        return ResponseEntity.ok("""
                <html><body style="font-family:sans-serif;text-align:center;padding:60px">
                  <h2>✅ Email verified!</h2>
                  <p>Your email address has been confirmed. You can close this tab.</p>
                </body></html>
                """);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(jwt, registerRequest));
    }

    @Override
    public ResponseEntity<UserResponse> create(@RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @Override
    @GetMapping("/{id}")
    @PreAuthorize("@UserPolicy.canGetById(authentication, #id)")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return super.getById(id);
    }

    @Override
    @GetMapping
    @PreAuthorize("@UserPolicy.canGetAll(authentication)")
    public ResponseEntity<Paginated<UserResponse>> getAll(@Valid @ModelAttribute PaginationFilter filter) {
        return super.getAll(filter);
    }

    @Override
    @PutMapping
    @PreAuthorize("@UserPolicy.canUpdate(authentication, #updateUserRequest)")
    public ResponseEntity<UserResponse> update(@Valid @RequestBody UpdateUserRequest updateUserRequest) {
        return super.update(updateUserRequest);
    }

    @Override
    @DeleteMapping("/{id}/disable")
    @PreAuthorize("@UserPolicy.canDisable(authentication)")
    public ResponseEntity<UserResponse> disable(@PathVariable UUID id) {
        return super.disable(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @PreAuthorize("@UserPolicy.canDelete(authentication, #id)")
    public ResponseEntity<UserResponse> delete(@PathVariable UUID id) {
        return super.delete(id);
    }
}
