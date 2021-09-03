package org.factcast.store.registry.transformation;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
abstract class AbstractFactTransformer implements FactTransformer {

  @NonNull private final TransformationChains chains;
  @NonNull private final Transformer trans;
  @NonNull private final RegistryMetrics registryMetrics;

  protected TransformationChain getChain(Fact e, int targetVersion, TransformationKey key) {
    return chains.get(key, e.version(), targetVersion);
  }

  @NonNull
  protected Fact transform(
      Fact e, int targetVersion, TransformationKey key, TransformationChain chain) {

    try {
      JsonNode input = FactCastJson.readTree(e.jsonPayload());
      JsonNode header = FactCastJson.readTree(e.jsonHeader());
      ((ObjectNode) header).put("version", targetVersion);
      JsonNode transformedPayload = trans.transform(chain, input);

      return Fact.of(header, transformedPayload);

    } catch (JsonProcessingException e1) {
      registryMetrics.count(
          RegistryMetrics.EVENT.TRANSFORMATION_FAILED,
          Tags.of(
              Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
              Tag.of("version", String.valueOf(targetVersion))));

      throw new TransformationException(e1);
    }
  }

  protected boolean isTransformationNecessary(Fact e, int targetVersion) {
    return e.version() != targetVersion && targetVersion != 0;
  }
}
