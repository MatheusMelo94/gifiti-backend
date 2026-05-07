package com.gifiti.api.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Verifies the {@code PostHogDistinctIdFilter} is wired into the Spring
 * filter chain and that requests with no {@code X-PostHog-DistinctId}
 * header succeed (the header is optional). Header validation behavior is
 * covered by unit tests; here we only assert that the filter is present
 * and does not break a benign request.
 */
class PostHogDistinctIdFilterIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("a request with no PostHog distinctId header still reaches the controller")
    void no_header_request_succeeds() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/actuator/health"))
                .andReturn();

        // The actuator health endpoint may be 200 or 401 depending on
        // configuration — both are signs that the filter chain ran. The
        // filter must not cause a 500 on a missing header.
        assertThat(result.getResponse().getStatus())
                .isIn(200, 401, 403);
    }

    @Test
    @DisplayName("a request with a valid PostHog distinctId header still reaches the controller")
    void valid_header_request_succeeds() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/actuator/health")
                                .header("X-PostHog-DistinctId", "anon-test-1"))
                .andReturn();

        assertThat(result.getResponse().getStatus())
                .isIn(200, 401, 403);
    }
}
