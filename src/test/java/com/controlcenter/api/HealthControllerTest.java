package com.controlcenter.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.controlcenter.config.ControlCenterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@EnableConfigurationProperties(ControlCenterProperties.class)
@TestPropertySource(properties = {"control-center.version=3f2a9c1e", "control-center.environment=dev"})
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void reportsHealthyStatusWithVersion() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("cloud-deployment-control-center"))
                .andExpect(jsonPath("$.version").value("3f2a9c1e"))
                .andExpect(jsonPath("$.environment").value("dev"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
