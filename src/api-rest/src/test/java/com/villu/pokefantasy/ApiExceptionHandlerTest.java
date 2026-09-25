package com.villu.pokefantasy;

import com.villu.pokefantasy.exception.ForbiddenOperationException;
import com.villu.pokefantasy.exception.TooManyAttemptsException;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/throw")
        String boom(@RequestParam String type) throws Exception {
            throw switch (type) {
                case "bad" -> new IllegalArgumentException("Nombre inválido");
                case "conflict" -> new IllegalStateException("El draft ya empezó");
                case "forbidden" -> new ForbiddenOperationException("No eres admin");
                case "credentials" -> new BadCredentialsException("x");
                case "attempts" -> new TooManyAttemptsException("Demasiados intentos", Duration.ofMinutes(15));
                case "lock" -> new OptimisticLockingFailureException("stale");
                case "duplicate" -> new DuplicateKeyException("Ya existe un usuario con name=ash");
                case "abort" -> new ClientAbortException();
                default -> new RuntimeException("secreto interno");
            };
        }

        @PostMapping("/json")
        String json(@RequestBody Map<String, String> body) {
            return "ok";
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    private ResultActions problem(String type, int status, String code) throws Exception {
        return mvc.perform(get("/throw").param("type", type))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void domainErrors_keepTheirStatusAndCarryCodeAndMessage() throws Exception {
        problem("bad", 400, "BAD_REQUEST")
                .andExpect(jsonPath("$.detail").value("Nombre inválido"))
                .andExpect(jsonPath("$.message").value("Nombre inválido"));
        problem("conflict", 409, "CONFLICT").andExpect(jsonPath("$.message").value("El draft ya empezó"));
        problem("forbidden", 403, "FORBIDDEN");
        problem("credentials", 401, "INVALID_CREDENTIALS")
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        problem("lock", 409, "CONCURRENT_MODIFICATION");
        problem("duplicate", 409, "DUPLICATE");
    }

    @Test
    void tooManyAttempts_includesRetryAfter() throws Exception {
        problem("attempts", 429, "TOO_MANY_ATTEMPTS").andExpect(header().string("Retry-After", "900"));
    }

    @Test
    void unexpectedError_hidesInternalMessage() throws Exception {
        problem("other", 500, "INTERNAL_ERROR")
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("secreto"))));
    }

    @Test
    void clientAbort_noContent() throws Exception {
        mvc.perform(get("/throw").param("type", "abort")).andExpect(status().isNoContent());
    }

    @Test
    void wrongMethod_is405NotA500() throws Exception {
        mvc.perform(delete("/throw"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void missingParameter_is400() throws Exception {
        mvc.perform(get("/throw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void malformedJson_is400() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unsupportedMediaType_is415() throws Exception {
        mvc.perform(post("/json").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }
}
