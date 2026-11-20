package com.nutricomunidad.controllers;

import com.nutricomunidad.dto.AlergiaRequest;
import com.nutricomunidad.dto.BeneficiarioRequest;
import com.nutricomunidad.models.Alergia;
import com.nutricomunidad.models.Beneficiario;
import com.nutricomunidad.models.Preferencia;
import com.nutricomunidad.models.Solicitud;
import com.nutricomunidad.security.RequiereRol;
import com.nutricomunidad.security.Roles;
import com.nutricomunidad.services.AlergiaService;
import com.nutricomunidad.services.BeneficiarioService;
import com.nutricomunidad.services.PreferenciaService;
import com.nutricomunidad.services.SolicitudService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Beneficiarios y sus subrecursos (preferencias, alergias y solicitudes). Comercio no tiene acceso. */
@RestController
@RequestMapping("/api/beneficiarios")
@RequiereRol({Roles.ADMIN, Roles.ORGANIZACION, Roles.BENEFICIARIO})
public class BeneficiarioController {

    private final BeneficiarioService service;
    private final PreferenciaService preferencias;
    private final AlergiaService alergias;
    private final SolicitudService solicitudes;

    public BeneficiarioController(BeneficiarioService service, PreferenciaService preferencias,
                                  AlergiaService alergias, SolicitudService solicitudes) {
        this.service = service;
        this.preferencias = preferencias;
        this.alergias = alergias;
        this.solicitudes = solicitudes;
    }

    @GetMapping
    public List<Beneficiario> listar(@RequestParam(required = false) String buscar,
                                     @RequestParam(required = false) String municipio) {
        return service.listar(buscar, municipio);
    }

    @GetMapping("/{id}")
    public Beneficiario obtener(@PathVariable int id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Beneficiario crear(@Valid @RequestBody BeneficiarioRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public Beneficiario actualizar(@PathVariable int id, @Valid @RequestBody BeneficiarioRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiereRol(Roles.ADMIN)
    public void eliminar(@PathVariable int id) {
        service.eliminar(id);
    }

    @GetMapping("/{id}/preferencias")
    public List<Preferencia> preferencias(@PathVariable int id) {
        service.asegurarExiste(id);
        return preferencias.listar(id, null);
    }

    @GetMapping("/{id}/solicitudes")
    public List<Solicitud> solicitudes(@PathVariable int id) {
        service.asegurarExiste(id);
        return solicitudes.listar(null, null, id, null);
    }

    @GetMapping("/{id}/alergias")
    public List<Alergia> alergias(@PathVariable int id) {
        return alergias.listar(id);
    }

    @PostMapping("/{id}/alergias")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Alergia> agregarAlergia(@PathVariable int id, @Valid @RequestBody AlergiaRequest request) {
        return alergias.agregar(id, request);
    }

    @DeleteMapping("/{id}/alergias/{idPreferencias}/{idAlimento}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarAlergia(@PathVariable int id, @PathVariable int idPreferencias, @PathVariable int idAlimento) {
        alergias.eliminar(id, idPreferencias, idAlimento);
    }
}
