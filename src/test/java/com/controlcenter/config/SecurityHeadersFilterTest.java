package com.controlcenter.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.controlcenter.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
class SecurityHeadersFilterTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void protectsDashboardPagesFromFramingAndSniffing() throws Exception {
        assertSecurityHeaders(mvc.perform(get("/")).andExpect(status().isOk()));
    }

    @Test
    void addsTheHeadersToApiAndErrorResponses() throws Exception {
        assertSecurityHeaders(mvc.perform(get("/api/health")).andExpect(status().isOk()));
        assertSecurityHeaders(mvc.perform(get("/api/applications/999999")).andExpect(status().isNotFound()));
    }

    private static void assertSecurityHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }
}
