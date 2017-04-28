package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;

import javax.ws.rs.core.Response;

import org.factcast.server.rest.FactCastRestApplication;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.SpringLifecycleListener;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.restdocs.JUnitRestDocumentation;

@Ignore("fails for unknown reason - please fix me")
// TODO jar
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