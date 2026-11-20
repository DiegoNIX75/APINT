package com.nutricomunidad.controllers;

import com.nutricomunidad.dto.EntregaRequest;
import com.nutricomunidad.dto.EstadoRequest;
import com.nutricomunidad.models.Entrega;
import com.nutricomunidad.security.RequiereRol;
import com.nutricomunidad.security.Roles;
import com.nutricomunidad.services.EntregaService;
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
@RequestMapping("/api/entregas")
@RequiereRol({Roles.ADMIN, Roles.ORGANIZACION, Roles.BENEFICIARIO})
public class EntregaController {

    private final EntregaService service;

    public EntregaController(EntregaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Entrega> listar(@RequestParam(required = false) String estado,
                                @RequestParam(required = false) Integer idSolicitud) {
        return service.listar(estado, idSolicitud);
    }

    @GetMapping("/{id}")
    public Entrega obtener(@PathVariable int id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public Entrega crear(@Valid @RequestBody EntregaRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public Entrega actualizar(@PathVariable int id, @Valid @RequestBody EntregaRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/estado")
    @RequiereRol({Roles.ADMIN, Roles.ORGANIZACION})
    public Entrega cambiarEstado(@PathVariable int id, @Valid @RequestBody EstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiereRol(Roles.ADMIN)
    public void eliminar(@PathVariable int id) {
        service.eliminar(id);
    }
}
