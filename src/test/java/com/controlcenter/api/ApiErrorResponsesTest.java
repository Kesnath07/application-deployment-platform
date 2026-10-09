package com.controlcenter.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs against a real servlet container, because unknown routes and unsupported methods are
 * answered through the container's error dispatch, which MockMvc does not perform.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiErrorResponsesTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void unsupportedMethodGetsAnApiErrorWithTheAllowedMethods() {
        ResponseEntity<String> response = http.exchange("/api/applications/1", HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.PUT);
        assertApiError(response, 405, "/api/applications/1");
        assertThat(message(response)).startsWith("Method DELETE is not supported by /api/applications/1; use ")
                .contains("GET").contains("PUT");
    }

    @Test
    void unknownApiRouteGetsAnApiError() {
        ResponseEntity<String> response = http.getForEntity("/api/does-not-exist", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertApiError(response, 404, "/api/does-not-exist");
        assertThat(message(response)).isEqualTo("No API endpoint matches GET /api/does-not-exist");
    }

    @Test
    void errorsAreJsonEvenWhenTheClientAcceptsSomethingElse() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_XML));

        ResponseEntity<String> response = http.exchange("/api/applications/999999", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(message(response)).isEqualTo("Application with id 999999 was not found");
    }

    @Test
    void unsupportedContentTypeIs415() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);

        ResponseEntity<String> response = http.postForEntity("/api/applications",
                new HttpEntity<>("name=billing", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertApiError(response, 415, "/api/applications");
        assertThat(message(response)).startsWith("Content type 'text/plain").endsWith("send application/json");
    }

    @Test
    void pagesOutsideTheApiKeepTheHtmlErrorPage() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.TEXT_HTML));

        ResponseEntity<String> response = http.exchange("/no-such-page", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody()).contains("Back to dashboard");
    }

    private static void assertApiError(ResponseEntity<String> response, int status, String path) {
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        String body = response.getBody();
        assertThat((Integer) JsonPath.read(body, "$.status")).isEqualTo(status);
        assertThat((String) JsonPath.read(body, "$.path")).isEqualTo(path);
        assertThat((String) JsonPath.read(body, "$.error")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.timestamp")).isNotBlank();
        assertThat((List<?>) JsonPath.read(body, "$.details")).isEmpty();
    }

    private static String message(ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.message");
    }
}
