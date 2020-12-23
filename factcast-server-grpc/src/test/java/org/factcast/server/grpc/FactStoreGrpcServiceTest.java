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

import static org.junit.jupiter.api.Assertions.*;
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
import lombok.val;
import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.grpc.api.Capabilities;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts.Builder;
import org.factcast.server.grpc.auth.FactCastAccount;
import org.factcast.server.grpc.auth.FactCastAuthority;
import org.factcast.server.grpc.auth.FactCastUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.intercept.RunAsUserToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
public class FactStoreGrpcServiceTest {

  @Mock FactStore backend;
  @Mock GrpcRequestMetadata meta;

  @InjectMocks FactStoreGrpcService uut;

  @Captor ArgumentCaptor<List<Fact>> acFactList;

  final ProtoConverter conv = new ProtoConverter();

  @Captor private ArgumentCaptor<SubscriptionRequestTO> reqCaptor;

  private final FactCastUser PRINCIPAL = new FactCastUser(FactCastAccount.GOD, "DISABLED");

  @BeforeEach
  void setUp() {
    uut = new FactStoreGrpcService(backend, meta);

    SecurityContextHolder.setContext(
        new SecurityContext() {
          Authentication testToken =
              new TestToken(new FactCastUser(FactCastAccount.GOD, "DISABLED"));

          private static final long serialVersionUID = 1L;

          @Override
          public void setAuthentication(Authentication authentication) {
            this.testToken = authentication;
          }

          @Override
          public Authentication getAuthentication() {
            return testToken;
          }
        });
  }

  @Test
  void currentTime() {
    val store = mock(FactStore.class);
    val uut = new FactStoreGrpcService(store, meta);
    when(store.currentTime()).thenReturn(101L);
    StreamObserver<MSG_CurrentDatabaseTime> stream = mock(StreamObserver.class);

    uut.currentTime(MSG_Empty.getDefaultInstance(), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(101L)));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void currentTimeWithException() {
    when(backend.currentTime()).thenThrow(RuntimeException.class);
    StreamObserver<MSG_CurrentDatabaseTime> stream = mock(StreamObserver.class);

    uut.currentTime(MSG_Empty.getDefaultInstance(), stream);

    verify(stream).onError(any(RuntimeException.class));
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchById() {
    val store = mock(FactStore.class);
    val uut = new FactStoreGrpcService(store, meta);
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
    val expected = Optional.of(fact);
    when(store.fetchById(fact.id())).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchById(new ProtoConverter().toProto(fact.id()), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(Optional.of(fact))));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIEmpty() {
    val store = mock(FactStore.class);
    val uut = new FactStoreGrpcService(store, meta);

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
    val store = mock(FactStore.class);
    val uut = new FactStoreGrpcService(store, meta);
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
    when(store.fetchById(fact.id())).thenThrow(IllegalMonitorStateException.class);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchById(new ProtoConverter().toProto(fact.id()), stream);

    verify(stream, never()).onNext(any());
    verify(stream, never()).onCompleted();
    verify(stream).onError(any(IllegalMonitorStateException.class));
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIdAndVersion() throws TransformationException {
    val store = mock(FactStore.class);
    val uut = new FactStoreGrpcService(store, meta);
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
    val expected = Optional.of(fact);
    when(store.fetchByIdAndVersion(fact.id(), 1)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchByIdAndVersion(new ProtoConverter().toProto(fact.id(), 1), stream);

    verify(stream).onNext(eq(new ProtoConverter().toProto(Optional.of(fact))));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIdAndVersionEmpty() throws TransformationException {
    val store = mock(FactStore.class);
    val uut = new FactStoreGrpcService(store, meta);
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
  void testSubscribeFacts() {
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
    when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);
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
    when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);

    val sre =
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
    when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);

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
  void testSubscribeExhaustCheckDisabledp() {
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
    when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);

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
    when(backend.subscribe(this.reqCaptor.capture(), any())).thenReturn(null);

    val sre =
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
    uut = new FactStoreGrpcService(backend, meta);
    StreamObserver so = mock(StreamObserver.class);
    when(backend.enumerateNamespaces()).thenThrow(UnsupportedOperationException.class);

    uut.enumerateNamespaces(conv.empty(), so);
    verify(so).onError(any(UnsupportedOperationException.class));
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
    uut = new FactStoreGrpcService(backend, meta);
    StreamObserver so = mock(StreamObserver.class);
    when(backend.enumerateTypes(eq("ns"))).thenThrow(UnsupportedOperationException.class);

    uut.enumerateTypes(conv.toProto("ns"), so);
    verify(so).onError(any(UnsupportedOperationException.class));
  }

  @Test
  public void testPublishThrows() {
    doThrow(UnsupportedOperationException.class).when(backend).publish(anyList());
    List<Fact> toPublish = Lists.newArrayList(Fact.builder().build("{}"));
    StreamObserver so = mock(StreamObserver.class);

    uut.publish(conv.toProto(toPublish), so);
    verify(so).onError(any(UnsupportedOperationException.class));
  }

  @Test
  public void testHandshake() {

    StreamObserver so = mock(StreamObserver.class);
    uut.handshake(conv.empty(), so);

    verify(so).onCompleted();
    verify(so).onNext(any(MSG_ServerConfig.class));
  }

  @Test
  public void testRetrieveImplementationVersion() {
    uut = spy(uut);
    when(uut.getProjectProperties()).thenReturn(this.getClass().getResource("/test.properties"));
    HashMap<String, String> map = new HashMap<>();
    uut.retrieveImplementationVersion(map);

    assertEquals("9.9.9", map.get(Capabilities.FACTCAST_IMPL_VERSION.toString()));
  }

  @Test
  public void testRetrieveImplementationVersionEmptyPropertyFile() {
    uut = spy(uut);
    when(uut.getProjectProperties())
        .thenReturn(this.getClass().getResource("/no-version.properties"));
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
  public void testStateFor() {

    {
      UUID id = UUID.randomUUID();

      StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "foo");
      MSG_StateForRequest req = conv.toProto(sfr);
      StreamObserver o = mock(StreamObserver.class);
      UUID token = UUID.randomUUID();
      when(backend.stateFor(any())).thenReturn(new StateToken(token));
      uut.stateFor(req, o);

      verify(backend).stateFor(any());
      verify(o).onNext(eq(conv.toProto(token)));
      verify(o).onCompleted();
    }

    {
      doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend).stateFor(any());

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
  public void testPublishConditional() {
    {
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

    {
      doThrow(new StatusRuntimeException(Status.DATA_LOSS))
          .when(backend)
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
    } catch (StatusException s) {
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
    } catch (StatusException s) {
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
    } catch (StatusException s) {
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

      val matches = Arrays.stream(ex).anyMatch(e -> e.isInstance(actual));
      if (!matches) {
        fail("Wrong exception, expected " + Arrays.toString(ex) + " but got " + actual);
      }
    }
  }

  @Test
  void stateForSpecsJson() {
    val list =
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
  void stateForSpecsJsonEmpty() {
    List<FactSpec> list = Lists.newArrayList();
    MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
    StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);

    // ACT
    uut.stateForSpecsJson(req, obs);

    verify(obs).onError(any(IllegalArgumentException.class));
  }

  @Test
  void invalidateStateToken() {

    val id = UUID.randomUUID();
    val req = conv.toProto(id);
    val stateToken = new StateToken(id);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);

    // ACT
    uut.invalidate(req, obs);

    verify(backend).invalidate(eq(stateToken));
    verify(obs).onNext(any(MSG_Empty.class));
    verify(obs).onCompleted();
  }

  @Test
  void invalidateStateTokenWithError() {

    val id = UUID.randomUUID();
    val req = conv.toProto(id);
    val stateToken = new StateToken(id);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);
    doThrow(RuntimeException.class).when(backend).invalidate(any());
    // ACT
    uut.invalidate(req, obs);

    verify(backend).invalidate(eq(stateToken));
    verify(obs).onError(any());
  }

  @Test
  void getFactcastUserWithoutPrincipal() throws StatusException {
    SecurityContextHolder.setContext(
        new SecurityContext() {
          Authentication testToken = new TokenWithoutPrincipal();

          private static final long serialVersionUID = 1L;

          @Override
          public void setAuthentication(Authentication authentication) {
            this.testToken = authentication;
          }

          @Override
          public Authentication getAuthentication() {
            return testToken;
          }
        });

    assertThrows(StatusException.class, () -> uut.getFactcastUser());
  }

  @Test
  void clearSnapshot() {

    val id = new SnapshotId("foo", UUID.randomUUID());
    val req = conv.toProto(id);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);

    // ACT
    uut.clearSnapshot(req, obs);

    verify(backend).clearSnapshot(eq(id));
    verify(obs).onNext(any(MSG_Empty.class));
    verify(obs).onCompleted();
  }

  static class TestException extends RuntimeException {}

  @Test
  void clearSnapshotWithException() {

    val id = new SnapshotId("foo", UUID.randomUUID());
    val req = conv.toProto(id);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);
    doThrow(TestException.class).when(backend).clearSnapshot(eq(id));

    // ACT
    uut.clearSnapshot(req, obs);

    verify(obs).onError(any(TestException.class));
  }

  @Test
  void getSnapshot() {
    val id = new SnapshotId("foo", UUID.randomUUID());
    val req = conv.toProto(id);
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
    val id = new SnapshotId("foo", UUID.randomUUID());
    val req = conv.toProto(id);
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
    val id = new SnapshotId("foo", UUID.randomUUID());
    val req = conv.toProto(id);
    StreamObserver<MSG_OptionalSnapshot> obs = mock(StreamObserver.class);
    Optional<Snapshot> optSnap = Optional.empty();
    when(backend.getSnapshot(id)).thenThrow(TestException.class);

    // ACT
    uut.getSnapshot(req, obs);

    verify(backend).getSnapshot(eq(id));
    verify(obs).onError(any(TestException.class));
  }

  @Test
  void setSnapshot() {
    val id = new SnapshotId("foo", UUID.randomUUID());
    Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
    val req = conv.toProto(snap);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);

    // ACT
    uut.setSnapshot(req, obs);

    verify(backend).setSnapshot(snap);
    verify(obs).onNext(any(MSG_Empty.class));
    verify(obs).onCompleted();
  }

  @Test
  void setSnapshotWithException() {
    val id = new SnapshotId("foo", UUID.randomUUID());
    Snapshot snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
    val req = conv.toProto(snap);
    StreamObserver<MSG_Empty> obs = mock(StreamObserver.class);
    doThrow(TestException.class).when(backend).setSnapshot(any());

    // ACT
    uut.setSnapshot(req, obs);

    verify(backend).setSnapshot(snap);
    verify(obs).onError(any(TestException.class));
  }
}
