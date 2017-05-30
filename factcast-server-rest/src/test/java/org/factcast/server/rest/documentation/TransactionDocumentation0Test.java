package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.document;
import static io.github.restdocsext.jersey.JerseyRestDocumentation.documentationConfiguration;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.removeHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;

import java.util.List;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.factcast.server.rest.FactCastRestApplication;
import org.factcast.server.rest.documentation.util.ConstrainedFields;
import org.factcast.server.rest.documentation.util.Descriptors;
import org.factcast.server.rest.documentation.util.SpringConfig;
import org.factcast.server.rest.resources.FactTransactionJson;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.SpringLifecycleListener;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.snippet.Snippet;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TransactionDocumentation0Test extends JerseyTest {
    @Rule
    public JUnitRestDocumentation documentation = new JUnitRestDocumentation(
            "src/docs/generated-snippets");

    private AnnotationConfigApplicationContext ctx;

    @Override
    public ResourceConfig configure() {
        ResourceConfig rc = new FactCastRestApplication();
        ctx = new AnnotationConfigApplicationContext(SpringConfig.class);
        rc.property("contextConfig", ctx);

        rc.register(SpringLifecycleListener.class);
        rc.register(RequestContextFilter.class);

        return rc;
    }

    @After
    public void shutown() {
        ctx.close();
    }

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void post() throws Exception {

        ConstrainedFields fields = new ConstrainedFields(FactTransactionJson.class);
        List<FieldDescriptor> fieldDescriptors = Descriptors.getFactFieldDescriptors("facts[].",
                fields);
        fieldDescriptors.add(0, fields.withPath("facts").description(
                "Non empty list with the facts to commit in this transaction"));
        Snippet requestFieldSnippet = requestFields(//
                fieldDescriptors);

        FactTransactionJson factTransactionJson = objectMapper.readValue(this.getClass()
                .getResourceAsStream("TransactionJson.json"), FactTransactionJson.class);

        final Response response = target("/transactions").register(documentationConfiguration(
                this.documentation))
                .register(document("facts-transactions", preprocessRequest(
                        removeHeaders("User-Agent"), prettyPrint()), preprocessResponse(
                                prettyPrint()), requestFieldSnippet))
                .request()
                .post(Entity.entity(
                        factTransactionJson, MediaType.APPLICATION_JSON));
        assertThat(response.getStatus(), is(204));

    }
}
