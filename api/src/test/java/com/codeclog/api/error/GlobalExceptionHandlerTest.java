package com.codeclog.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeclog.api.config.CodeclogProperties;
import com.codeclog.api.config.WebConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller slice over a throwaway controller. The subject under test is the error contract itself
 * (§10) — every failure mode a client can trigger has to come back in the same envelope, with the
 * right status.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@EnableConfigurationProperties(CodeclogProperties.class)
@Import({GlobalExceptionHandlerTest.ProbeController.class, GlobalExceptionHandler.class, WebConfig.class})
@TestPropertySource(properties = "codeclog.allowed-origins=http://localhost:5173")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("body validation failures return 400 with field-level detail")
    void bodyValidationReturnsFieldDetails() throws Exception {
        mockMvc.perform(post("/probe/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handle\":\"\",\"bio\":\"way too long for the limit\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.length()").value(2))
                .andExpect(jsonPath("$.error.details[0].field").value("bio"))
                .andExpect(jsonPath("$.error.details[1].field").value("handle"))
                .andExpect(jsonPath("$.error.details[1].message").isNotEmpty());
    }

    @Test
    @DisplayName("an unparseable body is a 400, not a 500")
    void malformedBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/probe/echo").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    @DisplayName("a wrongly typed query parameter is reported against the parameter name")
    void typeMismatchNamesTheParameter() throws Exception {
        mockMvc.perform(get("/probe/page").param("limit", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("limit"));
    }

    @Test
    @DisplayName("deliberate API errors keep their code and status")
    void apiExceptionMapsToItsCode() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("Handle already taken"));
    }

    @Test
    @DisplayName("an unexpected exception becomes a 500 that leaks nothing")
    void unexpectedExceptionIsOpaque() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Something went wrong on our end"));
    }

    @Test
    @DisplayName("the wrong HTTP verb returns 405 in the same envelope")
    void wrongMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/probe/echo"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("an unknown path returns 404 in the same envelope")
    void unknownPathReturnsNotFound() throws Exception {
        mockMvc.perform(get("/probe/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("No endpoint at /probe/nope"));
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        @PostMapping("/echo")
        ProbeRequest echo(@Valid @RequestBody ProbeRequest request) {
            return request;
        }

        @GetMapping("/page")
        String page(@RequestParam @Min(1) int limit) {
            return "limit=" + limit;
        }

        @GetMapping("/conflict")
        String conflict() {
            throw ApiException.conflict("Handle already taken");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("internal detail that must not reach the client");
        }
    }

    record ProbeRequest(@NotBlank String handle, @Size(max = 10) String bio) {}
}
