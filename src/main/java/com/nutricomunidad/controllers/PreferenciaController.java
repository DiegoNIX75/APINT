package com.nutricomunidad.controllers;

import com.nutricomunidad.dto.PreferenciaRequest;
import com.nutricomunidad.models.Preferencia;
import com.nutricomunidad.security.RequiereRol;
import com.nutricomunidad.security.Roles;
import com.nutricomunidad.services.PreferenciaService;
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
@RequestMapping("/api/preferencias")
@RequiereRol({Roles.ADMIN, Roles.ORGANIZACION, Roles.BENEFICIARIO})
public class PreferenciaController {

    private final PreferenciaService service;

    public PreferenciaController(PreferenciaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Preferencia> listar(@RequestParam(required = false) Integer idBeneficiario,
                                    @RequestParam(required = false) String tipo) {
        return service.listar(idBeneficiario, tipo);
    }

    @GetMapping("/{id}")
    public Preferencia obtener(@PathVariable int id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Preferencia crear(@Valid @RequestBody PreferenciaRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    public Preferencia actualizar(@PathVariable int id, @Valid @RequestBody PreferenciaRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable int id) {
        service.eliminar(id);
    }
}
