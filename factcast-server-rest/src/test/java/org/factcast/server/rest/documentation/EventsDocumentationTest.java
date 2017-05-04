package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.document;
import static io.github.restdocsext.jersey.JerseyRestDocumentation.documentationConfiguration;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.removeHeaders;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.factcast.server.rest.FactCastRestApplication;
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

import com.mercateo.common.rest.schemagen.link.relation.Rel;

public class EventsDocumentationTest extends JerseyTest {

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

    @Test
    public void documentNotFoundCacheHeaders() {

        final Response response = target("/events/5").register(documentationConfiguration(
                this.documentation)).register(document("event-404", preprocessRequest(removeHeaders(
                        "User-Agent")), preprocessResponse(prettyPrint()), responseHeaders(
                                headerWithName(HttpHeaders.CACHE_CONTROL).description(
                                        "Caching of errors is set to 10 seconds")))).request()
                .get();
        assertThat(response.getStatus(), is(404));
        assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(
                "max-age=10, s-maxage=10, public"));

    }

    @Test
    public void getSimpleEvent() {

        LinksSnippet links = HypermediaDocumentation.links(new HyperschemaLinkExtractor(), //
                HypermediaDocumentation.linkWithRel(Rel.SELF.getRelation().getName()).description(
                        "The link to that specific resource"));

        final Response response = target("/events/" + SetupRunner.one.id().toString()).register(
                documentationConfiguration(this.documentation)).register(document("event",
                        preprocessRequest(removeHeaders("User-Agent")), preprocessResponse(
                                prettyPrint()), links)).request().get();
        assertThat(response.getStatus(), is(200));

    }
}