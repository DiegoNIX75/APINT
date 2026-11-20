# NutriComunidad – API REST

Backend de NutriComunidad: **Java 17 + Spring Boot 4**, PostgreSQL (Neon) con `JdbcTemplate`, autenticación **JWT** (`com.auth0:java-jwt`) y contraseñas con **BCrypt**.

## Requisitos

- JDK 17 (si no está instalado, Gradle lo descarga automáticamente mediante el plugin *foojay*).
- Base de datos PostgreSQL (Neon) con el esquema de `sql/schema.sql`.
- No hace falta instalar Gradle: se usa el wrapper (`gradlew`).

## Puesta en marcha

```bash
git clone https://github.com/DiegoNIX75/APINT.git
cd APINT
git checkout develop
cp .env.example .env        # en Windows: copy .env.example .env
# completa .env (ver tabla) y ejecuta sql/schema.sql en Neon
./gradlew bootRun           # en Windows: gradlew.bat bootRun
```

La API queda en `http://localhost:8080`. Comprueba la conexión con `GET /api/health/db`.

### Variables de entorno (`.env`)

| Variable | Descripción |
|---|---|
| `DB_URL` | URL JDBC de Neon: `jdbc:postgresql://<host>.neon.tech/<base>?sslmode=require` |
| `DB_USER` / `DB_PASSWORD` | Usuario y contraseña de Neon |
| `JWT_SECRET` | Cadena aleatoria de **al menos 32 caracteres** (la app no arranca sin ella) |
| `JWT_EXPIRACION_HORAS` | Vigencia del token (por defecto 8) |
| `CORS_ORIGENES` | Orígenes permitidos separados por coma, p. ej. `http://localhost:*,https://tu-front.vercel.app`. Vacío = `http://localhost:*` |
| `PORT` | Puerto HTTP (por defecto 8080) |
| `ADMIN_CORREO` / `ADMIN_PASSWORD` | Opcional. Crea el primer Administrador al iniciar si el correo no existe |

El archivo `.env` está en `.gitignore`; sólo se versiona `.env.example`.

### Conexión con Neon

1. En el panel de Neon copia la *connection string* (`postgresql://usuario:contraseña@host/base?sslmode=require`).
2. Conviértela a JDBC: `DB_URL=jdbc:postgresql://host/base?sslmode=require`, y coloca usuario y contraseña en `DB_USER` y `DB_PASSWORD`.
3. Ejecuta `sql/schema.sql` en el *SQL Editor* de Neon (crea las tablas y los 4 roles; es idempotente).

### Ejecutar sin Neon (PostgreSQL embebido)

```bash
./gradlew bootTestRun
```

Levanta la API con un PostgreSQL temporal (los datos se borran al detenerla) y aplica `sql/schema.sql`. Toma `ADMIN_CORREO`/`ADMIN_PASSWORD` del entorno o del `.env`.

### Pruebas

```bash
./gradlew test
```

35 pruebas: JWT, `AuthService` (Mockito), validaciones de DTO, construcción segura de SQL e integración completa contra PostgreSQL embebido (autenticación, roles, errores, transacciones).

## Estructura

```
src/main/java/com/nutricomunidad/
├── controllers/   Endpoints REST (un controlador por recurso)
├── dto/           Peticiones/respuestas con validaciones (Bean Validation)
├── errors/        ManejadorErrores (@RestControllerAdvice) y fábrica Errores
├── models/        Records que reflejan las tablas del diccionario
├── repositories/  SQL parametrizado con JdbcTemplate (BaseRepository + Filtro)
├── security/      JwtService, AuthInterceptor, @RequiereRol, Roles, WebConfig (CORS), AdminInicial
└── services/      Lógica de negocio y transacciones
sql/schema.sql     Esquema PostgreSQL + roles iniciales
postman/           Colección y entorno de Postman
```

## Seguridad

- Contraseñas con BCrypt; nunca se devuelven ni la contraseña ni el hash.
- `Authorization: Bearer <token>` en todo `/api/**` excepto `/api/auth/login`, `/api/auth/registro` y `/api/health/**`.
- Roles: **Administrador**, **Beneficiario**, **Comercio**, **Organizacion**. El registro público permite los tres últimos (por defecto Beneficiario). El Administrador inicial se crea con `ADMIN_CORREO`/`ADMIN_PASSWORD`.
- Todas las consultas usan parámetros (`?`); los filtros de búsqueda sólo concatenan nombres de columna fijos.

### Matriz de permisos

| Recurso | Consultar | Crear / editar | Eliminar |
|---|---|---|---|
| Usuarios | Admin | Admin | Admin |
| Roles | Autenticado | Admin | Admin (no los 4 del sistema) |
| Categorías, alimentos, recetas | Autenticado | Admin, Organización | Admin (ingredientes: Admin, Organización) |
| Organizaciones | Autenticado | Admin, Organización | Admin |
| Comercios | Autenticado | Admin, Comercio | Admin |
| Donaciones y sus detalles | Autenticado | Admin, Comercio | Admin (detalles: Admin, Comercio) |
| Inventarios, lotes | Autenticado | Admin, Organización | Admin |
| Beneficiarios, preferencias, alergias | Admin, Organización, Beneficiario | Admin, Organización, Beneficiario | Admin (preferencias/alergias: los tres) |
| Solicitudes | Admin, Organización, Beneficiario | Crear: los tres · Editar/estado: Admin, Organización | Admin |
| Entregas | Admin, Organización, Beneficiario | Admin, Organización | Admin |

## Errores

Todas las respuestas de error tienen la forma `{"mensaje": "..."}`.

| Código | Caso |
|---|---|
| 400 | Validación fallida, JSON mal formado, parámetro con formato inválido, referencia inexistente en el cuerpo, regla de negocio |
| 401 | Falta el token, token inválido o expirado, credenciales incorrectas |
| 403 | Rol sin permiso, cuenta inactiva, registro como Administrador |
| 404 | Registro o ruta inexistente |
| 409 | Duplicado (correo, nombre de rol/categoría), registro relacionado con otros (FK) |
| 503 / 500 | Base de datos no disponible / error inesperado (sin detalles internos) |

## Endpoints

Los listados aceptan filtros opcionales por query string. `{id}` es numérico.

### Autenticación y health (`/api/auth`, `/api/health`)
| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/health` · `/api/health/db` | – | 200 `Mensaje` (503 si la BD no responde) |
| POST | `/api/auth/registro` | `RegistroRequest` (`nombreUsuario`, `apUsuario`, `amUsuario`, `correoUsuario`, `contrasena`, `telefonoUsuario`, `rol`) | 201 `UsuarioResponse` |
| POST | `/api/auth/login` | `LoginRequest` (`correo`, `contrasena`) | 200 `{token, tipo, expiraEnHoras, usuario}` |
| GET | `/api/auth/yo` | – | 200 `UsuarioResponse` |
| PUT | `/api/auth/yo` | `PerfilRequest` | 200 `UsuarioResponse` |
| PUT | `/api/auth/yo/contrasena` | `CambioContrasenaRequest` (`contrasenaActual`, `contrasenaNueva`) | 200 `Mensaje` |

### Usuarios y roles
| Método | Ruta | Cuerpo / filtros |
|---|---|---|
| GET | `/api/usuarios` | `?estado=&idRol=&buscar=` |
| GET · PUT · DELETE | `/api/usuarios/{id}` | PUT: `UsuarioActualizacionRequest` |
| POST | `/api/usuarios` | `UsuarioRequest` (incluye `idRol`) |
| PATCH | `/api/usuarios/{id}/estado` · `/api/usuarios/{id}/rol` | `{estado}` · `{idRol}` |
| GET · POST | `/api/roles` | POST: `RolRequest` |
| GET · PUT · DELETE | `/api/roles/{id}` | PUT: `RolRequest` |

### Catálogo
| Método | Ruta | Cuerpo / filtros |
|---|---|---|
| GET · POST | `/api/categorias` | `?estado=&buscar=` · `CategoriaAlimentoRequest` |
| GET · PUT · DELETE | `/api/categorias/{id}` | |
| GET | `/api/categorias/{id}/alimentos` | |
| GET · POST | `/api/alimentos` | `?idCategoria=&estado=&buscar=` · `AlimentoRequest` |
| GET · PUT · DELETE | `/api/alimentos/{id}` | |
| GET · POST | `/api/recetas` | `?buscar=&idAlimento=` · `RecetaRequest` (con `ingredientes` opcional) |
| GET · PUT · DELETE | `/api/recetas/{id}` | GET devuelve la receta con ingredientes · PUT: `RecetaActualizacionRequest` |
| POST | `/api/recetas/{id}/ingredientes` | `DetalleRecetaRequest` |
| PUT · DELETE | `/api/recetas/{id}/ingredientes/{idDetalle}` | |

### Actores
| Método | Ruta | Cuerpo / filtros |
|---|---|---|
| GET · POST | `/api/organizaciones` | `?buscar=&tipo=&municipio=` · `OrganizacionRequest` |
| GET · PUT · DELETE | `/api/organizaciones/{id}` | |
| GET | `/api/organizaciones/{id}/solicitudes` · `/inventarios` | |
| GET · POST | `/api/comercios` | `?buscar=&tipo=&municipio=` · `ComercioRequest` |
| GET · PUT · DELETE | `/api/comercios/{id}` | |
| GET · POST | `/api/beneficiarios` | `?buscar=&municipio=` · `BeneficiarioRequest` |
| GET · PUT · DELETE | `/api/beneficiarios/{id}` | |
| GET | `/api/beneficiarios/{id}/preferencias` · `/solicitudes` | |
| GET · POST | `/api/beneficiarios/{id}/alergias` | POST: `{idPreferencias, idAlimento}` |
| DELETE | `/api/beneficiarios/{id}/alergias/{idPreferencias}/{idAlimento}` | |
| GET · POST | `/api/preferencias` | `?idBeneficiario=&tipo=` · `PreferenciaRequest` |
| GET · PUT · DELETE | `/api/preferencias/{id}` | |

### Operación
| Método | Ruta | Cuerpo / filtros |
|---|---|---|
| GET · POST | `/api/solicitudes` | `?estado=&prioridad=&idBeneficiario=&idOrganizacion=` · `SolicitudRequest` |
| GET · PUT · DELETE | `/api/solicitudes/{id}` | |
| PATCH | `/api/solicitudes/{id}/estado` | `{estado}` |
| GET | `/api/solicitudes/{id}/entregas` | |
| GET · POST | `/api/entregas` | `?estado=&idSolicitud=` · `EntregaRequest` |
| GET · PUT · DELETE | `/api/entregas/{id}` · PATCH `/api/entregas/{id}/estado` | |
| GET · POST | `/api/donaciones` | `?estado=&idComercio=` · `DonacionRequest` (con `detalles`, transaccional) |
| GET · PUT · DELETE | `/api/donaciones/{id}` | GET incluye detalles · PUT: `DonacionActualizacionRequest` |
| POST | `/api/donaciones/{id}/detalles` | `DetalleDonacionRequest` |
| PUT · DELETE | `/api/donaciones/{id}/detalles/{idDetalle}` | |
| GET · POST | `/api/inventarios` | `?idOrganizacion=&idDonacion=&estado=` · `InventarioRequest` |
| GET · PUT · DELETE | `/api/inventarios/{id}` | |
| GET | `/api/inventarios/{id}/lotes` | |
| GET · POST | `/api/lotes` | `?idInventario=&caducaAntesDe=AAAA-MM-DD` · `LoteRequest` |
| GET · PUT · DELETE | `/api/lotes/{id}` | |

Respuestas: 200 (consulta/edición), 201 (creación, devuelve el registro creado), 204 (eliminación). Los JSON usan los nombres del diccionario en camelCase (p. ej. `nombreBeneficiario`, `codigoPostalOrganizacion`); las fechas van en formato `AAAA-MM-DD`.

## Decisiones respecto al diccionario de datos

- **DETALLE_DONACION y DETALLE_RECETAS** llevan la llave foránea de su encabezado (`id_donacion`, `id_receta`), de modo que la relación es 1:N. El diccionario la tenía invertida, lo que dejaba a cada donación o receta con un único detalle.
- `cantidad_total` de la donación se calcula como la suma de sus detalles dentro de la misma transacción.
- **ALERGIAS** usa una llave primaria compuesta (preferencia, alimento, beneficiario); la preferencia debe pertenecer al beneficiario.
- El diccionario no define longitudes, obligatoriedad ni unicidad. Se tomaron como obligatorios las PK/FK y los nombres principales, y como únicos el correo de usuario y los nombres de rol y categoría.
- `fecha_actualizacion` del inventario la asigna el servidor (también al modificar sus lotes). Lotes: `cantidadActual ≤ cantidadInicial` y caducidad/consumo ≥ recepción.
- Los campos `Estado` son texto libre, salvo usuarios, roles, categorías y alimentos, que usan `Activo` por defecto.

### Pendiente o por definir

- El diccionario no relaciona `usuarios` con `beneficiarios`, `comercios` u `organizaciones`, así que un usuario no puede limitarse a "sus propios" registros: los permisos son por rol. Si se agrega esa relación (p. ej. `id_usuario` en esas tablas), se podrá restringir por propietario.
- Valores permitidos para estados y prioridades (catálogo cerrado).
- Revisión contra el diseño de Figma (siguiente etapa).

## Postman

1. En Postman: *Import* → `postman/NutriComunidad.postman_collection.json` y `postman/NutriComunidad.postman_environment.json`.
2. Selecciona el entorno **NutriComunidad Local** y define `adminCorreo` y `adminPassword` con los valores de `ADMIN_CORREO` y `ADMIN_PASSWORD` del backend.
3. Ejecuta la colección completa con *Run collection*. El login guarda el JWT en `{{token}}` y los IDs creados en variables. La carpeta *Limpieza* borra los datos de prueba.

También desde terminal: `npx newman run postman/NutriComunidad.postman_collection.json -e postman/NutriComunidad.postman_environment.json --env-var adminCorreo=... --env-var adminPassword=...`
