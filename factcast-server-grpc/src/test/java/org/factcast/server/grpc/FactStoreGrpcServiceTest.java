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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.io.Serial;
import java.time.LocalDate;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.store.StateToken;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.grpc.api.ConditionalPublishRequest;
import org.factcast.grpc.api.EnumerateVersionsRequest;
import org.factcast.grpc.api.StateForRequest;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Facts.Builder;
import org.factcast.server.grpc.metrics.NOPServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetrics.OP;
import org.factcast.server.security.auth.FactCastAccount;
import org.factcast.server.security.auth.FactCastAuthority;
import org.factcast.server.security.auth.FactCastUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock FastForwardTarget ffwdTarget;

  @Mock(lenient = true)
  GrpcLimitProperties grpcLimitProperties;

  @Mock GrpcRequestMetadata grpcRequestMetadata;
  @Spy ServerMetrics metrics = new NOPServerMetrics();

  @Captor ArgumentCaptor<List<Fact>> acFactList;

  final ProtoConverter conv = new ProtoConverter();

  @Captor private ArgumentCaptor<SubscriptionRequestTO> reqCaptor;

  FactStoreGrpcService uut;

  @BeforeEach
  void setUp() {

    when(grpcLimitProperties.numberOfCatchupRequestsAllowedPerClientPerMinute()).thenReturn(5);
    when(grpcLimitProperties.initialNumberOfCatchupRequestsAllowedPerClient()).thenReturn(5);

    SecurityContextHolder.setContext(
        new SecurityContext() {
          Authentication testToken =
              new TestToken(new FactCastUser(FactCastAccount.GOD, "DISABLED"));

          @Serial private static final long serialVersionUID = 1L;

          @Override
          public void setAuthentication(Authentication authentication) {
            testToken = authentication;
          }

          @Override
          public Authentication getAuthentication() {
            return testToken;
          }
        });

    lenient().when(grpcRequestMetadata.clientIdAsString()).thenReturn("testingClient");

    uut =
        new FactStoreGrpcService(
            backend, grpcRequestMetadata, grpcLimitProperties, ffwdTarget, metrics);
  }

  @Test
  void currentTime() {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, grpcRequestMetadata);
    when(store.currentTime()).thenReturn(101L);
    StreamObserver<MSG_CurrentDatabaseTime> stream = mock(StreamObserver.class);

    uut.currentTime(MSG_Empty.getDefaultInstance(), stream);

    verify(stream).onNext(new ProtoConverter().toProtoTime(101L));
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
    var uut = new FactStoreGrpcService(store, grpcRequestMetadata);
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
    var expected = Optional.of(fact);
    when(store.fetchById(fact.id())).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchById(new ProtoConverter().toProto(fact.id()), stream);

    verify(stream).onNext(new ProtoConverter().toProto(Optional.of(fact)));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIEmpty() {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, grpcRequestMetadata);

    Optional<Fact> expected = Optional.empty();
    UUID id = UUID.randomUUID();
    when(store.fetchById(id)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchById(new ProtoConverter().toProto(id), stream);

    verify(stream).onNext(new ProtoConverter().toProto(Optional.empty()));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIdThrowingException() {
    assertThatThrownBy(
            () -> {
              var store = mock(FactStore.class);
              var uut = new FactStoreGrpcService(store, grpcRequestMetadata);
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
    var uut = new FactStoreGrpcService(store, grpcRequestMetadata);
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).buildWithoutPayload();
    var expected = Optional.of(fact);
    when(store.fetchByIdAndVersion(fact.id(), 1)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchByIdAndVersion(new ProtoConverter().toProto(fact.id(), 1), stream);

    verify(stream).onNext(new ProtoConverter().toProto(Optional.of(fact)));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void fetchByIdAndVersionEmpty() throws TransformationException {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, grpcRequestMetadata);
    Optional<Fact> expected = Optional.empty();
    @NonNull UUID id = UUID.randomUUID();
    when(store.fetchByIdAndVersion(id, 1)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchByIdAndVersion(new ProtoConverter().toProto(id, 1), stream);

    verify(stream).onNext(new ProtoConverter().toProto(Optional.empty()));
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

    @Serial private static final long serialVersionUID = 1L;
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

    @Serial private static final long serialVersionUID = 1L;
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
    b.addAllFact(List.of(msg1));
    MSG_Facts r = b.build();
    uut.publish(r, mock(StreamObserver.class));
    verify(backend).publish(acFactList.capture());
    List<Fact> facts = acFactList.getValue();
    assertFalse(facts.isEmpty());
    assertEquals(1, facts.size());
    Fact f = facts.get(0);
    assertEquals(f1.id(), f.id());
    assertEquals(clientId, f.meta("source"));
    Assertions.assertThat(f.jsonHeader()).contains(clientId);
  }

  @Test
  void testSubscribeFacts() {
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
    when(backend.subscribe(reqCaptor.capture(), any())).thenReturn(null);
    uut.subscribe(
        new ProtoConverter().toProto(SubscriptionRequestTO.from(req)),
        mock(ServerCallStreamObserver.class));
    verify(backend).subscribe(any(), any());
  }

  @Test
  void testSubscribeExhaustContinuous() {
    uut =
        new FactStoreGrpcService(
            backend,
            grpcRequestMetadata,
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
                    new ProtoConverter().toProto(SubscriptionRequestTO.from(req).continuous(true)),
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
            grpcRequestMetadata,
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
          new ProtoConverter().toProto(SubscriptionRequestTO.from(req).continuous(true)),
          mock(ServerCallStreamObserver.class));
    }
  }

  @Test
  void testSubscribeExhaustCheckDisabled() {
    uut =
        new FactStoreGrpcService(
            backend,
            grpcRequestMetadata,
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
          new ProtoConverter().toProto(SubscriptionRequestTO.from(req).continuous(true)),
          mock(ServerCallStreamObserver.class));
    }
  }

  @Test
  void testSubscribeExhaustCatchup() {
    uut =
        new FactStoreGrpcService(
            backend,
            grpcRequestMetadata,
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
                    new ProtoConverter().toProto(SubscriptionRequestTO.from(req).continuous(true)),
                    mock(ServerCallStreamObserver.class));
              }
            });

    assertEquals(Status.RESOURCE_EXHAUSTED, sre.getStatus());
  }

  @Test
  void testSerialOf() {
    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);

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
  void testSerialOfThrows() {
    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);

    StreamObserver so = mock(StreamObserver.class);
    when(backend.serialOf(any(UUID.class))).thenThrow(UnsupportedOperationException.class);

    assertThrows(
        UnsupportedOperationException.class,
        () -> uut.serialOf(conv.toProto(UUID.randomUUID()), so));
  }

  @Test
  void testEnumerateNamespaces() {
    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);
    StreamObserver so = mock(StreamObserver.class);
    when(backend.enumerateNamespaces()).thenReturn(Sets.newHashSet("foo", "bar"));

    uut.enumerateNamespaces(conv.empty(), so);

    verify(so).onCompleted();
    verify(so).onNext(conv.toProto(Sets.newHashSet("foo", "bar")));
    verifyNoMoreInteractions(so);
  }

  @Test
  void testEnumerateNamespacesThrows() {
    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);
    StreamObserver so = mock(StreamObserver.class);
    when(backend.enumerateNamespaces()).thenThrow(UnsupportedOperationException.class);
    assertThatThrownBy(() -> uut.enumerateNamespaces(conv.empty(), so))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testEnumerateTypes() {
    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);
    StreamObserver so = mock(StreamObserver.class);

    when(backend.enumerateTypes("ns")).thenReturn(Sets.newHashSet("foo", "bar"));

    uut.enumerateTypes(conv.toProto("ns"), so);

    verify(so).onCompleted();
    verify(so).onNext(conv.toProto(Sets.newHashSet("foo", "bar")));
    verifyNoMoreInteractions(so);
  }

  @Test
  void testEnumerateTypesThrows() {
    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);
    StreamObserver so = mock(StreamObserver.class);
    when(backend.enumerateTypes("ns")).thenThrow(UnsupportedOperationException.class);

    assertThatThrownBy(() -> uut.enumerateTypes(conv.toProto("ns"), so))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testEnumerateVersions() {
    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);
    StreamObserver so = mock(StreamObserver.class);

    when(backend.enumerateVersions("ns", "type")).thenReturn(Sets.newHashSet(1, 2));

    uut.enumerateVersions(conv.toProto(new EnumerateVersionsRequest("ns", "type")), so);

    verify(so).onCompleted();
    verify(so).onNext(conv.toProtoIntSet(Sets.newHashSet(1, 2)));
    verifyNoMoreInteractions(so);
  }

  @Test
  void testEnumerateVersionsThrows() {
    when(backend.enumerateVersions("ns", "type")).thenThrow(UnsupportedOperationException.class);
    StreamObserver so = mock(StreamObserver.class);

    uut = new FactStoreGrpcService(backend, grpcRequestMetadata);
    final var request = conv.toProto(new EnumerateVersionsRequest("ns", "type"));

    assertThatThrownBy(() -> uut.enumerateVersions(request, so))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testPublishThrows() {
    doThrow(UnsupportedOperationException.class).when(backend).publish(anyList());
    List<Fact> toPublish = Lists.newArrayList(Fact.builder().build("{}"));
    StreamObserver so = mock(StreamObserver.class);

    assertThatThrownBy(() -> uut.publish(conv.toProto(toPublish), so))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void testHandshake() {
    when(grpcRequestMetadata.clientIdAsString()).thenReturn("funky-service");
    when(grpcRequestMetadata.clientVersionAsString()).thenReturn("3.11 for Workgroups");

    StreamObserver so = mock(StreamObserver.class);
    MSG_Empty empty = conv.empty();
    uut.handshake(empty, so);

    verify(metrics).timed(same(OP.HANDSHAKE), any(Runnable.class));
    ArgumentCaptor<Tags> tagsCaptor = ArgumentCaptor.forClass(Tags.class);
    verify(metrics).count(same(ServerMetrics.EVENT.CLIENT_VERSION), tagsCaptor.capture());
    verify(so).onCompleted();
    verify(so).onNext(any(MSG_ServerConfig.class));

    assertThat(tagsCaptor.getValue().stream())
        .hasSize(2)
        .contains(Tag.of("id", "funky-service"), Tag.of("version", "3.11 for Workgroups"));
  }

  @Test
  void testHandshakeWithUnknownVersion() {

    when(grpcRequestMetadata.clientIdAsString()).thenReturn("funky-service");
    when(grpcRequestMetadata.clientVersionAsString()).thenReturn(GrpcRequestMetadata.UNKNOWN);

    StreamObserver so = mock(StreamObserver.class);
    MSG_Empty empty = conv.empty();
    uut.handshake(empty, so);

    verify(metrics).timed(same(OP.HANDSHAKE), any(Runnable.class));
    ArgumentCaptor<Tags> tagsCaptor = ArgumentCaptor.forClass(Tags.class);
    verify(metrics).count(same(ServerMetrics.EVENT.CLIENT_VERSION), tagsCaptor.capture());
    verify(so).onCompleted();
    verify(so).onNext(any(MSG_ServerConfig.class));

    assertThat(tagsCaptor.getValue().stream())
        .hasSize(2)
        .contains(Tag.of("id", "funky-service"), Tag.of("version", GrpcRequestMetadata.UNKNOWN));
  }

  @Test
  void testInvalidate() {
    UUID id = UUID.randomUUID();
    MSG_UUID req = conv.toProto(id);
    StreamObserver o = mock(StreamObserver.class);
    uut.invalidate(req, o);

    verify(backend).invalidate(new StateToken(id));
    verify(o).onNext(any());
    verify(o).onCompleted();
  }

  @Test
  void testInvalidatePropagatesGRPCException() {
    doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend).invalidate(any());

    UUID id = UUID.randomUUID();
    MSG_UUID req = conv.toProto(id);
    StreamObserver o = mock(StreamObserver.class);
    assertThatThrownBy(() -> uut.invalidate(req, o)).isInstanceOf(StatusRuntimeException.class);
    verify(backend).invalidate(new StateToken(id));
    verifyNoMoreInteractions(o);
  }

  @Test
  void testStateFor() {
    UUID id = UUID.randomUUID();

    StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "foo");
    MSG_StateForRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);
    UUID token = UUID.randomUUID();
    when(backend.stateFor(any())).thenReturn(new StateToken(token));
    uut.stateFor(req, o);
    ArrayList<FactSpec> expectedFactSpecs = Lists.newArrayList(FactSpec.ns("foo").aggId(id));

    verify(backend).stateFor(expectedFactSpecs);
    verify(o).onNext(conv.toProto(token));
    verify(o).onCompleted();
  }

  @Test
  void testStateForPropagatesGRPCException() {
    doThrow(new StatusRuntimeException(Status.DATA_LOSS)).when(backend).stateFor(any());

    UUID id = UUID.randomUUID();
    StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "foo");
    MSG_StateForRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);
    assertThatThrownBy(() -> uut.stateFor(req, o)).isInstanceOf(StatusRuntimeException.class);
    ArrayList<FactSpec> expectedFactSpecs = Lists.newArrayList(FactSpec.ns("foo").aggId(id));
    verify(backend).stateFor(expectedFactSpecs);
    verifyNoMoreInteractions(o);
  }

  @Test
  void testStateForNotAllowedOnNS() {
    UUID id = UUID.randomUUID();

    FactCastUser mockedFactCastUser = mock(FactCastUser.class);
    when(mockedFactCastUser.canRead("denied")).thenReturn(false);

    Authentication mockedAuthentication = mock(Authentication.class);
    when(mockedAuthentication.getPrincipal()).thenReturn(mockedFactCastUser);

    SecurityContext mockedSecurityContext = mock(SecurityContext.class);
    when(mockedSecurityContext.getAuthentication()).thenReturn(mockedAuthentication);

    try (MockedStatic<SecurityContextHolder> utilities =
        Mockito.mockStatic(SecurityContextHolder.class)) {
      utilities.when(SecurityContextHolder::getContext).thenReturn(mockedSecurityContext);

      StateForRequest sfr = new StateForRequest(Lists.newArrayList(id), "denied");
      MSG_StateForRequest req = conv.toProto(sfr);
      StreamObserver o = mock(StreamObserver.class);
      assertThatThrownBy(() -> uut.stateFor(req, o)).isInstanceOf(StatusRuntimeException.class);
    }
  }

  @Test
  void testPublishConditional() {
    UUID id = UUID.randomUUID();

    ConditionalPublishRequest sfr = new ConditionalPublishRequest(Lists.newArrayList(), id);
    MSG_ConditionalPublishRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);
    when(backend.publishIfUnchanged(any(), any())).thenReturn(true);

    uut.publishConditional(req, o);

    verify(backend).publishIfUnchanged(Lists.newArrayList(), Optional.of(new StateToken(id)));
    verify(o).onNext(conv.toProto(true));
    verify(o).onCompleted();
  }

  @Test
  void testPublishConditionalPropagatesGRPCException() {
    doThrow(new StatusRuntimeException(Status.DATA_LOSS))
        .when(backend)
        .publishIfUnchanged(any(), any());

    UUID id = UUID.randomUUID();

    ConditionalPublishRequest sfr = new ConditionalPublishRequest(Lists.newArrayList(), id);
    MSG_ConditionalPublishRequest req = conv.toProto(sfr);
    StreamObserver o = mock(StreamObserver.class);

    assertThatThrownBy(() -> uut.publishConditional(req, o))
        .isInstanceOf(StatusRuntimeException.class);
    verify(backend).publishIfUnchanged(Lists.newArrayList(), Optional.of(new StateToken(id)));
    verifyNoMoreInteractions(o);
  }

  @Test
  void testSourceTaggingPublishConditional() {
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
    Assertions.assertThat(fact.jsonHeader()).contains(clientId);

    verify(o).onNext(conv.toProto(true));
    verify(o).onCompleted();
  }

  @Test
  void testAssertCanReadString() {

    FactCastAccount account = mock(FactCastAccount.class);

    when(account.id()).thenReturn("mock");
    when(account.canRead(anyString())).thenReturn(false);

    SecurityContextHolder.getContext()
        .setAuthentication(new TestToken(new FactCastUser(account, "s3cr3t")));

    try {
      uut.assertCanRead("foo");
      fail();
    } catch (StatusRuntimeException s) {
      assertThat(s.getStatus()).isEqualTo(Status.PERMISSION_DENIED);
    } catch (Throwable s) {
      fail(s);
    }
  }

  @Test
  void testAssertCanReadStrings() {

    FactCastAccount account = mock(FactCastAccount.class);

    when(account.id()).thenReturn("mock");
    when(account.canRead(anyString())).thenReturn(false);

    SecurityContextHolder.getContext()
        .setAuthentication(new TestToken(new FactCastUser(account, "s3cr3t")));

    try {
      uut.assertCanRead(Lists.newArrayList("foo", "bar"));
      fail();
    } catch (StatusRuntimeException s) {
      assertThat(s.getStatus()).isEqualTo(Status.PERMISSION_DENIED);
    } catch (Throwable s) {
      fail(s);
    }
  }

  @Test
  void testAssertCanWriteStrings() {

    FactCastAccount account = mock(FactCastAccount.class);

    when(account.id()).thenReturn("mock");
    when(account.canWrite(anyString())).thenReturn(false);

    SecurityContextHolder.getContext()
        .setAuthentication(new TestToken(new FactCastUser(account, "s3cr3t")));

    try {
      uut.assertCanWrite(Lists.newArrayList("foo", "bar"));
      fail();
    } catch (StatusRuntimeException s) {
      assertThat(s.getStatus()).isEqualTo(Status.PERMISSION_DENIED);
    } catch (Throwable s) {
      fail(s);
    }
  }

  static void expectNPE(Runnable r) {
    expect(r, NullPointerException.class, IllegalArgumentException.class);
  }

  static void expect(Runnable r, Class<? extends Throwable>... ex) {
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

    when(backend.stateFor(list)).thenReturn(new StateToken(tokenId));

    // ACT
    uut.stateForSpecsJson(req, obs);

    verify(obs).onNext(conv.toProto(tokenId));
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

    when(backend.currentStateFor(list)).thenReturn(new StateToken(tokenId));

    // ACT
    uut.currentStateForSpecsJson(req, obs);

    verify(obs).onNext(conv.toProto(tokenId));
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
      utilities.when(SecurityContextHolder::getContext).thenReturn(mockedSecurityContext);

      ArrayList<FactSpec> list = Lists.newArrayList(FactSpec.ns("denied").type("nope"));
      MSG_FactSpecsJson req = conv.toProtoFactSpecs(list);
      StreamObserver<MSG_UUID> obs = mock(StreamObserver.class);
      assertThatThrownBy(() -> uut.stateForSpecsJson(req, obs))
          .isInstanceOf(StatusRuntimeException.class);
    }
  }

  @Test
  void testCurrentStateForSpecsJsonNotAllowedOnNS() {
    FactCastUser mockedFactCastUser = mock(FactCastUser.class);
    when(mockedFactCastUser.canRead("denied")).thenReturn(false);

    Authentication mockedAuthentication = mock(Authentication.class);
    when(mockedAuthentication.getPrincipal()).thenReturn(mockedFactCastUser);

    SecurityContext mockedSecurityContext = mock(SecurityContext.class);
    when(mockedSecurityContext.getAuthentication()).thenReturn(mockedAuthentication);

    try (MockedStatic<SecurityContextHolder> utilities =
        Mockito.mockStatic(SecurityContextHolder.class)) {
      utilities.when(SecurityContextHolder::getContext).thenReturn(mockedSecurityContext);

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

    verify(backend).invalidate(stateToken);
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

              verify(backend).invalidate(stateToken);
              verifyNoMoreInteractions(obs);
            })
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void getFactcastUserWithoutPrincipal() throws StatusException {
    SecurityContextHolder.setContext(
        new SecurityContext() {
          Authentication testToken = new TokenWithoutPrincipal();

          @Serial private static final long serialVersionUID = 1L;

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
  void testHeaderSourceTagging() {
    Fact f = Fact.builder().ns("x").meta("foo", "bar").buildWithoutPayload();
    f = uut.tagFactSource(f, "theSourceApplication");
    assertThat(f.meta("source")).isEqualTo("theSourceApplication");
    Assertions.assertThat(f.jsonHeader()).contains("theSourceApplication");
  }

  @Test
  void testHeaderSourceTaggingOverwrites() {
    Fact f = Fact.builder().ns("x").meta("source", "before").buildWithoutPayload();
    f = uut.tagFactSource(f, "after");
    assertThat(f.meta("source")).isEqualTo("after");
    Assertions.assertThat(f.jsonHeader()).contains("after");
  }

  @SneakyThrows
  @Test
  void logsServerVersion() {
    LogCaptor logCaptor = LogCaptor.forClass(FactStoreGrpcService.class);
    logCaptor.setLogLevelToInfo();

    uut.afterPropertiesSet();

    assertThat(logCaptor.getInfoLogs()).anyMatch(s -> s.startsWith("Service version: "));
  }

  @Test
  void onlyTouchesServerCall() {
    StreamObserver<?> so = mock(StreamObserver.class);
    uut.initialize(so);
    verifyNoMoreInteractions(so);
  }

  @Test
  void setsDefaultCancelHandler() {
    ServerCallStreamObserver<?> responseObserver = mock(ServerCallStreamObserver.class);
    uut.initialize(responseObserver);
    verify(responseObserver, times(1)).setOnCancelHandler(notNull());
  }

  @Test
  void defaultCancelHandlerThrows() {

    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    ServerCallStreamObserver<?> responseObserver = mock(ServerCallStreamObserver.class);
    doNothing().when(responseObserver).setOnCancelHandler(captor.capture());
    uut.initialize(responseObserver);

    Runnable handler = captor.getValue();
    assertThatThrownBy(handler::run).isInstanceOf(RequestCanceledByClientException.class);
  }

  @Test
  void fetchBySerial() {
    var store = mock(FactStore.class);
    var uut = new FactStoreGrpcService(store, grpcRequestMetadata);
    Fact fact =
        Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).serial(31).buildWithoutPayload();
    var expected = Optional.of(fact);
    when(store.fetchBySerial(31)).thenReturn(expected);
    StreamObserver<MSG_OptionalFact> stream = mock(StreamObserver.class);

    uut.fetchBySerial(new ProtoConverter().toProto(31), stream);

    verify(stream).onNext(new ProtoConverter().toProto(Optional.of(fact)));
    verify(stream).onCompleted();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void latestSerial() {
    var req = conv.empty();
    StreamObserver<MSG_Serial> obs = mock(StreamObserver.class);
    when(backend.latestSerial()).thenReturn(2L);
    // ACT
    uut.latestSerial(req, obs);

    verify(backend).latestSerial();
    verify(obs).onNext(conv.toProto(2L));
    verify(obs).onCompleted();
  }

  @Test
  void lastSerialBefore() {
    LocalDate xmas = LocalDate.of(2023, 12, 24);
    var req = conv.toProto(xmas);
    StreamObserver<MSG_Serial> obs = mock(StreamObserver.class);

    when(backend.lastSerialBefore(xmas)).thenReturn(2L);
    // ACT
    uut.lastSerialBefore(req, obs);

    verify(backend).lastSerialBefore(xmas);
    verify(obs).onNext(conv.toProto(2L));
    verify(obs).onCompleted();
  }
}
