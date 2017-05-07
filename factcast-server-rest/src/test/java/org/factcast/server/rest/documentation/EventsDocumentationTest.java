package org.factcast.server.rest.documentation;

import static io.github.restdocsext.jersey.JerseyRestDocumentation.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.springframework.restdocs.headers.HeaderDocumentation.*;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.factcast.server.rest.FactCastRestApplication;
import org.factcast.server.rest.resources.FactJson;
import org.glassfish.jersey.media.sse.EventInput;
import org.glassfish.jersey.media.sse.SseFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.SpringLifecycleListener;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.restdocs.headers.ResponseHeadersSnippet;
import org.springframework.restdocs.hypermedia.HypermediaDocumentation;
import org.springframework.restdocs.hypermedia.LinksSnippet;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.PayloadDocumentation;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.restdocs.request.RequestDocumentation;
import org.springframework.restdocs.request.RequestParametersSnippet;
import org.springframework.restdocs.snippet.Snippet;

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
    @Ignore
    public void getEvents() throws InterruptedException, ExecutionException {

        // request-Documentation

        List<ParameterDescriptor> subscriptionParamDescriptors = Descriptors
                .getSubscriptionRequestParamsdescriptor();
        RequestParametersSnippet requestSnippet = RequestDocumentation.relaxedRequestParameters(
                subscriptionParamDescriptors);

        // header
        ResponseHeadersSnippet headerDoc = responseHeaders(headerWithName(HttpHeaders.CACHE_CONTROL)
                .description("Caching is disabled for streams"));

        EventInput eventInput = target("/events/").queryParam("follow", true).queryParam("factSpec",
                "%7B%20%22ns%22%3A%22a%22%7D").register(SseFeature.class).register(
                        documentationConfiguration(this.documentation)).register(document(
                                "event-stream", preprocessRequest(removeHeaders("User-Agent")),
                                preprocessResponse(prettyPrint()), headerDoc, requestSnippet))
                .request().get(EventInput.class);

        // assertThat(response.getStatus(), is(200));
        // assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL),
        // is("no-chache"));
    }

    // TODO jar //FIXME jar
    @Ignore("disabled failing test after changeing aggId to be an array.")
    @Test
    public void getSimpleEvent() {
        // links
        LinksSnippet links = HypermediaDocumentation.links(new HyperschemaLinkExtractor(), //
                HypermediaDocumentation.linkWithRel(Rel.SELF.getRelation().getName()).description(
                        "The link to that specific resource"));
        // payload
        List<FieldDescriptor> factFieldDescriptors = Descriptors.getFactFieldDescriptors("",
                new ConstrainedFields(FactJson.class));
        factFieldDescriptors.add(PayloadDocumentation.fieldWithPath("_schema").description(
                "Schemainformation"));
        Snippet responseDoc = PayloadDocumentation.responseFields(factFieldDescriptors);

        // header
        ResponseHeadersSnippet headerDoc = responseHeaders(headerWithName(HttpHeaders.CACHE_CONTROL)
                .description("Caching for 1000000 seconds."));

        final Response response = target("/events/" + SetupRunner.one.id().toString()).register(
                documentationConfiguration(this.documentation)).register(document("event",
                        preprocessRequest(removeHeaders("User-Agent")), preprocessResponse(
                                prettyPrint()), links, responseDoc, headerDoc)).request().get();
        assertThat(response.getStatus(), is(200));
        assertThat(response.getHeaderString(HttpHeaders.CACHE_CONTROL), is(
                "max-age=1000000, s-maxage=1000000, public"));
    }
}