package org.acme.quickstart;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@Disabled
public class GiftResourceTest {

    @Test
    @Disabled
    public void testGiftEndpoint() {
        given()
                .when().get("/gift")
                .then()
                .statusCode(200)
                .body(is("[]"));
    }

    @Test
    @Disabled
    public void testCreateGiftEndpoint() {
        given()
                .when().post("/gift?name=toto")
                .then()
                .statusCode(200)
                .body(is("created toto"));
    }

    @Test
    // @Disabled
    public void testGiftEndpointLoop() {
        given()
                .when().get("/gift/loop")
                .then()
                .statusCode(200)
                .body(is("OK"));
    }

}