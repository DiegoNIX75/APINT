package com.nutricomunidad.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ValidacionDtoTest {

    private static jakarta.validation.ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void iniciar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    private static Set<String> mensajes(Object dto) {
        return validador.validate(dto).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void registroValidoNoTieneErrores() {
        assertThat(mensajes(new RegistroRequest("Ana", "López", null, "ana@correo.test", "Secreta123", "555 123 4567", null)))
                .isEmpty();
    }

    @Test
    void registroDetectaCorreoContrasenaYTelefonoInvalidos() {
        assertThat(mensajes(new RegistroRequest("Ana", "López", null, "no-es-correo", "123", "abc", null)))
                .containsExactlyInAnyOrder(
                        "El correo no es válido",
                        "La contraseña debe tener entre 8 y 72 caracteres",
                        Validaciones.TELEFONO_MENSAJE);
    }

    @Test
    void registroExigeCamposObligatorios() {
        assertThat(mensajes(new RegistroRequest(" ", null, null, null, null, null, null)))
                .contains("El campo nombreUsuario es obligatorio", "El campo apUsuario es obligatorio",
                        "El campo correoUsuario es obligatorio", "El campo contrasena es obligatorio");
    }

    @Test
    void beneficiarioValidaCodigoPostalEIntegrantes() {
        BeneficiarioRequest r = new BeneficiarioRequest("Luis", "Pérez", null, null, null, 0,
                null, null, null, null, "123", null, null);

        assertThat(mensajes(r)).containsExactlyInAnyOrder(
                Validaciones.CODIGO_POSTAL_MENSAJE, "numeroIntegrantes debe ser mayor que cero");
    }

    @Test
    void donacionExigeDetallesYValidaCadaUno() {
        assertThat(mensajes(new DonacionRequest(null, null, null, List.of())))
                .containsExactly("La donación debe incluir al menos un detalle");

        DetalleDonacionRequest invalido = new DetalleDonacionRequest(new BigDecimal("-1"), "kg", null, 1);
        assertThat(mensajes(new DonacionRequest(null, null, null, List.of(invalido))))
                .containsExactlyInAnyOrder("cantidadDetalleDonacion debe ser mayor que cero", "El campo idAlimento es obligatorio");
    }

    @Test
    void solicitudNoAceptaFechasFuturas() {
        SolicitudRequest r = new SolicitudRequest(LocalDate.now().plusDays(1), "Apoyo", 3, null, null, 1, 1);

        assertThat(mensajes(r)).containsExactly("fechaSolicitud no puede ser una fecha futura");
    }
}
