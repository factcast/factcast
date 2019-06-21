/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.server.grpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.*;
import java.util.*;

import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.core.store.*;
import org.factcast.core.subscription.*;
import org.factcast.grpc.api.*;
import org.factcast.grpc.api.conv.*;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import com.google.common.collect.*;

import io.grpc.*;
import io.grpc.stub.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class FactStoreGrpcServiceTest {

    @Mock
    FactStore backend;

    FactStoreGrpcService uut;

    @Captor
    ArgumentCaptor<List<Fact>> acFactList;

    final ProtoConverter conv = new ProtoConverter();

    @Captor
    private ArgumentCaptor<SubscriptionRequestTO> reqCaptor;

    @BeforeEach
    void setUp() {
        uut = new FactStoreGrpcService(backend);
    }

    @Test
    void testPublishNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish(null, mock(StreamObserver.class));
        });
    }

    @Test
    void testPublishNone() {
        doNothing().when(backend).publish(acFactList.capture());
        MSG_Facts r = MSG_Facts.newBuilder().build();
        uut.publish(r, mock(StreamObserver.class));
        verify(backend).publish(anyList());
        assertTrue(acFactList.getValue().isEmpty());
    }

    @Test
    void testPublishSome() {
        doNothing().when(backend).publish(acFactList.capture());
        Builder b = MSG_Facts.newBuilder();
        Fact f1 = Fact.builder().ns("test").build("{}");
        Fact f2 = Fact.builder().ns("test").build("{}");
        MSG_Fact msg1 = conv.toProto(f1);
        MSG_Fact msg2 = conv.toProto(f2);
        b.addAllFact(Arrays.asList(msg1, msg2));
        MSG_Facts r = b.build();
        uut.publish(r, mock(StreamObserver.class));
        verify(backend).publish(anyList());
        List<Fact> facts = acFactList.getValue();
        assertFalse(facts.isEmpty());
        assertEquals(2, facts.size());
        assertEquals(f1.id(), facts.get(0).id());
        assertEquals(f2.id(), facts.get(1).id());
    }

    @Test
    void testFetchById() {
        UUID id = UUID.randomUUID();
        uut.fetchById(conv.toProto(id), mock(ServerCallStreamObserver.class));
        verify(backend).fetchById(eq(id));
    }

    @Test
    void testSubscribeFacts() {
        SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
        when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);
        uut.subscribe(new ProtoConverter().toProto(SubscriptionRequestTO.forFacts(req)),
                mock(ServerCallStreamObserver.class));
        verify(backend).subscribe(any(), any());
        assertFalse(reqCaptor.getValue().idOnly());
    }

    @Test
    void testSubscribeIds() {
        SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
        when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);
        uut.subscribe(new ProtoConverter().toProto(SubscriptionRequestTO.forIds(req)),
                mock(ServerCallStreamObserver.class));
        verify(backend).subscribe(any(), any());
        assertTrue(reqCaptor.getValue().idOnly());
    }

    @Test
    public void testSerialOf() throws Exception {
        uut = new FactStoreGrpcService(backend);

        StreamObserver so = mock(StreamObserver.class);
        assertThrows(NullPointerException.class, () -> {
            uut.serialOf(null, so);
        });

        UUID id = new UUID(0, 1);
        OptionalLong twenty_two = OptionalLong.of(22);
        when(backend.serialOf(id)).thenReturn(twenty_two);

        uut.serialOf(conv.toProto(id), so);

        verify(so).onCompleted();
        verify(so).onNext(conv.toProto(twenty_two));
        verifyNoMoreInteractions(so);
    }

    @Test
    public void testSerialOfThrows() throws Exception {
        uut = new FactStoreGrpcService(backend);

        StreamObserver so = mock(StreamObserver.class);
        when(backend.serialOf(any(UUID.class))).thenThrow(UnsupportedOperationException.class);

        assertThrows(UnsupportedOperationException.class, () -> {
            uut.serialOf(conv.toProto(UUID.randomUUID()), so);
        });
    }

    @Test
    public void testEnumerateNamespaces() throws Exception {
        uut = new FactStoreGrpcService(backend);
        StreamObserver so = mock(StreamObserver.class);
        when(backend.enumerateNamespaces()).thenReturn(Sets.newHashSet("foo", "bar"));

        uut.enumerateNamespaces(conv.empty(), so);

        verify(so).onCompleted();
        verify(so).onNext(eq(conv.toProto(Sets.newHashSet("foo", "bar"))));
        verifyNoMoreInteractions(so);
    }

    @Test
    public void testEnumerateNamespacesThrows() throws Exception {
        uut = new FactStoreGrpcService(backend);
        StreamObserver so = mock(StreamObserver.class);
        when(backend.enumerateNamespaces()).thenThrow(UnsupportedOperationException.class);

        uut.enumerateNamespaces(conv.empty(), so);
        verify(so).onError(any(UnsupportedOperationException.class));

    }

    @Test
    public void testEnumerateTypes() throws Exception {
        uut = new FactStoreGrpcService(backend);
        StreamObserver so = mock(StreamObserver.class);

        when(backend.enumerateTypes(eq("ns"))).thenReturn(Sets.newHashSet("foo", "bar"));

        uut.enumerateTypes(conv.toProto("ns"), so);

        verify(so).onCompleted();
        verify(so).onNext(eq(conv.toProto(Sets.newHashSet("foo", "bar"))));
        verifyNoMoreInteractions(so);
    }

    @Test
    public void testEnumerateTypesThrows() throws Exception {
        uut = new FactStoreGrpcService(backend);
        StreamObserver so = mock(StreamObserver.class);
        when(backend.enumerateTypes(eq("ns"))).thenThrow(UnsupportedOperationException.class);

        uut.enumerateTypes(conv.toProto("ns"), so);
        verify(so).onError(any(UnsupportedOperationException.class));
    }

    @Test
    void testFetchByIdThrows() {
        UUID id = UUID.randomUUID();
        when(backend.fetchById(any(UUID.class))).thenThrow(UnsupportedOperationException.class);
        StreamObserver so = mock(StreamObserver.class);
        uut.fetchById(conv.toProto(id), so);
        verify(so).onError(any(UnsupportedOperationException.class));

    }

    @Test
    public void testPublishThrows() throws Exception {
        doThrow(UnsupportedOperationException.class).when(backend).publish(anyListOf(Fact.class));
        List<Fact> toPublish = Lists.newArrayList(Fact.builder().build("{}"));
        StreamObserver so = mock(StreamObserver.class);

        uut.publish(conv.toProto(toPublish), so);
        verify(so).onError(any(UnsupportedOperationException.class));
    }

    @Test
    public void testHandshake() throws Exception {

        StreamObserver so = mock(StreamObserver.class);
        uut.handshake(conv.empty(), so);

        verify(so).onCompleted();
        verify(so).onNext(any(MSG_ServerConfig.class));

    }

    @Test
    public void testRetrieveImplementationVersion() throws Exception {
        uut = spy(uut);
        when(uut.getProjectProperties()).thenReturn(this.getClass()
                .getResource(
                        "/test.properties"));
        HashMap<String, String> map = new HashMap<>();
        uut.retrieveImplementationVersion(map);

        assertEquals("9.9.9", map.get(Capabilities.FACTCAST_IMPL_VERSION.toString()));

    }

    @Test
    public void testRetrieveImplementationVersionEmptyPropertyFile() throws Exception {
        uut = spy(uut);
        when(uut.getProjectProperties()).thenReturn(this.getClass()
                .getResource(
                        "/no-version.properties"));
        HashMap<String, String> map = new HashMap<>();
        uut.retrieveImplementationVersion(map);

        assertEquals("UNKNOWN", map.get(Capabilities.FACTCAST_IMPL_VERSION.toString()));

    }

    @Test
    public void testRetrieveImplementationVersionCannotReadFile() throws Exception {
        uut = spy(uut);
        URL url = mock(URL.class);
        when(url.openStream()).thenReturn(null);
        when(uut.getProjectProperties()).thenReturn(url);
        HashMap<String, String> map = new HashMap<>();
        uut.retrieveImplementationVersion(map);

        assertEquals("UNKNOWN", map.get(Capabilities.FACTCAST_IMPL_VERSION.toString()));

    }

    @Test
    public void testInvalidate() throws Exception {

        {
            UUID id = UUID.randomUUID();
            MSG_UUID req = conv.toProto(id);
            StreamObserver o = mock(StreamObserver.class);
            uut.invalidate(req, o);

            verify(backend).invalidate(eq(new StateToken(id)));
            verify(o).onNext(any());
            verify(o).onCompleted();
        }

        {
            doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend).invalidate(any());

            UUID id = UUID.randomUUID();
            MSG_UUID req = conv.toProto(id);
            StreamObserver o = mock(StreamObserver.class);
            uut.invalidate(req, o);

            verify(backend).invalidate(eq(new StateToken(id)));
            verify(o).onError(any());
            verifyNoMoreInteractions(o);
        }

    }

    @Test
    public void testStateFor() throws Exception {

        {
            UUID id = UUID.randomUUID();

            StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "foo");
            MSG_StateForRequest req = conv.toProto(sfr);
            StreamObserver o = mock(StreamObserver.class);
            UUID token = UUID.randomUUID();
            when(backend.stateFor(any(), any())).thenReturn(new StateToken(token));
            uut.stateFor(req, o);

            verify(backend).stateFor(eq(Lists.newArrayList(id)), eq(Optional.of("foo")));
            verify(o).onNext(eq(conv.toProto(token)));
            verify(o).onCompleted();
        }

        {
            doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend)
                    .stateFor(any(),
                            any());

            UUID id = UUID.randomUUID();
            StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "foo");
            MSG_StateForRequest req = conv.toProto(sfr);
            StreamObserver o = mock(StreamObserver.class);

            uut.stateFor(req, o);

            verify(o).onError(any());
            verifyNoMoreInteractions(o);
        }

    }

    @Test
    public void testPublishConditional() throws Exception {
        {
            UUID id = UUID.randomUUID();

            ConditionalPublishRequest sfr = new ConditionalPublishRequest(Lists.newArrayList(), id);
            MSG_ConditionalPublishRequest req = conv.toProto(sfr);
            StreamObserver o = mock(StreamObserver.class);
            when(backend.publishIfUnchanged(any(), any())).thenReturn(true);

            uut.publishConditional(req, o);

            verify(backend).publishIfUnchanged(eq(Lists.newArrayList()), eq(Optional.of(
                    new StateToken(id))));
            verify(o).onNext(eq(conv.toProto(true)));
            verify(o).onCompleted();
        }

        {
            doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend)
                    .publishIfUnchanged(any(), any());

            UUID id = UUID.randomUUID();

            ConditionalPublishRequest sfr = new ConditionalPublishRequest(Lists.newArrayList(), id);
            MSG_ConditionalPublishRequest req = conv.toProto(sfr);
            StreamObserver o = mock(StreamObserver.class);

            uut.publishConditional(req, o);

            verify(o).onError(any());
            verifyNoMoreInteractions(o);
        }
    }

}
