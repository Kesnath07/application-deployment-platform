package com.controlcenter.web.form;

import com.controlcenter.api.dto.DeploymentRequest;
import com.controlcenter.api.dto.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DeploymentForm {

    @NotBlank
    @Pattern(regexp = ValidationPatterns.IMAGE_TAG, message = ValidationPatterns.IMAGE_TAG_MESSAGE)
    private String imageTag;

    @Size(max = ValidationPatterns.VERSION_MAX_LENGTH)
    @Pattern(regexp = ValidationPatterns.VERSION, message = ValidationPatterns.VERSION_MESSAGE)
    private String version;

    @Size(max = ValidationPatterns.MESSAGE_MAX_LENGTH)
    private String message;

    public DeploymentRequest toRequest() {
        return new DeploymentRequest(imageTag, version, message);
    }

    public String getImageTag() {
        return imageTag;
    }

    public void setImageTag(String imageTag) {
        this.imageTag = imageTag;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
