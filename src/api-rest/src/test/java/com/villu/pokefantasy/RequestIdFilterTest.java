package com.villu.pokefantasy;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestIdFilterTest {

    @RestController
    static class Endpoints {
        final AtomicReference<String> seenInMdc = new AtomicReference<>();

        @GetMapping("/ok")
        String ok() {
            seenInMdc.set(MDC.get(RequestIdFilter.MDC_KEY));
            return "ok";
        }

        @GetMapping("/fail")
        String fail() {
            throw new IllegalStateException("nope");
        }
    }

    private final Endpoints endpoints = new Endpoints();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(endpoints)
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void generatesIdWhenAbsent_availableInMdcAndResponse_andClearedAfterwards() throws Exception {
        String id = mvc.perform(get("/ok"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(endpoints.seenInMdc.get()).isEqualTo(id);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void reusesSafeIncomingId() throws Exception {
        mvc.perform(get("/ok").header(RequestIdFilter.HEADER, "abc-123_x.y"))
                .andExpect(header().string(RequestIdFilter.HEADER, "abc-123_x.y"));
    }

    @Test
    void replacesUnsafeIncomingId() {
        assertThat(RequestIdFilter.resolve("evil\nFAKE LOG LINE")).isNotEqualTo("evil\nFAKE LOG LINE").hasSize(36);
        assertThat(RequestIdFilter.resolve("x".repeat(65))).hasSize(36);
        assertThat(RequestIdFilter.resolve(null)).hasSize(36);
    }

    @Test
    void errorsCarryTheRequestId() throws Exception {
        mvc.perform(get("/fail").header(RequestIdFilter.HEADER, "req-42"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.requestId").value("req-42"));
    }

    @Test
    void filterClearsMdcEvenIfChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        try {
            new RequestIdFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                throw new java.io.IOException("broken pipe");
            });
        } catch (Exception expected) {
            // se propaga, pero el MDC queda limpio
        }
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
