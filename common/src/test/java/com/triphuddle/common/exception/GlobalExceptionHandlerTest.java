package com.triphuddle.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "access-denied,         403, Access denied",
            "not-found,             404, Country with id=1 not found",
            "invalid-filter,        400, bad filter",
            "insufficient-tokens,   402, no tokens left",
            "too-many-requests,     429, slow down",
            "business-rule,         422, already exists",
            "data-integrity,        422, The request conflicts with data that already exists",
            "no-resource,           404, ''",
            "unexpected,            500, ''"
    })
    void answersEachFailureWithItsStatusInOneShape(String failure, int status, String message) throws Exception {
        mvc.perform(get("/fail/" + failure))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.path").value("/fail/" + failure))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.message", containsString(message)));
    }

    @Test
    void namesEachInvalidField() throws Exception {
        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.validationErrors.name").value("Name is required"));
    }

    @Test
    void answersAnUnreadableBodyWith400WithoutEchoingIt() throws Exception {
        mvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\": [unclosed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is malformed or has a value of the wrong type"));
    }

    @Test
    void namesAParameterOfTheWrongType() throws Exception {
        mvc.perform(get("/typed/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value 'not-a-uuid' for parameter 'id'"));
    }

    @Test
    void namesAMissingHeader() throws Exception {
        mvc.perform(get("/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required header 'X-Group-Id' is missing"));
    }

    @Test
    void namesAMissingParameter() throws Exception {
        mvc.perform(get("/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'page' is missing"));
    }

    @Test
    void namesWhoWasDeniedWhenTheyAreKnown() throws Exception {
        mvc.perform(get("/fail/access-denied").principal(() -> "someone")).andExpect(status().isForbidden());
    }

    record Named(@NotBlank(message = "Name is required") String name) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/fail/{failure}")
        void fail(@PathVariable String failure) throws Exception {
            throw switch (failure) {
                case "access-denied" -> new AccessDeniedException("no");
                case "not-found" -> new ResourceNotFoundException("Country with id=1 not found");
                case "invalid-filter" -> new InvalidFilterException("bad filter");
                case "insufficient-tokens" -> new InsufficientTokensException("no tokens left");
                case "too-many-requests" -> new TooManyRequestsException("slow down");
                case "business-rule" -> new IllegalStateException("already exists");
                case "data-integrity" -> new DataIntegrityViolationException("duplicate key uk_country_alpha2");
                case "no-resource" -> new NoResourceFoundException(HttpMethod.GET, "favicon.ico");
                default -> new RuntimeException("boom");
            };
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody Named named) {
        }

        @GetMapping("/typed/{id}")
        void typed(@PathVariable UUID id) {
        }

        @GetMapping("/header")
        void header(@RequestHeader("X-Group-Id") String groupId) {
        }

        @GetMapping("/param")
        void param(@RequestParam int page) {
        }
    }
}
