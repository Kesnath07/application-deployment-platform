package com.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.common.ConflictException;
import com.controlcenter.common.NotFoundException;
import com.controlcenter.domain.Application;
import com.controlcenter.repository.ApplicationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository repository;

    @InjectMocks
    private ApplicationService service;

    @Test
    void registersApplicationWithNormalizedRepositoryUrlAndDefaultBranch() {
        when(repository.existsByNameIgnoreCase("billing")).thenReturn(false);
        when(repository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Application application = service.register(
                new ApplicationRequest("billing", "  ", "https://github.com/acme/billing.git", null));

        assertThat(application.getRepositoryUrl()).isEqualTo("https://github.com/acme/billing");
        assertThat(application.getDefaultBranch()).isEqualTo("main");
        assertThat(application.getDescription()).isNull();
    }

    @Test
    void rejectsDuplicateName() {
        when(repository.existsByNameIgnoreCase("billing")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new ApplicationRequest("billing", null, "https://github.com/acme/billing", "main")))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void throwsNotFoundForUnknownApplication() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void normalizesTrailingSlashAndGitSuffix() {
        assertThat(ApplicationService.normalizeRepositoryUrl(" https://github.com/acme/web.git/ "))
                .isEqualTo("https://github.com/acme/web");
    }
}
