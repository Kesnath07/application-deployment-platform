package com.controlcenter.web.form;

import com.controlcenter.api.dto.ApplicationRequest;
import com.controlcenter.api.dto.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mutable form-backing bean for the "register application" page. */
public class ApplicationForm {

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.APPLICATION_NAME, message = ValidationPatterns.APPLICATION_NAME_MESSAGE)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Size(max = 500)
    @Pattern(regexp = ValidationPatterns.GITHUB_REPOSITORY_URL, message = ValidationPatterns.GITHUB_REPOSITORY_URL_MESSAGE)
    private String repositoryUrl;

    @Size(max = 100)
    @Pattern(regexp = ValidationPatterns.BRANCH, message = ValidationPatterns.BRANCH_MESSAGE)
    private String defaultBranch = "main";

    public ApplicationRequest toRequest() {
        return new ApplicationRequest(name, description, repositoryUrl, defaultBranch);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    public void setDefaultBranch(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }
}
