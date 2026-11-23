package com.nutricomunidad;

import com.nutricomunidad.support.PostgresEmbebido;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Ejecuta la API contra un PostgreSQL embebido y temporal (los datos se pierden al detenerla).
 * Uso: ./gradlew bootTestRun  — útil para probar la colección de Postman sin Neon.
 * El secreto JWT se genera aleatoriamente en cada arranque. El administrador inicial se toma
 * de ADMIN_CORREO / ADMIN_PASSWORD (variables de entorno o .env), igual que en producción.
 */
public final class NutriComunidadLocalApplication {

    private NutriComunidadLocalApplication() {
    }

    public static void main(String[] args) {
        System.setProperty("spring.datasource.url", PostgresEmbebido.jdbcUrl());
        System.setProperty("spring.datasource.username", PostgresEmbebido.USUARIO);
        System.setProperty("spring.datasource.password", "");
        System.setProperty("jwt.secreto", secretoAleatorio());
        NutriComunidadApplication.main(args);
    }

    private static String secretoAleatorio() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
