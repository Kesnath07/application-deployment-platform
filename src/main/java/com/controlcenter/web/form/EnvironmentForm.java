package com.controlcenter.web.form;

import com.controlcenter.api.dto.EnvironmentRequest;
import com.controlcenter.api.dto.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EnvironmentForm {

    @NotBlank
    @Pattern(regexp = ValidationPatterns.ENVIRONMENT_NAME, message = ValidationPatterns.ENVIRONMENT_NAME_MESSAGE)
    private String name;

    @Size(max = 500)
    @Pattern(regexp = ValidationPatterns.HTTP_URL, message = ValidationPatterns.HTTP_URL_MESSAGE)
    private String url;

    public EnvironmentRequest toRequest() {
        return new EnvironmentRequest(name, url);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
