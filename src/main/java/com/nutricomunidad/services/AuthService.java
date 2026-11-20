package com.nutricomunidad.services;

import com.nutricomunidad.dto.Textos;
import com.nutricomunidad.dto.CambioContrasenaRequest;
import com.nutricomunidad.dto.LoginRequest;
import com.nutricomunidad.dto.LoginResponse;
import com.nutricomunidad.dto.PerfilRequest;
import com.nutricomunidad.dto.RegistroRequest;
import com.nutricomunidad.dto.UsuarioResponse;
import com.nutricomunidad.errors.Errores;
import com.nutricomunidad.models.Rol;
import com.nutricomunidad.models.Usuario;
import com.nutricomunidad.repositories.RolRepository;
import com.nutricomunidad.repositories.UsuarioRepository;
import com.nutricomunidad.security.JwtService;
import com.nutricomunidad.security.Roles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos";

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final JwtService jwt;
    private final BCryptPasswordEncoder encoder;
    /** Hash ficticio para que el tiempo de respuesta no revele si un correo existe. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, RolRepository roles, JwtService jwt, BCryptPasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.jwt = jwt;
        this.encoder = encoder;
        this.hashFicticio = encoder.encode("hash-ficticio-para-tiempo-constante");
    }

    public UsuarioResponse registrar(RegistroRequest request) {
        String rolPedido = Textos.oPorDefecto(request.rol(), Roles.POR_DEFECTO);
        if (Roles.ADMIN.equalsIgnoreCase(rolPedido)) {
            throw Errores.prohibido("No se puede registrar una cuenta de Administrador");
        }
        String nombreRol = Roles.registrable(rolPedido);
        if (nombreRol == null) {
            throw Errores.peticionInvalida("El rol " + rolPedido + " no es válido. Opciones: " + Roles.REGISTRABLES);
        }
        Rol rol = roles.buscarPorNombre(nombreRol)
                .orElseThrow(() -> Errores.peticionInvalida("El rol " + nombreRol + " no está configurado en la base de datos"));

        String correo = Textos.correo(request.correoUsuario());
        if (usuarios.existeCorreo(correo, null)) {
            throw Errores.conflicto("Ya existe una cuenta con ese correo");
        }

        int id = usuarios.crear(request.nombreUsuario().trim(), request.apUsuario().trim(),
                Textos.limpiar(request.amUsuario()), correo, encoder.encode(request.contrasena()),
                Textos.limpiar(request.telefonoUsuario()), rol.idRol());
        return yo(id);
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarios.buscarPorCorreo(Textos.correo(request.correo())).orElse(null);
        String hash = usuario == null ? hashFicticio : usuario.contrasenaUsuario();
        boolean coincide = encoder.matches(request.contrasena(), hash);

        if (usuario == null || !coincide) {
            throw Errores.noAutorizado(CREDENCIALES_INCORRECTAS);
        }
        if (!usuario.estaActivo()) {
            throw Errores.prohibido("La cuenta está inactiva, contacta a un administrador");
        }
        return new LoginResponse(jwt.crearToken(usuario), "Bearer", jwt.getHorasDeVida(), UsuarioResponse.de(usuario));
    }

    public UsuarioResponse yo(int id) {
        return usuarios.buscarPorId(id)
                .map(UsuarioResponse::de)
                .orElseThrow(() -> Errores.noAutorizado("La cuenta del token ya no existe"));
    }

    public UsuarioResponse actualizarPerfil(int id, PerfilRequest request) {
        Usuario actual = usuarios.buscarPorId(id)
                .orElseThrow(() -> Errores.noAutorizado("La cuenta del token ya no existe"));
        usuarios.actualizarDatos(id, request.nombreUsuario().trim(), request.apUsuario().trim(),
                Textos.limpiar(request.amUsuario()), actual.correoUsuario(), Textos.limpiar(request.telefonoUsuario()));
        return yo(id);
    }

    public void cambiarContrasena(int id, CambioContrasenaRequest request) {
        Usuario actual = usuarios.buscarPorId(id)
                .orElseThrow(() -> Errores.noAutorizado("La cuenta del token ya no existe"));
        if (!encoder.matches(request.contrasenaActual(), actual.contrasenaUsuario())) {
            throw Errores.peticionInvalida("La contraseña actual es incorrecta");
        }
        usuarios.actualizarContrasena(id, encoder.encode(request.contrasenaNueva()));
    }
}
