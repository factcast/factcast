package org.factcast.grpc.lz4;

import io.grpc.Codec;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

// TODO autoconfig setup
@Configuration
public class Lz4GrpcCodecConfig {

  private final CompressorRegistry compressorRegistry;
  private final DecompressorRegistry decompressorRegistry;
  private final Codec lz4GrpcCodec;

  public Lz4GrpcCodecConfig(
      CompressorRegistry compressorRegistry,
      DecompressorRegistry decompressorRegistry,
      Codec lz4GrpcCodec) {
    this.compressorRegistry = compressorRegistry;
    this.decompressorRegistry = decompressorRegistry;
    this.lz4GrpcCodec = lz4GrpcCodec;
  }

  @Bean
  public Codec lz4GrpcCodec() {
      return new Lz4GrpcCodec();
  }

  @PostConstruct
  public void registerCodec() {
      compressorRegistry.register(lz4GrpcCodec);
      decompressorRegistry.with(lz4GrpcCodec, true);
  }
}
