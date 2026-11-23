package com.nutricomunidad.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutricomunidad.dto.LoginRequest;
import com.nutricomunidad.dto.LoginResponse;
import com.nutricomunidad.dto.RegistroRequest;
import com.nutricomunidad.models.Rol;
import com.nutricomunidad.models.Usuario;
import com.nutricomunidad.repositories.BeneficiarioRepository;
import com.nutricomunidad.repositories.ComercioRepository;
import com.nutricomunidad.repositories.OrganizacionRepository;
import com.nutricomunidad.repositories.RolRepository;
import com.nutricomunidad.repositories.UsuarioRepository;
import com.nutricomunidad.security.JwtService;
import com.nutricomunidad.security.Roles;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {

    private static final String CONTRASENA = "Secreta123";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioRepository usuarios;
    private RolRepository roles;
    private JwtService jwt;
    private AuthService service;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioRepository.class);
        roles = mock(RolRepository.class);
        jwt = mock(JwtService.class);
        service = new AuthService(usuarios, roles, jwt, encoder, mock(BeneficiarioRepository.class),
                mock(ComercioRepository.class), mock(OrganizacionRepository.class));
    }

    private static RegistroRequest registro(String rol) {
        return new RegistroRequest("Ana", "López", null, " Ana@Correo.TEST ", CONTRASENA, null, rol);
    }

    private Usuario usuario(String estado) {
        return new Usuario(1, "Ana", "López", null, "ana@correo.test", encoder.encode(CONTRASENA), null,
                LocalDate.now(), estado, 2, Roles.BENEFICIARIO);
    }

    @Test
    void registraConContrasenaHasheadaYCorreoNormalizado() {
        when(roles.buscarPorNombre(Roles.COMERCIO)).thenReturn(Optional.of(new Rol(3, Roles.COMERCIO, null, "Activo")));
        when(usuarios.crear(anyString(), anyString(), any(), anyString(), anyString(), any(), anyInt())).thenReturn(1);
        when(usuarios.buscarPorId(1)).thenReturn(Optional.of(usuario("Activo")));

        service.registrar(registro("comercio"));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(usuarios).crear(eq("Ana"), eq("López"), isNull(), eq("ana@correo.test"), hash.capture(), isNull(), eq(3));
        assertThat(hash.getValue()).isNotEqualTo(CONTRASENA).startsWith("$2");
        assertThat(encoder.matches(CONTRASENA, hash.getValue())).isTrue();
    }

    @Test
    void usaBeneficiarioComoRolPorDefecto() {
        when(roles.buscarPorNombre(Roles.BENEFICIARIO)).thenReturn(Optional.of(new Rol(2, Roles.BENEFICIARIO, null, "Activo")));
        when(usuarios.buscarPorId(anyInt())).thenReturn(Optional.of(usuario("Activo")));

        service.registrar(registro(null));

        verify(usuarios).crear(anyString(), anyString(), any(), anyString(), anyString(), any(), eq(2));
    }

    @Test
    void noPermiteRegistrarAdministradores() {
        assertEstado(() -> service.registrar(registro("administrador")), HttpStatus.FORBIDDEN);
        verify(usuarios, never()).crear(any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void rechazaRolesInexistentes() {
        assertEstado(() -> service.registrar(registro("superusuario")), HttpStatus.BAD_REQUEST);
    }

    @Test
    void rechazaCorreoDuplicado() {
        when(roles.buscarPorNombre(Roles.BENEFICIARIO)).thenReturn(Optional.of(new Rol(2, Roles.BENEFICIARIO, null, "Activo")));
        when(usuarios.existeCorreo("ana@correo.test", null)).thenReturn(true);

        assertEstado(() -> service.registrar(registro(null)), HttpStatus.CONFLICT);
    }

    @Test
    void loginCorrectoDevuelveToken() {
        when(usuarios.buscarPorCorreo("ana@correo.test")).thenReturn(Optional.of(usuario("Activo")));
        when(jwt.crearToken(any())).thenReturn("token.jwt");
        when(jwt.getHorasDeVida()).thenReturn(8L);

        LoginResponse respuesta = service.login(new LoginRequest("ANA@correo.test", CONTRASENA));

        assertThat(respuesta.token()).isEqualTo("token.jwt");
        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.usuario().correoUsuario()).isEqualTo("ana@correo.test");
    }

    @Test
    void loginConContrasenaIncorrectaOCorreoInexistenteDevuelve401() {
        when(usuarios.buscarPorCorreo("ana@correo.test")).thenReturn(Optional.of(usuario("Activo")));

        assertEstado(() -> service.login(new LoginRequest("ana@correo.test", "otra-contrasena")), HttpStatus.UNAUTHORIZED);
        assertEstado(() -> service.login(new LoginRequest("nadie@correo.test", CONTRASENA)), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginDeCuentaInactivaDevuelve403() {
        when(usuarios.buscarPorCorreo("ana@correo.test")).thenReturn(Optional.of(usuario("Inactivo")));

        assertEstado(() -> service.login(new LoginRequest("ana@correo.test", CONTRASENA)), HttpStatus.FORBIDDEN);
    }

    private static void assertEstado(org.assertj.core.api.ThrowableAssert.ThrowingCallable accion, HttpStatus estado) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(estado));
    }
}
