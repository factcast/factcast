package org.factcast.grpc.snappy;

import io.grpc.Codec;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

// TODO autoconfig setup
@Configuration
public class SnappyGrpcCodecConfig {

  private final CompressorRegistry compressorRegistry;
  private final DecompressorRegistry decompressorRegistry;
  private final Codec snappyGrpcCodec;

  public SnappyGrpcCodecConfig(
      CompressorRegistry compressorRegistry,
      DecompressorRegistry decompressorRegistry,
      Codec snappyGrpcCodec) {
    this.compressorRegistry = compressorRegistry;
    this.decompressorRegistry = decompressorRegistry;
    this.snappyGrpcCodec = snappyGrpcCodec;
  }

  @Bean
  public Codec snappyGrpcCodec() {
      return new SnappycGrpcCodec();
  }

  @PostConstruct
  public void registerCodec() {
      compressorRegistry.register(snappyGrpcCodec);
      decompressorRegistry.with(snappyGrpcCodec, true);
  }
}
