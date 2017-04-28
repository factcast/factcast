package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.document;
import static io.github.restdocsext.jersey.JerseyRestDocumentation.documentationConfiguration;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.removeHeaders;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.restdocs.JUnitRestDocumentation;

public class SimpleDocumentation extends JerseyTest {

    @Rule
    public JUnitRestDocumentation documentation = new JUnitRestDocumentation(
            "target/generated-snippets");

    @Path("test")
    public static class TestResource {
        @GET
        public String getSimple() {
            return "SimpleTesting";
        }
    }

    @Override
    public ResourceConfig configure() {
        ResourceConfig rc = new ResourceConfig(TestResource.class);
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                SpringConfig.class);
        rc.property("contextConfig", ctx);
        return rc;
    }

    @Test
    public void getSimple() {
        final Response response = target("test").register(documentationConfiguration(
                this.documentation)).register(document("get-simple", preprocessRequest(
                        removeHeaders("User-Agent")))).request().get();
        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), is("SimpleTesting"));
    }
}