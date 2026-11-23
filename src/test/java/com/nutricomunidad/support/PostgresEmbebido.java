package com.nutricomunidad.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * PostgreSQL embebido (binario real) compartido por las pruebas y por la ejecución local.
 * Al iniciar aplica sql/schema.sql, el mismo script que se usa en Neon.
 */
public final class PostgresEmbebido {

    public static final String USUARIO = "postgres";

    private static EmbeddedPostgres postgres;

    private PostgresEmbebido() {
    }

    public static synchronized EmbeddedPostgres iniciar() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
                try (Connection conexion = postgres.getPostgresDatabase().getConnection()) {
                    ScriptUtils.executeSqlScript(conexion,
                            new EncodedResource(new FileSystemResource("sql/schema.sql"), StandardCharsets.UTF_8));
                }
                Runtime.getRuntime().addShutdownHook(new Thread(PostgresEmbebido::detener));
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo iniciar PostgreSQL embebido", e);
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo aplicar sql/schema.sql", e);
            }
        }
        return postgres;
    }

    public static String jdbcUrl() {
        return iniciar().getJdbcUrl(USUARIO, "postgres");
    }

    private static void detener() {
        try {
            postgres.close();
        } catch (IOException ignored) {
            // El proceso termina de todas formas.
        }
    }
}
