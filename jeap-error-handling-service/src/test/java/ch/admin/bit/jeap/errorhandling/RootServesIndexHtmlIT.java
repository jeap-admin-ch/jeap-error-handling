package ch.admin.bit.jeap.errorhandling;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

/**
 * Verifies that requesting the context root actually serves the contents of the SPA's index.html.
 * The root path forwards to /index.html (see {@code FrontendWebConfig}); this test exercises the
 * full request through a running servlet container so that the forward is resolved and the static
 * resource is served - something a {@code @WebMvcTest} slice cannot verify.
 */
class RootServesIndexHtmlIT extends ErrorHandlingITBase {

    private final RequestSpecification spec;

    public RootServesIndexHtmlIT(@Value("${server.port}") int serverPort) {
        spec = new RequestSpecBuilder()
                .setPort(serverPort).build();
    }

    @Test
    void getRoot_servesIndexHtmlContent() {
        given()
                .spec(spec)
                .when()
                .get("/")
                .then()
                .statusCode(200)
                .contentType(startsWith("text/html"))
                .body(containsString("<title>Error Handling</title>"))
                .body(containsString("<app-root"));
    }
}
