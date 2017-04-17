package org.factcast.store.pgsql.internal;

import java.io.IOException;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.impossibl.postgres.jdbc.PGDataSource;
import com.impossibl.postgres.jdbc.PGDriver;

import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig;

@Configuration
@Import(PGFactStoreInternalConfiguration.class)
@ImportAutoConfiguration({ DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
		TransactionAutoConfiguration.class })
public class PGEmbeddedConfiguration {

	private static PGDataSource ds;

	static {
		try {
			PostgresConfig pgConfig = PostgresConfig.defaultWithDbName("embedded", "test", "test");
			PostgresStarter<PostgresExecutable, PostgresProcess> runtime = PostgresStarter.getDefaultInstance();
			PostgresExecutable exec = runtime.prepare(pgConfig);
			PostgresProcess process = exec.start();

			ds = new PGDataSource();
			ds.setUser(pgConfig.credentials().username());
			ds.setPassword(pgConfig.credentials().password());
			ds.setPort(pgConfig.net().port());
			ds.setHost(pgConfig.net().host());
			ds.setDatabase(pgConfig.storage().dbName());

			String host = pgConfig.net().host();
			int port = pgConfig.net().port();
			String dbName = pgConfig.storage().dbName();
			String username = pgConfig.credentials().username();
			String password2 = pgConfig.credentials().password();
			String url = String.format("jdbc:pgsql://%s:%s/%s?currentSchema=public&user=%s&password=%s", host, port,
					dbName, username, password2);
			//

			System.setProperty("spring.datasource.url", url);
			System.setProperty("spring.datasource.driverClassName", PGDriver.class.getCanonicalName());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Bean
	public DataSource dataSource() {
		return ds;
	}
}
//
// volatile static EmbeddedPGDataSource embeddedPg = null;
// static {
// if (embeddedPg == null) {
// embeddedPg = new EmbeddedPGDataSource();
// embeddedPg.start();
// }
//
// System.setProperty("spring.datasource.url", embeddedPg.getUrl());
// System.setProperty("spring.datasource.driverClassName",
// PGDriver.class.getCanonicalName());
//
// }
//
// static class EmbeddedPGDataSource extends DriverManagerDataSource {
//
// public void start() {
// try {
//
//
// final PostgresStarter<PostgresExecutable, PostgresProcess> runtime =
// PostgresStarter
// .getDefaultInstance();
//
// pgConfig.getAdditionalInitDbParams().addAll(Arrays.asList("-E", "UTF-8",
// "--locale=en_US.UTF-8",
// "--lc-collate=en_US.UTF-8", "--lc-ctype=en_US.UTF-8"));
// PostgresExecutable exec = runtime.prepare(pgConfig);
// exec.start();
//
// // connecting to a running Postgres
// String host = pgConfig.net().host();
// int port = pgConfig.net().port();
// String dbName = pgConfig.storage().dbName();
// String username = pgConfig.credentials().username();
// String password2 = pgConfig.credentials().password();
// String url =
// String.format("jdbc:pgsql://%s:%s/%s?currentSchema=public&user=%s&password=%s",
// host, port,
// dbName, username, password2);
//
// setUrl(url);
// } catch (IOException e) {
// // TODO Auto-generated catch block
// e.printStackTrace();
// }
// }
// }
