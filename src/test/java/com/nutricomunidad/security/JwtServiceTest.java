package com.nutricomunidad.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.nutricomunidad.dto.UsuarioToken;
import com.nutricomunidad.models.Usuario;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class JwtServiceTest {

    private final String secreto = UUID.randomUUID() + "-" + UUID.randomUUID();
    private final JwtService jwt = new JwtService(secreto, 8);

    private static Usuario usuario() {
        return new Usuario(7, "Ana", "López", null, "ana@correo.test", "hash", null,
                LocalDate.now(), "Activo", 2, Roles.BENEFICIARIO);
    }

    @Test
    void generaYLeeElTokenConIdNombreYRol() {
        String token = jwt.crearToken(usuario());

        UsuarioToken leido = jwt.leerToken("Bearer " + token);

        assertThat(leido.id()).isEqualTo(7);
        assertThat(leido.nombre()).isEqualTo("Ana");
        assertThat(leido.rol()).isEqualTo(Roles.BENEFICIARIO);
    }

    @Test
    void elTokenExpiraSegunLasHorasConfiguradas() {
        Instant expira = JWT.decode(jwt.crearToken(usuario())).getExpiresAtAsInstant();

        assertThat(expira).isBetween(Instant.now().plusSeconds(8 * 3600 - 60), Instant.now().plusSeconds(8 * 3600 + 60));
    }

    @Test
    void rechazaHeaderAusenteOSinBearer() {
        assertNoAutorizado(() -> jwt.leerToken(null), "Falta el token");
        assertNoAutorizado(() -> jwt.leerToken("Basic abc"), "Falta el token");
    }

    @Test
    void rechazaTokenFirmadoConOtroSecreto() {
        String ajeno = new JwtService(UUID.randomUUID() + "-" + UUID.randomUUID(), 1).crearToken(usuario());

        assertNoAutorizado(() -> jwt.leerToken("Bearer " + ajeno), "Token inválido");
    }

    @Test
    void rechazaTokenManipulado() {
        String token = jwt.crearToken(usuario());
        String manipulado = token.substring(0, token.length() - 3) + "abc";

        assertNoAutorizado(() -> jwt.leerToken("Bearer " + manipulado), "Token inválido");
    }

    @Test
    void rechazaTokenExpirado() {
        String expirado = JWT.create()
                .withIssuer("nutricomunidad-api")
                .withSubject("7")
                .withClaim("rol", Roles.ADMIN)
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256(secreto));

        assertNoAutorizado(() -> jwt.leerToken("Bearer " + expirado), "expirado");
    }

    @Test
    void exigeUnSecretoDeAlMenos32Caracteres() {
        assertThatThrownBy(() -> new JwtService("corto", 8))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtService("", 8)).isInstanceOf(IllegalStateException.class);
    }

    private static void assertNoAutorizado(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, String mensaje) {
        assertThatThrownBy(accion)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getReason()).contains(mensaje);
                });
    }
}
