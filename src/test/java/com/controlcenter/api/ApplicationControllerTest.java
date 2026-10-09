package com.controlcenter.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Application;
import com.controlcenter.service.ApplicationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApplicationService applicationService;

    @Test
    void registersApplication() throws Exception {
        when(applicationService.register(any())).thenReturn(application(7L, "billing"));

        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "billing", "repositoryUrl": "https://github.com/acme/billing", "defaultBranch": "main"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/applications/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("billing"));
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Not Valid!", "repositoryUrl": "https://gitlab.com/acme/billing"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void returnsConflictForDuplicateName() throws Exception {
        when(applicationService.register(any())).thenThrow(new ConflictException("already exists"));

        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "billing", "repositoryUrl": "https://github.com/acme/billing"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("already exists"));
    }

    @Test
    void returnsConflictWhenAConcurrentRequestWonTheUniqueConstraint() throws Exception {
        when(applicationService.register(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("duplicate key value violates uq_applications_name")));

        mvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "billing", "repositoryUrl": "https://github.com/acme/billing"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data; reload and try again"))
                .andExpect(jsonPath("$.details.length()").value(0));
    }

    @Test
    void listsApplications() throws Exception {
        when(applicationService.findAll()).thenReturn(List.of(application(1L, "billing"), application(2L, "web")));

        mvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("web"));
    }

    @Test
    void returnsNotFoundForUnknownApplication() throws Exception {
        when(applicationService.get(99L)).thenThrow(new NotFoundException("Application", 99L));

        mvc.perform(get("/api/applications/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/applications/99"));
    }

    private static Application application(Long id, String name) {
        Application application = new Application(name, null, "https://github.com/acme/" + name, "main");
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }
}
