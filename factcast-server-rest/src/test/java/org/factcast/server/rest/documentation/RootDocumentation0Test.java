package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.document;
import static io.github.restdocsext.jersey.JerseyRestDocumentation.documentationConfiguration;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.removeHeaders;

import javax.ws.rs.core.Response;

import org.factcast.server.rest.FactCastRestApplication;
import org.factcast.server.rest.documentation.util.HyperschemaLinkExtractor;
import org.factcast.server.rest.documentation.util.SpringConfig;
import org.factcast.server.rest.resources.FactsRel;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.SpringLifecycleListener;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.restdocs.hypermedia.HypermediaDocumentation;
import org.springframework.restdocs.hypermedia.LinksSnippet;

public class RootDocumentation0Test extends JerseyTest {

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

    @Test
    public void getSimple() {

        LinksSnippet links = HypermediaDocumentation.links(new HyperschemaLinkExtractor(), //
                HypermediaDocumentation.linkWithRel(FactsRel.FACT_IDS.getRelation().getName())
                        .description(
                                "The link for the eventstream of fact ids, links to the <<_sse_stream_of_fact_ids, facts resource>>"), //
                HypermediaDocumentation.linkWithRel(FactsRel.FULL_FACTS.getRelation().getName())
                        .description(
                                "The link for the eventstream, links to the <<_sse_stream_of_facts, facts resource>>"), //
                HypermediaDocumentation.linkWithRel(FactsRel.CREATE_TRANSACTIONAL.getRelation()
                        .getName()).description(
                                "Creating a new transaction links to the <<resources-transactions, transaction resource>>"));

        final Response response = target("/").register(documentationConfiguration(
                this.documentation))
                .register(document("root", preprocessRequest(removeHeaders(
                        "User-Agent")), preprocessResponse(prettyPrint()), links))
                .request()
                .get();
        assertThat(response.getStatus(), is(200));

    }
}