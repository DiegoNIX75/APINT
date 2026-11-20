package com.nutricomunidad.controllers;

import com.nutricomunidad.dto.OrganizacionRequest;
import com.nutricomunidad.models.Inventario;
import com.nutricomunidad.models.Organizacion;
import com.nutricomunidad.models.Solicitud;
import com.nutricomunidad.security.RequiereRol;
import com.nutricomunidad.security.Roles;
import com.nutricomunidad.services.InventarioService;
import com.nutricomunidad.services.OrganizacionService;
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

@RestController
@RequestMapping("/api/organizaciones")
public class OrganizacionController {

    private final OrganizacionService service;
    private final SolicitudService solicitudes;
    private final InventarioService inventarios;

    public OrganizacionController(OrganizacionService service, SolicitudService solicitudes,
                                  InventarioService inventarios) {
        this.service = service;
        this.solicitudes = solicitudes;
        this.inventarios = inventarios;
    }

    @GetMapping
    public List<Organizacion> listar(@RequestParam(required = false) String buscar,
                                     @RequestParam(required = false) String tipo,
                                     @RequestParam(required = false) String municipio) {
        return service.listar(buscar, tipo, municipio);
    }

    @GetMapping("/{id}")
    public Organizacion obtener(@PathVariable int id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public Organizacion crear(@Valid @RequestBody OrganizacionRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public Organizacion actualizar(@PathVariable int id, @Valid @RequestBody OrganizacionRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiereRol(Roles.ADMIN)
    public void eliminar(@PathVariable int id) {
        service.eliminar(id);
    }

    @GetMapping("/{id}/solicitudes")
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public List<Solicitud> solicitudes(@PathVariable int id) {
        service.asegurarExiste(id);
        return solicitudes.listar(null, null, null, id);
    }

    @GetMapping("/{id}/inventarios")
    public List<Inventario> inventarios(@PathVariable int id) {
        service.asegurarExiste(id);
        return inventarios.listar(id, null, null);
    }
}
