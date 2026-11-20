package com.nutricomunidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nutricomunidad.support.PostgresEmbebido;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pruebas de extremo a extremo (controlador → servicio → repositorio → PostgreSQL embebido):
 * autenticación, autorización por rol, validaciones, manejo de errores y transacciones.
 */
@SpringBootTest
class ApiIntegrationTest {

    private static final String ADMIN_CORREO = "admin@nutricomunidad.test";
    private static final String ADMIN_CONTRASENA = "Admin-" + UUID.randomUUID();
    private static final String CONTRASENA = "Prueba123";

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", PostgresEmbebido::jdbcUrl);
        registro.add("spring.datasource.username", () -> PostgresEmbebido.USUARIO);
        registro.add("spring.datasource.password", () -> "");
        registro.add("jwt.secreto", () -> UUID.randomUUID() + "-" + UUID.randomUUID());
        registro.add("app.admin.correo", () -> ADMIN_CORREO);
        registro.add("app.admin.contrasena", () -> ADMIN_CONTRASENA);
    }

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    // ---------- Salud y autenticación ----------

    @Test
    void healthEsPublicoYVerificaLaBase() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/health/db")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(containsString("correcta")));
    }

    @Test
    void registroLoginYPerfilSinExponerLaContrasena() throws Exception {
        String correo = correoUnico();
        enviar(post("/api/auth/registro"), null, registroJson(correo, "Comercio"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreRol").value("Comercio"))
                .andExpect(jsonPath("$.estado").value("Activo"))
                .andExpect(jsonPath("$.contrasenaUsuario").doesNotExist())
                .andExpect(jsonPath("$.contrasena").doesNotExist());

        String hash = jdbc.queryForObject("SELECT contrasena_usuario FROM usuarios WHERE correo_usuario = ?",
                String.class, correo);
        assertThat(hash).startsWith("$2").isNotEqualTo(CONTRASENA);

        String token = login(correo, CONTRASENA);
        enviar(get("/api/auth/yo"), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correoUsuario").value(correo));

        enviar(put("/api/auth/yo"), token, """
                {"nombreUsuario":"Nuevo","apUsuario":"Apellido","telefonoUsuario":"5551234567"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("Nuevo"));

        enviar(put("/api/auth/yo/contrasena"), token, """
                {"contrasenaActual":"incorrecta","contrasenaNueva":"OtraClave123"}""")
                .andExpect(status().isBadRequest());
        enviar(put("/api/auth/yo/contrasena"), token, """
                {"contrasenaActual":"%s","contrasenaNueva":"OtraClave123"}""".formatted(CONTRASENA))
                .andExpect(status().isOk());
        login(correo, "OtraClave123");
    }

    @Test
    void erroresDeAutenticacion() throws Exception {
        String correo = correoUnico();
        enviar(post("/api/auth/registro"), null, registroJson(correo, null)).andExpect(status().isCreated());

        enviar(post("/api/auth/registro"), null, registroJson(correo, null))
                .andExpect(status().isConflict());
        enviar(post("/api/auth/registro"), null, registroJson(correoUnico(), "Administrador"))
                .andExpect(status().isForbidden());
        enviar(post("/api/auth/login"), null, """
                {"correo":"%s","contrasena":"incorrecta"}""".formatted(correo))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));

        mvc.perform(get("/api/alimentos")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(containsString("Falta el token")));
        enviar(get("/api/alimentos"), "token.invalido.xyz", null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Token inválido"));
    }

    @Test
    void cuentaInactivaNoPuedeIniciarSesion() throws Exception {
        String admin = login(ADMIN_CORREO, ADMIN_CONTRASENA);
        String correo = correoUnico();
        int id = idDe(enviar(post("/api/auth/registro"), null, registroJson(correo, null)).andReturn(), "$.idUsuario");

        enviar(patch("/api/usuarios/" + id + "/estado"), admin, """
                {"estado":"Inactivo"}""").andExpect(status().isOk());

        enviar(post("/api/auth/login"), null, """
                {"correo":"%s","contrasena":"%s"}""".formatted(correo, CONTRASENA))
                .andExpect(status().isForbidden());
    }

    // ---------- Autorización por rol ----------

    @Test
    void permisosPorRol() throws Exception {
        String admin = login(ADMIN_CORREO, ADMIN_CONTRASENA);
        String beneficiario = tokenNuevo("Beneficiario");
        String comercio = tokenNuevo("Comercio");

        enviar(get("/api/usuarios"), admin, null).andExpect(status().isOk());
        enviar(get("/api/usuarios"), beneficiario, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción"));
        enviar(get("/api/beneficiarios"), comercio, null).andExpect(status().isForbidden());
        enviar(get("/api/beneficiarios"), beneficiario, null).andExpect(status().isOk());
        enviar(post("/api/categorias"), comercio, """
                {"nombreCategoria":"Prohibida"}""").andExpect(status().isForbidden());
        enviar(get("/api/roles"), comercio, null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
        enviar(delete("/api/roles/1"), admin, null).andExpect(status().isConflict());
    }

    // ---------- Validaciones y errores ----------

    @Test
    void validacionesYErroresComunes() throws Exception {
        String admin = login(ADMIN_CORREO, ADMIN_CONTRASENA);

        enviar(post("/api/categorias"), admin, "{ no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("JSON")));
        enviar(post("/api/categorias"), admin, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El campo nombreCategoria es obligatorio"));
        enviar(get("/api/alimentos/999999"), admin, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Alimento con id 999999 no encontrado"));
        enviar(get("/api/alimentos/abc"), admin, null).andExpect(status().isBadRequest());
        enviar(get("/api/no-existe"), admin, null).andExpect(status().isNotFound());
        enviar(post("/api/alimentos"), admin, """
                {"nombreAlimento":"Arroz","idCategoria":999999}""").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La categoría con id 999999 no existe"));
    }

    @Test
    void integridadReferencialDevuelve409() throws Exception {
        String admin = login(ADMIN_CORREO, ADMIN_CONTRASENA);
        int categoria = crearCategoria(admin);
        crearAlimento(admin, categoria);

        enviar(delete("/api/categorias/" + categoria), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("relacionado")));
        enviar(post("/api/categorias"), admin, """
                {"nombreCategoria":"%s"}""".formatted(nombreCategoria(admin, categoria)))
                .andExpect(status().isConflict());
    }

    // ---------- Reglas de negocio y transacciones ----------

    @Test
    void donacionConDetallesCalculaTotalEnTransaccion() throws Exception {
        String admin = login(ADMIN_CORREO, ADMIN_CONTRASENA);
        String comercioToken = tokenNuevo("Comercio");
        int alimento = crearAlimento(admin, crearCategoria(admin));
        int comercio = idDe(enviar(post("/api/comercios"), comercioToken, """
                {"nombreComercio":"Tienda Prueba","codigoPostalComercio":"76000"}""")
                .andExpect(status().isCreated()).andReturn(), "$.idComercio");

        MvcResult creada = enviar(post("/api/donaciones"), comercioToken, """
                {"estadoDonacion":"Recibida","detalles":[
                  {"cantidadDetalleDonacion":10.5,"unidadMedidaDetalleDonacion":"kg","idAlimento":%d,"idComercio":%d},
                  {"cantidadDetalleDonacion":4.5,"unidadMedidaDetalleDonacion":"kg","idAlimento":%d,"idComercio":%d}]}"""
                .formatted(alimento, comercio, alimento, comercio))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadTotal").value(15.0))
                .andExpect(jsonPath("$.detalles", hasSize(2)))
                .andReturn();
        int donacion = idDe(creada, "$.idDonacion");

        enviar(post("/api/donaciones/" + donacion + "/detalles"), comercioToken, """
                {"cantidadDetalleDonacion":5,"idAlimento":%d,"idComercio":%d}""".formatted(alimento, comercio))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cantidadTotal").value(20.0));

        Integer antes = jdbc.queryForObject("SELECT COUNT(*) FROM donaciones", Integer.class);
        enviar(post("/api/donaciones"), comercioToken, """
                {"detalles":[{"cantidadDetalleDonacion":1,"idAlimento":%d,"idComercio":999999}]}""".formatted(alimento))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM donaciones", Integer.class)).isEqualTo(antes);

        enviar(get("/api/donaciones?idComercio=" + comercio), comercioToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idDonacion == " + donacion + ")]").exists());
    }

    @Test
    void flujoBeneficiarioSolicitudEntregaYAlergias() throws Exception {
        String admin = login(ADMIN_CORREO, ADMIN_CONTRASENA);
        String organizacionToken = tokenNuevo("Organizacion");
        int alimento = crearAlimento(admin, crearCategoria(admin));
        int organizacion = idDe(enviar(post("/api/organizaciones"), organizacionToken, """
                {"nombreOrganizacion":"Banco de Alimentos Prueba"}""").andExpect(status().isCreated()).andReturn(),
                "$.idOrganizacion");
        int beneficiario = idDe(enviar(post("/api/beneficiarios"), organizacionToken, """
                {"nombreBeneficiario":"Luis","ap":"Pérez","numeroIntegrantes":4,"codigoPostal":"76010"}""")
                .andExpect(status().isCreated()).andReturn(), "$.idBeneficiario");
        int otroBeneficiario = idDe(enviar(post("/api/beneficiarios"), organizacionToken, """
                {"nombreBeneficiario":"Marta","ap":"Ruiz","numeroIntegrantes":2}""").andReturn(), "$.idBeneficiario");
        int preferencia = idDe(enviar(post("/api/preferencias"), organizacionToken, """
                {"tipoPreferencia":"Alergia","idBeneficiario":%d}""".formatted(beneficiario))
                .andExpect(status().isCreated()).andReturn(), "$.idPreferencias");

        String alergia = """
                {"idPreferencias":%d,"idAlimento":%d}""".formatted(preferencia, alimento);
        enviar(post("/api/beneficiarios/" + beneficiario + "/alergias"), organizacionToken, alergia)
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(1)));
        enviar(post("/api/beneficiarios/" + beneficiario + "/alergias"), organizacionToken, alergia)
                .andExpect(status().isConflict());
        enviar(post("/api/beneficiarios/" + otroBeneficiario + "/alergias"), organizacionToken, alergia)
                .andExpect(status().isBadRequest());

        int solicitud = idDe(enviar(post("/api/solicitudes"), organizacionToken, """
                {"motivo":"Apoyo alimentario","numeroIntegrantes":4,"prioridad":"Alta","estadoSolicitud":"Pendiente",
                 "idOrganizacion":%d,"idBeneficiario":%d}""".formatted(organizacion, beneficiario))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaSolicitud").isNotEmpty())
                .andReturn(), "$.idSolicitud");
        enviar(patch("/api/solicitudes/" + solicitud + "/estado"), organizacionToken, """
                {"estado":"Aprobada"}""").andExpect(status().isOk()).andExpect(jsonPath("$.estadoSolicitud").value("Aprobada"));
        enviar(post("/api/entregas"), organizacionToken, """
                {"fechaEntrega":"2026-11-01","responsable":"Juan","estado":"Entregada","idSolicitud":%d}""".formatted(solicitud))
                .andExpect(status().isCreated());
        enviar(get("/api/solicitudes/" + solicitud + "/entregas"), organizacionToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        enviar(get("/api/beneficiarios/" + beneficiario + "/solicitudes"), organizacionToken, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void lotesValidanCantidadesYFechas() throws Exception {
        String admin = login(ADMIN_CORREO, ADMIN_CONTRASENA);
        int alimento = crearAlimento(admin, crearCategoria(admin));
        int comercio = idDe(enviar(post("/api/comercios"), admin, """
                {"nombreComercio":"Comercio Lotes"}""").andReturn(), "$.idComercio");
        int donacion = idDe(enviar(post("/api/donaciones"), admin, """
                {"detalles":[{"cantidadDetalleDonacion":8,"idAlimento":%d,"idComercio":%d}]}""".formatted(alimento, comercio))
                .andReturn(), "$.idDonacion");
        int organizacion = idDe(enviar(post("/api/organizaciones"), admin, """
                {"nombreOrganizacion":"Org Lotes"}""").andReturn(), "$.idOrganizacion");
        int inventario = idDe(enviar(post("/api/inventarios"), admin, """
                {"cantidadDisponible":8,"estadoInventario":"Disponible","idDonacion":%d,"idOrganizacion":%d}"""
                .formatted(donacion, organizacion)).andExpect(status().isCreated()).andReturn(), "$.idInventario");

        enviar(post("/api/lotes"), admin, """
                {"numeroLote":"L-1","fechaRecepcion":"2026-11-01","cantidadInicial":5,"cantidadActual":6,"idInventario":%d}"""
                .formatted(inventario)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("cantidadActual no puede ser mayor que cantidadInicial"));
        enviar(post("/api/lotes"), admin, """
                {"numeroLote":"L-1","fechaRecepcion":"2026-11-01","fechaCaducidad":"2026-10-01",
                 "cantidadInicial":5,"cantidadActual":5,"idInventario":%d}""".formatted(inventario))
                .andExpect(status().isBadRequest());
        enviar(post("/api/lotes"), admin, """
                {"numeroLote":"L-1","fechaRecepcion":"2026-11-01","fechaCaducidad":"2026-12-15",
                 "cantidadInicial":5,"cantidadActual":5,"idInventario":%d}""".formatted(inventario))
                .andExpect(status().isCreated());
        enviar(get("/api/lotes?idInventario=" + inventario + "&caducaAntesDe=2026-12-31"), admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    // ---------- Utilidades ----------

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String json) throws Exception {
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(peticion);
    }

    private static MockHttpServletRequestBuilder patch(String url) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url);
    }

    private String login(String correo, String contrasena) throws Exception {
        MvcResult resultado = enviar(post("/api/auth/login"), null, """
                {"correo":"%s","contrasena":"%s"}""".formatted(correo, contrasena))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }

    private String tokenNuevo(String rol) throws Exception {
        String correo = correoUnico();
        enviar(post("/api/auth/registro"), null, registroJson(correo, rol)).andExpect(status().isCreated());
        return login(correo, CONTRASENA);
    }

    private int crearCategoria(String token) throws Exception {
        return idDe(enviar(post("/api/categorias"), token, """
                {"nombreCategoria":"Categoria %s"}""".formatted(UUID.randomUUID()))
                .andExpect(status().isCreated()).andReturn(), "$.idCategoria");
    }

    private String nombreCategoria(String token, int id) throws Exception {
        MvcResult r = enviar(get("/api/categorias/" + id), token, null).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.nombreCategoria");
    }

    private int crearAlimento(String token, int categoria) throws Exception {
        return idDe(enviar(post("/api/alimentos"), token, """
                {"nombreAlimento":"Frijol","unidadMedida":"kg","caloriasPorPorcion":120.5,"idCategoria":%d}"""
                .formatted(categoria)).andExpect(status().isCreated()).andReturn(), "$.idAlimento");
    }

    private static int idDe(MvcResult resultado, String ruta) throws Exception {
        Integer id = JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), ruta);
        return id;
    }

    private static String correoUnico() {
        return "usuario-" + UUID.randomUUID() + "@correo.test";
    }

    private static String registroJson(String correo, String rol) {
        String campoRol = rol == null ? "" : ",\"rol\":\"" + rol + "\"";
        return """
                {"nombreUsuario":"Prueba","apUsuario":"Usuario","correoUsuario":"%s","contrasena":"%s"%s}"""
                .formatted(correo, CONTRASENA, campoRol);
    }
}
