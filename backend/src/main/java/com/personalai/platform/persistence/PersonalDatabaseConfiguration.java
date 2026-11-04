package com.personalai.platform.persistence;

import com.personalai.platform.configuration.RuntimeDataSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** No database bean exists in demo mode. Personal startup fails rather than falling back to memory. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sai.mode", havingValue = "personal")
@EnableTransactionManagement
public class PersonalDatabaseConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource personalDataSource(RuntimeDataSettings storage,
            @Value("${sai.database.url:}") String url,
            @Value("${sai.database.username:}") String username,
            @Value("${sai.database.password-file:}") String passwordFile) {
        validateConnectionSettings(url, username, passwordFile);
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(readPassword(storage, passwordFile));
        config.setPoolName("sai-personal");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setInitializationFailTimeout(10000);
        // All application tables are isolated from public and any demo schema.
        config.setConnectionInitSql("SET search_path TO sai_personal");
        return new HikariDataSource(config);
    }

    static void validateConnectionSettings(String url, String username, String passwordFile) {
        if (url == null || !url.startsWith("jdbc:postgresql://") || username == null || username.isBlank()
                || passwordFile == null || passwordFile.isBlank()) {
            throw new IllegalStateException("Personal mode requires SAI_DB_URL (jdbc:postgresql://...), "
                    + "SAI_DB_USER and SAI_DB_PASSWORD_FILE outside the repository. No in-memory fallback is allowed.");
        }
        try {
            URI address = URI.create(url.substring("jdbc:".length()));
            if (address.getHost() == null || address.getUserInfo() != null || address.getFragment() != null
                    || address.getPath() == null || address.getPath().length() < 2) throw new IllegalArgumentException();
            // An allowlist also blocks percent-encoded property names and arbitrary JDBC factory classes.
            var allowed = Set.of("sslmode", "connecttimeout", "sockettimeout", "tcpkeepalive", "applicationname");
            if (address.getRawQuery() != null) {
                for (String property : address.getRawQuery().split("&")) {
                    String name = URLDecoder.decode(property.split("=", 2)[0], StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                    if (!allowed.contains(name)) throw new IllegalArgumentException();
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Put database credentials in the private password file and SAI_DB_USER; "
                    + "SAI_DB_URL must name a PostgreSQL host and database. Only sslmode, connectTimeout, socketTimeout, "
                    + "tcpKeepAlive and ApplicationName connection options are allowed.");
        }
    }

    private static String readPassword(RuntimeDataSettings storage, String configured) {
        Path path = storage.validatePrivatePath(Path.of(configured));
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > 4096) {
                throw new IllegalStateException("SAI_DB_PASSWORD_FILE must be a small private text file.");
            }
            String password = Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
            if (password.isBlank()) throw new IllegalStateException("SAI_DB_PASSWORD_FILE is empty.");
            return password;
        } catch (IOException exception) {
            // Do not include private paths or file contents in startup diagnostics.
            throw new IllegalStateException("Cannot read SAI_DB_PASSWORD_FILE.");
        }
    }

    @Bean
    Flyway personalMigrations(DataSource dataSource) {
        var migrations = Flyway.configure().dataSource(dataSource)
                .schemas("sai_personal").defaultSchema("sai_personal")
                .locations("classpath:db/migration").cleanDisabled(true)
                .baselineOnMigrate(false).validateMigrationNaming(true).load();
        migrations.migrate();
        return migrations;
    }

    @Bean
    JdbcTemplate personalJdbcTemplate(DataSource dataSource, Flyway personalMigrations) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    JdbcTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
