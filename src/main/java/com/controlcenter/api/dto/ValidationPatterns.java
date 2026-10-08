package com.controlcenter.api.dto;

/** Validation rules shared by REST requests and UI forms. */
public final class ValidationPatterns {

    public static final String APPLICATION_NAME = "^[a-z0-9][a-z0-9-]*[a-z0-9]$";
    public static final String APPLICATION_NAME_MESSAGE =
            "must be lowercase letters, digits and hyphens, starting and ending with a letter or digit";

    public static final String ENVIRONMENT_NAME = "^[a-z][a-z0-9-]{1,29}$";
    public static final String ENVIRONMENT_NAME_MESSAGE =
            "must be 2-30 lowercase letters, digits or hyphens, starting with a letter (e.g. dev, prod)";

    public static final String GITHUB_REPOSITORY_URL = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?(\\.git)?/?$";
    public static final String GITHUB_REPOSITORY_URL_MESSAGE = "must be a GitHub repository URL, e.g. https://github.com/org/repo";

    public static final String BRANCH = "^$|^[A-Za-z0-9._/-]+$";
    public static final String BRANCH_MESSAGE = "must be a valid Git branch name";

    public static final String HTTP_URL = "^$|^https?://[^\\s]+$";
    public static final String HTTP_URL_MESSAGE = "must be an http(s) URL";

    /** Docker tag rules: max 128 chars of [A-Za-z0-9_.-], not starting with '.' or '-'. */
    public static final String IMAGE_TAG = "^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$";
    public static final String IMAGE_TAG_MESSAGE = "must be a valid Docker image tag (e.g. a Git commit SHA)";

    private ValidationPatterns() {
    }
}
