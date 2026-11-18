package com.triphuddle.common.controller;

import com.triphuddle.common.dto.Paginated;
import com.triphuddle.common.dto.PaginationMetaData;
import com.triphuddle.common.exception.GlobalExceptionHandler;
import com.triphuddle.common.exception.ResourceNotFoundException;
import com.triphuddle.common.filter.PaginationFilter;
import com.triphuddle.common.service.BaseJpaService;
import com.triphuddle.common.service.BaseMongoService;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaseControllerTest {

    record Request(@NotBlank String name) {
    }

    record Thing(String id, String name) {
    }

    interface JpaThings extends BaseJpaService<Object, Request, Request, Thing> {
    }

    interface MongoThings extends BaseMongoService<Object, Request, Request, Thing> {
    }

    @RestController
    @RequestMapping("/jpa")
    static class JpaController extends BaseJpaController<Object, Request, Request, Thing> {
        final JpaThings service = mock(JpaThings.class);

        @Override
        protected BaseJpaService<Object, Request, Request, Thing> getService() {
            return service;
        }
    }

    @RestController
    @RequestMapping("/mongo")
    static class MongoController extends BaseMongoController<Object, Request, Request, Thing> {
        final MongoThings service = mock(MongoThings.class);

        @Override
        protected BaseMongoService<Object, Request, Request, Thing> getService() {
            return service;
        }
    }

    private static final Thing THING = new Thing("1", "thing");

    private static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Nested
    class Jpa {

        private final JpaController controller = new JpaController();
        private final MockMvc mvc = mvc(controller);

        @Test
        void createsWith201AndUpdatesWith200() throws Exception {
            given(controller.service.create(new Request("a"))).willReturn(THING);
            given(controller.service.update(new Request("b"))).willReturn(THING);

            mvc.perform(post("/jpa").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"a\"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("thing"));
            mvc.perform(put("/jpa").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"b\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void validatesTheBodyBeforeTheServiceSeesIt() throws Exception {
            mvc.perform(post("/jpa").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                    .andExpect(status().isBadRequest());
            then(controller.service).shouldHaveNoInteractions();
        }

        @Test
        void listsByPage() throws Exception {
            given(controller.service.getAll(new PaginationFilter(2, 5)))
                    .willReturn(new Paginated<>(List.of(THING), new PaginationMetaData(6L, 5, 2, 2)));

            mvc.perform(get("/jpa").param("page", "2").param("pageSize", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.metaData.currentPage").value(2));
        }

        @Test
        void readsDisablesAndDeletesById() throws Exception {
            UUID id = UUID.randomUUID();
            given(controller.service.getById(id)).willReturn(THING);
            given(controller.service.disable(id)).willReturn(THING);
            given(controller.service.delete(id)).willReturn(THING);

            mvc.perform(get("/jpa/" + id)).andExpect(status().isOk());
            mvc.perform(delete("/jpa/" + id + "/disable")).andExpect(status().isOk());
            mvc.perform(delete("/jpa/" + id)).andExpect(status().isOk());
        }

        @Test
        void passesNotFoundThrough() throws Exception {
            given(controller.service.getById(any())).willThrow(new ResourceNotFoundException("gone"));

            mvc.perform(get("/jpa/" + UUID.randomUUID())).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Mongo {

        private final MongoController controller = new MongoController();
        private final MockMvc mvc = mvc(controller);

        @Test
        void readsDisablesAndDeletesByStringId() throws Exception {
            given(controller.service.getById("abc")).willReturn(THING);
            given(controller.service.disable("abc")).willReturn(THING);
            given(controller.service.delete("abc")).willReturn(THING);

            mvc.perform(get("/mongo/abc")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"));
            mvc.perform(delete("/mongo/abc/disable")).andExpect(status().isOk());
            mvc.perform(delete("/mongo/abc")).andExpect(status().isOk());
        }
    }
}
