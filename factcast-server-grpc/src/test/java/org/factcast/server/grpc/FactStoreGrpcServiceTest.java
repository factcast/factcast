/*
 * Copyright © 2017-2020 factcast.org
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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.net.URL;
import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts.Builder;
import org.factcast.server.grpc.auth.FactCastAccount;
import org.factcast.server.grpc.auth.FactCastAuthority;
import org.factcast.server.grpc.auth.FactCastUser;
import org.factcast.server.grpc.metrics.NOPServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetrics.OP;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.intercept.RunAsUserToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
@ExtendWith(MockitoExtension.class)
public class FactStoreGrpcServiceTest {

  @Mock FactStore backend;
  @Mock GrpcRequestMetadata meta;
  @Mock FastForwardTarget ffwdTarget;

  @Mock(lenient = true)
  GrpcLimitProperties grpcLimitProperties;

  @Mock GrpcRequestMetadata grpcRequestMetadata;
  @Spy ServerMetrics metrics = new NOPServerMetrics();

  @InjectMocks FactStoreGrpcService uut;

  @Captor ArgumentCaptor<List<Fact>> acFactList;

  final ProtoConverter conv = new ProtoConverter();

  @Captor private ArgumentCaptor<SubscriptionRequestTO> reqCaptor;

  private final FactCastUser PRINCIPAL = new FactCastUser(FactCastAccount.GOD, "DISABLED");

  @BeforeEach
  void setUp() {

    when(grpcLimitProperties.numberOfCatchupRequestsAllowedPerClientPerMinute()).thenReturn(5);
    when(grpcLimitProperties.initialNumberOfCatchupRequestsAllowedPerClient()).thenReturn(5);

    SecurityContextHolder.setContext(
        new SecurityContext() {
          Authentication testToken =
              new TestToken(new FactCastUser(FactCastAccount.GOD, "DISABLED"));

          private static final long serialVersionUID = 1L;

          @Override
          public void setAuthentication(Authentication authentication) {
            testToken = authentication;
          }

          @Override
          public Authentication getAuthentication() {
            return testToken;
          }
        });
  }

  @Test
  void currentTime() {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, meta);
    when(store.currentTime()).thenReturn(101L);
    StreamObserver<MSG_CurrentDatabaseTime> stream = mock(StreamObserver.class);

    uut.currentTime(MSG_Empty.getDefaultInstance(), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(101L)));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void currentTimeWithException() {
    assertThatThrownBy(
            () -> {
              when(backend.currentTime()).thenThrow(RuntimeException.class);
              StreamObserver<MSG_CurrentDatabaseTime> stream = mock(StreamObserver.class);

              uut.currentTime(MSG_Empty.getDefaultInstance(), stream);

              verifyNoMoreInteractions(stream);
            })
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void fetchById() {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, meta);
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
    var expected = Optional.of(fact);
    when(store.fetchById(fact.id())).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchById(new ProtoConverter().toProto(fact.id()), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(Optional.of(fact))));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIEmpty() {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, meta);

    Optional<Fact> expected = Optional.empty();
    UUID id = UUID.randomUUID();
    when(store.fetchById(id)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchById(new ProtoConverter().toProto(id), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(Optional.empty())));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIdThrowingException() {
    assertThatThrownBy(
            () -> {
              var store = mock(FactStore.class);
              var uut = new FactStoreGrpcService(store, meta);
              Fact fact =
                  Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
              when(store.fetchById(fact.id())).thenThrow(IllegalMonitorStateException.class);
              StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

              uut.fetchById(new ProtoConverter().toProto(fact.id()), stream);

              verify(stream, never()).onNext(any());
              verify(stream, never()).onCompleted();

              verifyNoMoreInteractions(stream);
            })
        .isInstanceOf(IllegalMonitorStateException.class);
  }

  @Test
  void fetchByIdAndVersion() throws TransformationException {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, meta);
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
    var expected = Optional.of(fact);
    when(store.fetchByIdAndVersion(fact.id(), 1)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchByIdAndVersion(new ProtoConverter().toProto(fact.id(), 1), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(Optional.of(fact))));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIdAndVersionEmpty() throws TransformationException {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, meta);
    Optional<Fact> expected = Optional.empty();
    @NonNull UUID id = UUID.randomUUID();
    when(store.fetchByIdAndVersion(id, 1)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchByIdAndVersion(new ProtoConverter().toProto(id, 1), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(Optional.empty())));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  static class TestToken extends RunAsUserToken {

    public TestToken(FactCastUser principal) {
      super(
          "GOD",
          principal,
          "",
          AuthorityUtils.createAuthorityList(FactCastAuthority.AUTHENTICATED),
          null);
    }

    private static final long serialVersionUID = 1L;
  }

  static class TokenWithoutPrincipal extends RunAsUserToken {

    public TokenWithoutPrincipal() {
      super(
          "BR0KEN",
          null,
          "",
          AuthorityUtils.createAuthorityList(FactCastAuthority.AUTHENTICATED),
          null);
    }

    private static final long serialVersionUID = 1L;
  }

  @Test
  void testPublishNull() {
    expectNPE(() -> uut.publish(null, mock(StreamObserver.class)));
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
  void testPublishTagging() {
    String clientId = "someApplication";
    when(grpcRequestMetadata.clientId()).thenReturn(Optional.of(clientId));
    doNothing().when(backend).publish(acFactList.capture());

    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);

    Fact f1 = Fact.builder().ns("test").build("{}");
    MSG_Fact msg1 = conv.toProto(f1);
    Builder b = MSG_Facts.newBuilder();
    b.addAllFact(Arrays.asList(msg1));
    MSG_Facts r = b.build();
    uut.publish(r, mock(StreamObserver.class));
    verify(backend).publish(acFactList.capture());
    List<Fact> facts = acFactList.getValue();
    assertFalse(facts.isEmpty());
    assertEquals(1, facts.size());
    assertEquals(f1.id(), facts.get(0).id());
    assertEquals(clientId, facts.get(0).meta("source"));
  }

  @Test
  void testSubscribeFacts() {
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
    when(backend.subscribe(reqCaptor.capture(), any())).thenReturn(null);
    uut.subscribe(
        new ProtoConverter().toProto(SubscriptionRequestTO.forFacts(req)),
        mock(ServerCallStreamObserver.class));
    verify(backend).subscribe(any(), any());
  }

  @Test
  void testSubscribeExhaustContinous() {
    uut =
        new FactStoreGrpcService(
            backend,
            meta,
            new GrpcLimitProperties()
                .initialNumberOfFollowRequestsAllowedPerClient(3)
                .numberOfFollowRequestsAllowedPerClientPerMinute(1));
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
    when(backend.subscribe(reqCaptor.capture(), any())).thenReturn(null);

    var sre =
        assertThrows(
            StatusRuntimeException.class,
            () -> {
              for (int i = 0; i < 10; i++) {
                uut.subscribe(
                    new ProtoConverter()
                        .toProto(SubscriptionRequestTO.forFacts(req).continuous(true)),
                    mock(ServerCallStreamObserver.class));
              }
            });

    assertEquals(Status.RESOURCE_EXHAUSTED, sre.getStatus());
  }

  @Test
  void testSubscribeExhaustNotTriggeredWithDifferentRequests() {
    uut =
        new FactStoreGrpcService(
            backend,
            meta,
            new GrpcLimitProperties()
                .initialNumberOfCatchupRequestsAllowedPerClient(3)
                .numberOfCatchupRequestsAllowedPerClientPerMinute(1)
                .initialNumberOfFollowRequestsAllowedPerClient(3)
                .numberOfFollowRequestsAllowedPerClientPerMinute(3));
    when(backend.subscribe(reqCaptor.capture(), any())).thenReturn(null);

    // must not throw exception
    for (int i = 0; i < 10; i++) {
      SubscriptionRequest req =
          SubscriptionRequest.catchup(FactSpec.ns("foo").aggId(UUID.randomUUID())).fromNowOn();
      uut.subscribe(
          new ProtoConverter().toProto(SubscriptionRequestTO.forFacts(req).continuous(true)),
          mock(ServerCallStreamObserver.class));
    }
  }

  @Test
  void testSubscribeExhaustCheckDisabled() {
    uut =
        new FactStoreGrpcService(
            backend,
            meta,
            new GrpcLimitProperties()
                .initialNumberOfCatchupRequestsAllowedPerClient(3)
                .numberOfCatchupRequestsAllowedPerClientPerMinute(1)
                .initialNumberOfFollowRequestsAllowedPerClient(3)
                .numberOfFollowRequestsAllowedPerClientPerMinute(1)
                .disabled(true));
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
    when(backend.subscribe(reqCaptor.capture(), any())).thenReturn(null);

    // must not throw exception
    for (int i = 0; i < 10; i++) {
      uut.subscribe(
          new ProtoConverter().toProto(SubscriptionRequestTO.forFacts(req).continuous(true)),
          mock(ServerCallStreamObserver.class));
    }
  }

  @Test
  void testSubscribeExhaustCatchup() {
    uut =
        new FactStoreGrpcService(
            backend,
            meta,
            new GrpcLimitProperties()
                .initialNumberOfCatchupRequestsAllowedPerClient(3)
                .numberOfCatchupRequestsAllowedPerClientPerMinute(1)
                .initialNumberOfFollowRequestsAllowedPerClient(3)
                .numberOfFollowRequestsAllowedPerClientPerMinute(1));
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
    when(backend.subscribe(reqCaptor.capture(), any())).thenReturn(null);

    var sre =
        assertThrows(
            StatusRuntimeException.class,
            () -> {
              for (int i = 0; i < 10; i++) {
                uut.subscribe(
                    new ProtoConverter()
                        .toProto(SubscriptionRequestTO.forFacts(req).continuous(true)),
                    mock(ServerCallStreamObserver.class));
              }
            });

    assertEquals(Status.RESOURCE_EXHAUSTED, sre.getStatus());
  }

  @Test
  public void testSerialOf() {
    uut = new FactStoreGrpcService(backend, meta);

    StreamObserver so = mock(StreamObserver.class);
    assertThrows(NullPointerException.class, () -> uut.serialOf(null, so));

    UUID id = new UUID(0, 1);
    OptionalLong twenty_two = OptionalLong.of(22);
    when(backend.serialOf(id)).thenReturn(twenty_two);

    uut.serialOf(conv.toProto(id), so);

    verify(so).onCompleted();
    verify(so).onNext(conv.toProto(twenty_two));
    verifyNoMoreInteractions(so);
  }

  @Test
  public void testSerialOfThrows() {
    uut = new FactStoreGrpcService(backend, meta);

    StreamObserver so = mock(StreamObserver.class);
    when(backend.serialOf(any(UUID.class))).thenThrow(UnsupportedOperationException.class);

    assertThrows(
        UnsupportedOperationException.class,
        () -> uut.serialOf(conv.toProto(UUID.randomUUID()), so));
  }

  @Test
  public void testEnumerateNamespaces() {
    uut = new FactStoreGrpcService(backend, meta);
    StreamObserver so = mock(StreamObserver.class);
    when(backend.enumerateNamespaces()).thenReturn(Sets.newHashSet("foo", "bar"));

    uut.enumerateNamespaces(conv.empty(), so);

    verify(so).onCompleted();
    verify(so).onNext(eq(conv.toProto(Sets.newHashSet("foo", "bar"))));
    verifyNoMoreInteractions(so);
  }

  @Test
  public void testEnumerateNamespacesThrows() {
    assertThatThrownBy(
            () -> {
              uut = new FactStoreGrpcService(backend, meta);
              StreamObserver so = mock(StreamObserver.class);
              when(backend.enumerateNamespaces()).thenThrow(UnsupportedOperationException.class);

              uut.enumerateNamespaces(conv.empty(), so);
            })
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void testEnumerateTypes() {
    uut = new FactStoreGrpcService(backend, meta);
    StreamObserver so = mock(StreamObserver.class);

    when(backend.enumerateTypes(eq("ns"))).thenReturn(Sets.newHashSet("foo", "bar"));

    uut.enumerateTypes(conv.toProto("ns"), so);

    verify(so).onCompleted();
    verify(so).onNext(eq(conv.toProto(Sets.newHashSet("foo", "bar"))));
    verifyNoMoreInteractions(so);
  }

  @Test
  public void testEnumerateTypesThrows() {
    assertThatThrownBy(
            () -> {
              uut = new FactStoreGrpcService(backend, meta);
              StreamObserver so = mock(StreamObserver.class);
              when(backend.enumerateTypes(eq("ns"))).thenThrow(UnsupportedOperationException.class);

              uut.enumerateTypes(conv.toProto("ns"), so);
            })
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void testPublishThrows() {
    assertThatThrownBy(
            () -> {
              doThrow(UnsupportedOperationException.class).when(backend).publish(anyList());
              List<Fact> toPublish = Lists.newArrayList(Fact.builder().build("{}"));
              StreamObserver so = mock(StreamObserver.class);

              uut.publish(conv.toProto(toPublish), so);
            })
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void testHandshake() {

    StreamObserver so = mock(StreamObserver.class);
    MSG_Empty empty = conv.empty();
    uut.handshake(empty, so);

    verify(metrics).timed(same(OP.HANDSHAKE), any(Runnable.class));

    verify(so).onCompleted();
    verify(so).onNext(any(MSG_ServerConfig.class));
  }

  @Test
  public void testRetrieveImplementationVersion() {
    uut = spy(uut);
    when(uut.getProjectProperties()).thenReturn(getClass().getResource("/test.properties"));
    HashMap<String, String> map = new HashMap<>();
    uut.retrieveImplementationVersion(map);

    assertEquals("9.9.9", map.get(Capabilities.FACTCAST_IMPL_VERSION.toString()));
  }

  @Test
  public void testRetrieveImplementationVersionEmptyPropertyFile() {
    uut = spy(uut);
    when(uut.getProjectProperties()).thenReturn(getClass().getResource("/no-version.properties"));
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
  public void testInvalidate() {
    UUID id = UUID.randomUUID();
    MSG_UUID req = conv.toProto(id);
    StreamObserver o = mock(StreamObserver.class);
    uut.invalidate(req, o);

    verify(backend).invalidate(eq(new StateToken(id)));
    verify(o).onNext(any());
    verify(o).onCompleted();
  }

  @Test
  public void testInvalidatePropagatesGRPCException() {
    doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend).invalidate(any());

    UUID id = UUID.randomUUID();
    MSG_UUID req = conv.toProto(id);
    StreamObserver o = mock(StreamObserver.class);
    assertThatThrownBy(
            () -> {
              uut.invalidate(req, o);
            })
        .isInstanceOf(StatusRuntimeException.class);
    verify(backend).invalidate(eq(new StateToken(id)));
    verifyNoMoreInteractions(o);
  }

  @Test
  public void testStateFor() {
    UUID id = UUID.randomUUID();

    StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "foo");
    MSG_StateForRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);
    UUID token = UUID.randomUUID();
    when(backend.stateFor(any())).thenReturn(new StateToken(token));
    uut.stateFor(req, o);
    ArrayList<FactSpec> expectedFactSpecs = Lists.newArrayList(FactSpec.ns("foo").aggId(id));

    verify(backend).stateFor(eq(expectedFactSpecs));
    verify(o).onNext(eq(conv.toProto(token)));
    verify(o).onCompleted();
  }

  @Test
  public void testStateForPropagatesGRPCException() {
    doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend).stateFor(any());

    UUID id = UUID.randomUUID();
    StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "foo");
    MSG_StateForRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);
    assertThatThrownBy(
            () -> {
              uut.stateFor(req, o);
            })
        .isInstanceOf(StatusRuntimeException.class);
    ArrayList<FactSpec> expectedFactSpecs = Lists.newArrayList(FactSpec.ns("foo").aggId(id));
    verify(backend).stateFor(eq(expectedFactSpecs));
    verifyNoMoreInteractions(o);
  }

  @Test
  public void testStateForNotAllowedOnNS() {
    UUID id = UUID.randomUUID();

    FactCastUser mockedFactCastUser = mock(FactCastUser.class);
    when(mockedFactCastUser.canRead("denied")).thenReturn(false);

    Authentication mockedAuthentication = mock(Authentication.class);
    when(mockedAuthentication.getPrincipal()).thenReturn(mockedFactCastUser);

    SecurityContext mockedSecurityContext = mock(SecurityContext.class);
    when(mockedSecurityContext.getAuthentication()).thenReturn(mockedAuthentication);

    try (MockedStatic<SecurityContextHolder> utilities =
        Mockito.mockStatic(SecurityContextHolder.class)) {
      utilities.when(() -> SecurityContextHolder.getContext()).thenReturn(mockedSecurityContext);

      StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "denied");
      MSG_StateForRequest req = conv.toProto(sfr);
      StreamObserver o = mock(StreamObserver.class);
      assertThatThrownBy(() -> uut.stateFor(req, o)).isInstanceOf(StatusRuntimeException.class);
    }
  }

  @Test
  public void testPublishConditional() {
    UUID id = UUID.randomUUID();

    ConditionalPublishRequest sfr = new ConditionalPublishRequest(Lists.newArrayList(), id);
    MSG_ConditionalPublishRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);
    when(backend.publishIfUnchanged(any(), any())).thenReturn(true);

    uut.publishConditional(req, o);

    verify(backend)
        .publishIfUnchanged(eq(Lists.newArrayList()), eq(Optional.of(new StateToken(id))));
    verify(o).onNext(eq(conv.toProto(true)));
    verify(o).onCompleted();
  }

  @Test
  public void testPublishConditionalPropagatesGRPCException() {
    doThrow(new StatusRuntimeException(Status.DATA_LOSS))
        .when(backend)
        .publishIfUnchanged(any(), any());

    UUID id = UUID.randomUUID();

    ConditionalPublishRequest sfr = new ConditionalPublishRequest(Lists.newArrayList(), id);
    MSG_ConditionalPublishRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);

    assertThatThrownBy(
            () -> {
              uut.publishConditional(req, o);
            })
        .isInstanceOf(StatusRuntimeException.class);
    verify(backend)
        .publishIfUnchanged(eq(Lists.newArrayList()), eq(Optional.of(new StateToken(id))));
    verifyNoMoreInteractions(o);
  }

  @Test
  public void testSourceTaggingPublishConditional() {
    String clientId = "someApplication";
    when(grpcRequestMetadata.clientId()).thenReturn(Optional.of(clientId));

    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);

    UUID id = UUID.randomUUID();

    Fact f = Fact.builder().ns("foo").type("bar").buildWithoutPayload();

    ConditionalPublishRequest sfr = new ConditionalPublishRequest(Lists.newArrayList(f), id);
    MSG_ConditionalPublishRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);
    when(backend.publishIfUnchanged(acFactList.capture(), eq(Optional.of(new StateToken(id)))))
        .thenReturn(true);

    uut.publishConditional(req, o);

    Fact fact = acFactList.getAllValues().get(0).get(0);
    assertThat(fact.meta("source")).isEqualTo(clientId);

    verify(o).onNext(eq(conv.toProto(true)));
    verify(o).onCompleted();
  }

  @Test
  public void testAssertCanReadString() {

    FactCastAccount account = mock(FactCastAccount.class);

    when(account.id()).thenReturn("mock");
    when(account.canRead(anyString())).thenReturn(false);

    SecurityContextHolder.getContext()
        .setAuthentication(new TestToken(new FactCastUser(account, "s3cr3t")));

    try {
      uut.assertCanRead("foo");
      fail();
    } catch (StatusRuntimeException s) {
      assertEquals(s.getStatus(), Status.PERMISSION_DENIED);
    } catch (Throwable s) {
      fail(s);
    }
  }

  @Test
  public void testAssertCanReadStrings() {

    FactCastAccount account = mock(FactCastAccount.class);

    when(account.id()).thenReturn("mock");
    when(account.canRead(anyString())).thenReturn(false);

    SecurityContextHolder.getContext()
        .setAuthentication(new TestToken(new FactCastUser(account, "s3cr3t")));

    try {
      uut.assertCanRead(Lists.newArrayList("foo", "bar"));
      fail();
    } catch (StatusRuntimeException s) {
      assertEquals(s.getStatus(), Status.PERMISSION_DENIED);
    } catch (Throwable s) {
      fail(s);
    }
  }

  @Test
  public void testAssertCanWriteStrings() {

    FactCastAccount account = mock(FactCastAccount.class);

    when(account.id()).thenReturn("mock");
    when(account.canWrite(anyString())).thenReturn(false);

    SecurityContextHolder.getContext()
        .setAuthentication(new TestToken(new FactCastUser(account, "s3cr3t")));

    try {
      uut.assertCanWrite(Lists.newArrayList("foo", "bar"));
      fail();
    } catch (StatusRuntimeException s) {
      assertEquals(s.getStatus(), Status.PERMISSION_DENIED);
    } catch (Throwable s) {
      fail(s);
    }
  }

  public static void expectNPE(Runnable r) {
    expect(r, NullPointerException.class, IllegalArgumentException.class);
  }

  public static void expect(Runnable r, Class<? extends Throwable>... ex) {
    try {
      r.run();
      fail("expected " + Arrays.toString(ex));
    } catch (Throwable actual) {

      var matches = Arrays.stream(ex).anyMatch(e -> e.isInstance(actual));
      if (!matches) {
        fail("Wrong exception, expected " + Arrays.toString(ex) + " but got " + actual);
      }
    }
  }

  @Test
  void stateForSpecsJson() {
    var list =
        Lists.newArrayList(
            FactSpec.ns("foo").type("bar").version(1), FactSpec.ns("foo").type("bar2").version(2));
    MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
    StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);
    UUID tokenId = UUID.randomUUID();

    when(backend.stateFor(eq(list))).thenReturn(new StateToken(tokenId));

    // ACT
    uut.stateForSpecsJson(req, obs);

    verify(obs).onNext(eq(conv.toProto(tokenId)));
    verify(obs).onCompleted();
  }

  @Test
  void currentStateForSpecsJson() {
    var list =
        Lists.newArrayList(
            FactSpec.ns("foo").type("bar").version(1), FactSpec.ns("foo").type("bar2").version(2));
    MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
    StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);
    UUID tokenId = UUID.randomUUID();

    when(backend.currentStateFor(eq(list))).thenReturn(new StateToken(tokenId));

    // ACT
    uut.currentStateForSpecsJson(req, obs);

    verify(obs).onNext(eq(conv.toProto(tokenId)));
    verify(obs).onCompleted();
  }

  @Test
  void stateForSpecsJsonEmpty() {
    List<FactSpec> list = Lists.newArrayList();
    MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
    StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);

    // ACT
    uut.stateForSpecsJson(req, obs);

    verify(obs).onError(any(IllegalArgumentException.class));
  }

  @Test
  void currentStateForSpecsJsonEmpty() {
    List<FactSpec> list = Lists.newArrayList();
    MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
    StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);

    // ACT
    uut.currentStateForSpecsJson(req, obs);

    verify(obs).onError(any(IllegalArgumentException.class));
  }

  @Test
  public void testStateForSpecsJsonNotAllowedOnNS() {
    FactCastUser mockedFactCastUser = mock(FactCastUser.class);
    when(mockedFactCastUser.canRead("denied")).thenReturn(false);

    Authentication mockedAuthentication = mock(Authentication.class);
    when(mockedAuthentication.getPrincipal()).thenReturn(mockedFactCastUser);

    SecurityContext mockedSecurityContext = mock(SecurityContext.class);
    when(mockedSecurityContext.getAuthentication()).thenReturn(mockedAuthentication);

    try (MockedStatic<SecurityContextHolder> utilities =
        Mockito.mockStatic(SecurityContextHolder.class)) {
      utilities.when(() -> SecurityContextHolder.getContext()).thenReturn(mockedSecurityContext);

      ArrayList<FactSpec> list = Lists.newArrayList(FactSpec.ns("denied").type("nope"));
      MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
      StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);
      assertThatThrownBy(() -> uut.stateForSpecsJson(req, obs))
          .isInstanceOf(StatusRuntimeException.class);
    }
  }

  @Test
  public void testCurrentStateForSpecsJsonNotAllowedOnNS() {
    FactCastUser mockedFactCastUser = mock(FactCastUser.class);
    when(mockedFactCastUser.canRead("denied")).thenReturn(false);

    Authentication mockedAuthentication = mock(Authentication.class);
    when(mockedAuthentication.getPrincipal()).thenReturn(mockedFactCastUser);

    SecurityContext mockedSecurityContext = mock(SecurityContext.class);
    when(mockedSecurityContext.getAuthentication()).thenReturn(mockedAuthentication);

    try (MockedStatic<SecurityContextHolder> utilities =
        Mockito.mockStatic(SecurityContextHolder.class)) {
      utilities.when(() -> SecurityContextHolder.getContext()).thenReturn(mockedSecurityContext);

      ArrayList<FactSpec> list = Lists.newArrayList(FactSpec.ns("denied").type("nope"));
      MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
      StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);
      assertThatThrownBy(() -> uut.currentStateForSpecsJson(req, obs))
          .isInstanceOf(StatusRuntimeException.class);
    }
  }

  @Test
  void invalidateStateToken() {

    var id = UUID.randomUUID();
    var req = conv.toProto(id);
    var stateToken = new StateToken(id);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);

    // ACT
    uut.invalidate(req, obs);

    verify(backend).invalidate(eq(stateToken));
    verify(obs).onNext(any(MSG_Empty.class));
    verify(obs).onCompleted();
  }

  @Test
  void invalidateStateTokenWithError() {

    assertThatThrownBy(
            () -> {
              var id = UUID.randomUUID();
              var req = conv.toProto(id);
              var stateToken = new StateToken(id);
              StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);
              doThrow(RuntimeException.class).when(backend).invalidate(any());
              // ACT
              uut.invalidate(req, obs);

              verify(backend).invalidate(eq(stateToken));
              verifyNoMoreInteractions(obs);
            })
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void getFactcastUserWithoutPrincipal() throws StatusException {
    SecurityContextHolder.setContext(
        new SecurityContext() {
          Authentication testToken = new TokenWithoutPrincipal();

          private static final long serialVersionUID = 1L;

          @Override
          public void setAuthentication(Authentication authentication) {
            testToken = authentication;
          }

          @Override
          public Authentication getAuthentication() {
            return testToken;
          }
        });

    assertThrows(StatusRuntimeException.class, () -> uut.getFactcastUser());
  }

  @Test
  void clearSnapshot() {

    var id = SnapshotId.of("foo", UUID.randomUUID());
    var req = conv.toProto(id);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);

    // ACT
    uut.clearSnapshot(req, obs);

    verify(backend).clearSnapshot(eq(id));
    verify(obs).onNext(any(MSG_Empty.class));
    verify(obs).onCompleted();
  }

  static class TestException extends RuntimeException {
    private static final long serialVersionUID = -3012325109668741715L;
  }

  @Test
  void clearSnapshotWithException() {
    assertThatThrownBy(
            () -> {
              var id = SnapshotId.of("foo", UUID.randomUUID());
              var req = conv.toProto(id);
              StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);
              doThrow(TestException.class).when(backend).clearSnapshot(eq(id));

              // ACT
              uut.clearSnapshot(req, obs);
            })
        .isInstanceOf(TestException.class);
  }

  @Test
  void getSnapshot() {
    var id = SnapshotId.of("foo", UUID.randomUUID());
    var req = conv.toProto(id);
    StreamObserver<MSG_OptionalSnapshot> obs = mock(StreamObserver.class);
    Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
    Optional<Snapshot> optSnap = Optional.of(snap);
    when(backend.getSnapshot(id)).thenReturn(optSnap);

    // ACT
    uut.getSnapshot(req, obs);

    verify(backend).getSnapshot(eq(id));
    verify(obs).onNext(eq(conv.toProtoSnapshot(optSnap)));
    verify(obs).onCompleted();
  }

  @Test
  void getSnapshotEmpty() {
    var id = SnapshotId.of("foo", UUID.randomUUID());
    var req = conv.toProto(id);
    StreamObserver<MSG_OptionalSnapshot> obs = mock(StreamObserver.class);
    Optional<Snapshot> optSnap = Optional.empty();
    when(backend.getSnapshot(id)).thenReturn(optSnap);

    // ACT
    uut.getSnapshot(req, obs);

    verify(backend).getSnapshot(eq(id));
    verify(obs).onNext(eq(conv.toProtoSnapshot(optSnap)));
    verify(obs).onCompleted();
  }

  @Test
  void getSnapshotException() {
    assertThatThrownBy(
            () -> {
              var id = SnapshotId.of("foo", UUID.randomUUID());
              var req = conv.toProto(id);
              StreamObserver<MSG_OptionalSnapshot> obs = mock(StreamObserver.class);
              Optional<Snapshot> optSnap = Optional.empty();
              when(backend.getSnapshot(id)).thenThrow(TestException.class);

              // ACT
              uut.getSnapshot(req, obs);

              verify(backend).getSnapshot(eq(id));
            })
        .isInstanceOf(TestException.class);
  }

  @Test
  void setSnapshot() {
    var id = SnapshotId.of("foo", UUID.randomUUID());
    Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
    var req = conv.toProto(snap);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);

    // ACT
    uut.setSnapshot(req, obs);

    verify(backend).setSnapshot(snap);
    verify(obs).onNext(any(MSG_Empty.class));
    verify(obs).onCompleted();
  }

  @Test
  void setSnapshotWithException() {
    assertThatThrownBy(
            () -> {
              var id = SnapshotId.of("foo", UUID.randomUUID());
              Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
              var req = conv.toProto(snap);
              StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);
              doThrow(TestException.class).when(backend).setSnapshot(any());

              // ACT
              uut.setSnapshot(req, obs);

              verify(backend).setSnapshot(snap);
              verifyNoMoreInteractions(obs);
            })
        .isInstanceOf(TestException.class);
  }

  @Test
  void testHeaderSourceTagging() {
    Fact f = Fact.builder().ns("x").meta("foo", "bar").buildWithoutPayload();
    f = uut.tagFactSource(f, "theSourceApplication");
    assertThat(f.meta("source")).isEqualTo("theSourceApplication");
  }

  @Test
  void testHeaderSourceTaggingOverwrites() {
    Fact f = Fact.builder().ns("x").meta("source", "before").buildWithoutPayload();
    f = uut.tagFactSource(f, "after");
    assertThat(f.meta("source")).isEqualTo("after");
  }

  @Test
  void testHeaderSourceTaggingWithBrokenHeader() {
    Fact f = spy(Fact.builder().buildWithoutPayload());
    when(f.jsonHeader()).thenReturn("{borken");
    Fact f1 = uut.tagFactSource(f, "after");
    assertSame(f, f1);
  }
}
