package org.factcast.example.smilepoc;

import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.bench.AggIdFilterBenchmark;
import org.factcast.example.smilepoc.bench.BulkReadBenchmark;
import org.factcast.example.smilepoc.bench.InsertBenchmark;
import org.factcast.example.smilepoc.bench.InvalidationBenchmark;
import org.factcast.example.smilepoc.bench.ParseBenchmark;
import org.factcast.example.smilepoc.bench.SingleReadBenchmark;
import org.factcast.example.smilepoc.bench.SizeBenchmark;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.factcast.example.smilepoc.schema.SchemaInitializer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(PocProperties.class)
public class SmilePocApplication {

  public static void main(String[] args) {
    SpringApplication.run(SmilePocApplication.class, args);
  }

  @Bean
  ApplicationRunner runner(
      PocProperties props,
      SchemaInitializer schema,
      InsertBenchmark insert,
      SizeBenchmark size,
      SingleReadBenchmark singleRead,
      BulkReadBenchmark bulkRead,
      ParseBenchmark parse,
      AggIdFilterBenchmark filter,
      InvalidationBenchmark invalidation,
      ReportWriter report) {
    return args -> {
      try {
        if (props.reset()) {
          log.info("Resetting schema");
          schema.reset();
        } else {
          log.info("Reset disabled (poc.reset=false), keeping existing tables");
          schema.ensureExists();
        }

        if (!props.skipLoad()) {
          log.info("Loading data from {}", props.csv().path());
          insert.run();
        } else {
          log.info("Skipping load (poc.skip-load=true)");
        }

        size.run();
        singleRead.run();
        bulkRead.run();
        parse.run();
        filter.run();
        invalidation.run();

        report.flushConsole();
        report.writeCsv();
      } catch (Exception e) {
        log.error("PoC failed", e);
        throw e;
      }
    };
  }
}
