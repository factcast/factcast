package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.document;
import static io.github.restdocsext.jersey.JerseyRestDocumentation.documentationConfiguration;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.removeHeaders;

import javax.ws.rs.core.Response;

import org.factcast.server.rest.FactCastRestApplication;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.SpringLifecycleListener;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.restdocs.JUnitRestDocumentation;

public class RootDocumentationTest extends JerseyTest {

    @Rule
    public JUnitRestDocumentation documentation = new JUnitRestDocumentation(
            "target/generated-snippets");

    @Override
    public ResourceConfig configure() {
        ResourceConfig rc = new FactCastRestApplication();
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(
                SpringConfig.class);
        rc.property("contextConfig", ctx);

        rc.register(SpringLifecycleListener.class);
        rc.register(RequestContextFilter.class);

        return rc;
    }

    @Test
    public void getSimple() {
        final Response response = target("/").register(documentationConfiguration(
                this.documentation)).register(document("root", preprocessRequest(removeHeaders(
                        "User-Agent")))).request().get();
        assertThat(response.getStatus(), is(200));

    }
}