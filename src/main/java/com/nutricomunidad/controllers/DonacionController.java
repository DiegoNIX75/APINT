package com.nutricomunidad.controllers;

import com.nutricomunidad.dto.DetalleDonacionRequest;
import com.nutricomunidad.dto.DonacionActualizacionRequest;
import com.nutricomunidad.dto.DonacionRequest;
import com.nutricomunidad.dto.DonacionResponse;
import com.nutricomunidad.models.Donacion;
import com.nutricomunidad.security.RequiereRol;
import com.nutricomunidad.security.Roles;
import com.nutricomunidad.services.DonacionService;
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
@RequestMapping("/api/donaciones")
public class DonacionController {

    private final DonacionService service;

    public DonacionController(DonacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<Donacion> listar(@RequestParam(required = false) String estado,
                                 @RequestParam(required = false) Integer idComercio) {
        return service.listar(estado, idComercio);
    }

    @GetMapping("/{id}")
    public DonacionResponse obtener(@PathVariable int id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({Roles.ADMIN, Roles.COMERCIO})
    public DonacionResponse crear(@Valid @RequestBody DonacionRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @RequiereRol({Roles.ADMIN, Roles.COMERCIO})
    public DonacionResponse actualizar(@PathVariable int id, @Valid @RequestBody DonacionActualizacionRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiereRol(Roles.ADMIN)
    public void eliminar(@PathVariable int id) {
        service.eliminar(id);
    }

    @PostMapping("/{id}/detalles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiereRol({Roles.ADMIN, Roles.COMERCIO})
    public DonacionResponse agregarDetalle(@PathVariable int id, @Valid @RequestBody DetalleDonacionRequest request) {
        return service.agregarDetalle(id, request);
    }

    @PutMapping("/{id}/detalles/{idDetalle}")
    @RequiereRol({Roles.ADMIN, Roles.COMERCIO})
    public DonacionResponse actualizarDetalle(@PathVariable int id, @PathVariable int idDetalle,
                                              @Valid @RequestBody DetalleDonacionRequest request) {
        return service.actualizarDetalle(id, idDetalle, request);
    }

    @DeleteMapping("/{id}/detalles/{idDetalle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiereRol({Roles.ADMIN, Roles.COMERCIO})
    public void eliminarDetalle(@PathVariable int id, @PathVariable int idDetalle) {
        service.eliminarDetalle(id, idDetalle);
    }
}
