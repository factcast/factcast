package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.document;
import static io.github.restdocsext.jersey.JerseyRestDocumentation.documentationConfiguration;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.removeHeaders;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.snippet.Attributes.key;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.factcast.server.rest.FactCastRestApplication;
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
import org.springframework.restdocs.constraints.ConstraintDescriptions;
import org.springframework.restdocs.constraints.ResourceBundleConstraintDescriptionResolver;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TransactionDocumentationTest extends JerseyTest {
    @Rule
    public JUnitRestDocumentation documentation = new JUnitRestDocumentation(
            "target/generated-snippets");

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
    public void getSimple() throws Exception {

        ConstrainedFields fields = new ConstrainedFields(FactTransactionJson.class);
        Snippet requestFieldSnippet = requestFields(//
                fields.withPath("facts").description(
                        "Non empty list with the facts to commit in this transaction"), //
                fields.withPath("facts[].header").description(
                        "The header of the new fact, it could have many custom attributes, but the follwing are needed"), //
                fields.withPath("facts[].header.id").description("client side UUID"), //
                fields.withPath("facts[].header.ns").description("namespace"), //
                fields.withPath("facts[].header.type").description("type"), //
                fields.withPath("facts[].header.aggId").description("client side UUID"), //
                fields.withPath("facts[].header.meta").description("Key-value map for meta data"),
                fields.withPath("facts[].payLoad").description("The payload of the ne fact"));

        FactTransactionJson factTransactionJson = objectMapper.readValue(this.getClass()
                .getResourceAsStream("TransactionJson.json"), FactTransactionJson.class);

        final Response response = target("/transactions").register(documentationConfiguration(
                this.documentation)).register(document("events-transactions", preprocessRequest(
                        removeHeaders("User-Agent"), prettyPrint()), preprocessResponse(
                                prettyPrint()), requestFieldSnippet)).request().post(Entity.entity(
                                        factTransactionJson, MediaType.APPLICATION_JSON));
        assertThat(response.getStatus(), is(204));

    }

    private static class ConstrainedFields {

        private final ConstraintDescriptions constraintDescriptions;

        ConstrainedFields(Class<?> input) {
            this.constraintDescriptions = new ConstraintDescriptions(input,
                    new ValidatorConstraintResolver(),
                    new ResourceBundleConstraintDescriptionResolver());
        }

        private FieldDescriptor withPath(String path) {
            return fieldWithPath(path).attributes(key("constraints").value(StringUtils
                    .collectionToDelimitedString(this.constraintDescriptions
                            .descriptionsForProperty(path), ". ")));
        }
    }
}
