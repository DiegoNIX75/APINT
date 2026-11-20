package com.nutricomunidad.controllers;

import com.nutricomunidad.dto.EstadoRequest;
import com.nutricomunidad.dto.SolicitudRequest;
import com.nutricomunidad.models.Entrega;
import com.nutricomunidad.models.Solicitud;
import com.nutricomunidad.security.RequiereRol;
import com.nutricomunidad.security.Roles;
import com.nutricomunidad.services.EntregaService;
import com.nutricomunidad.services.SolicitudService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solicitudes")
@RequiereRol({Roles.ADMIN, Roles.ORGANIZACION, Roles.BENEFICIARIO})
public class SolicitudController {

    private final SolicitudService service;
    private final EntregaService entregas;

    public SolicitudController(SolicitudService service, EntregaService entregas) {
        this.service = service;
        this.entregas = entregas;
    }

    @GetMapping
    public List<Solicitud> listar(@RequestParam(required = false) String estado,
                                  @RequestParam(required = false) String prioridad,
                                  @RequestParam(required = false) Integer idBeneficiario,
                                  @RequestParam(required = false) Integer idOrganizacion) {
        return service.listar(estado, prioridad, idBeneficiario, idOrganizacion);
    }

    @GetMapping("/{id}")
    public Solicitud obtener(@PathVariable int id) {
        return service.obtener(id);
    }

    @GetMapping("/{id}/entregas")
    public List<Entrega> entregas(@PathVariable int id) {
        service.asegurarExiste(id);
        return entregas.listar(null, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Solicitud crear(@Valid @RequestBody SolicitudRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public Solicitud actualizar(@PathVariable int id, @Valid @RequestBody SolicitudRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public Solicitud cambiarEstado(@PathVariable int id, @Valid @RequestBody EstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiereRol(Roles.ADMIN)
    public void eliminar(@PathVariable int id) {
        service.eliminar(id);
    }
}
