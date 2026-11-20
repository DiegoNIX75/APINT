package com.nutricomunidad.services;

import com.nutricomunidad.dto.DetalleDonacionRequest;
import com.nutricomunidad.dto.DonacionActualizacionRequest;
import com.nutricomunidad.dto.DonacionRequest;
import com.nutricomunidad.dto.DonacionResponse;
import com.nutricomunidad.errors.Errores;
import com.nutricomunidad.models.DetalleDonacion;
import com.nutricomunidad.models.Donacion;
import com.nutricomunidad.repositories.AlimentoRepository;
import com.nutricomunidad.repositories.ComercioRepository;
import com.nutricomunidad.repositories.DetalleDonacionRepository;
import com.nutricomunidad.repositories.DonacionRepository;
import com.nutricomunidad.repositories.Filtro;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Donaciones y sus detalles. Toda operación que modifica detalles recalcula cantidad_total
 * dentro de la misma transacción.
 */
@Service
public class DonacionService {

    private final DonacionRepository donaciones;
    private final DetalleDonacionRepository detalles;
    private final AlimentoRepository alimentos;
    private final ComercioRepository comercios;

    public DonacionService(DonacionRepository donaciones, DetalleDonacionRepository detalles,
                           AlimentoRepository alimentos, ComercioRepository comercios) {
        this.donaciones = donaciones;
        this.detalles = detalles;
        this.alimentos = alimentos;
        this.comercios = comercios;
    }

    public List<Donacion> listar(String estado, Integer idComercio) {
        return donaciones.listar(new Filtro()
                .igualSinMayusculas("estado_donacion", estado)
                .condicion("""
                        EXISTS (SELECT 1 FROM detalle_donacion dd
                                WHERE dd.id_donacion = donaciones.id_donacion AND dd.id_comercio = ?)
                        """, idComercio));
    }

    public DonacionResponse obtener(int id) {
        Donacion donacion = donaciones.buscarPorId(id).orElseThrow(() -> Errores.noEncontrado("Donación", id));
        return DonacionResponse.de(donacion, detalles.listarPorDonacion(id));
    }

    @Transactional
    public DonacionResponse crear(DonacionRequest r) {
        r.detalles().forEach(this::validarDetalle);
        LocalDate fecha = r.fechaDonacion() == null ? LocalDate.now() : r.fechaDonacion();
        int id = donaciones.crear(fecha, r.estadoDonacion(), r.observacionesDonacion());
        r.detalles().forEach(detalle -> detalles.crear(id, detalle));
        donaciones.recalcularTotal(id);
        return obtener(id);
    }

    public DonacionResponse actualizar(int id, DonacionActualizacionRequest r) {
        asegurarExiste(id);
        donaciones.actualizar(id, r.fechaDonacion(), r.estadoDonacion(), r.observacionesDonacion());
        return obtener(id);
    }

    public void eliminar(int id) {
        if (!donaciones.eliminar(id)) {
            throw Errores.noEncontrado("Donación", id);
        }
    }

    @Transactional
    public DonacionResponse agregarDetalle(int idDonacion, DetalleDonacionRequest r) {
        asegurarExiste(idDonacion);
        validarDetalle(r);
        detalles.crear(idDonacion, r);
        donaciones.recalcularTotal(idDonacion);
        return obtener(idDonacion);
    }

    @Transactional
    public DonacionResponse actualizarDetalle(int idDonacion, int idDetalle, DetalleDonacionRequest r) {
        obtenerDetalleDe(idDonacion, idDetalle);
        validarDetalle(r);
        detalles.actualizar(idDetalle, r);
        donaciones.recalcularTotal(idDonacion);
        return obtener(idDonacion);
    }

    @Transactional
    public void eliminarDetalle(int idDonacion, int idDetalle) {
        obtenerDetalleDe(idDonacion, idDetalle);
        detalles.eliminar(idDetalle);
        donaciones.recalcularTotal(idDonacion);
    }

    private DetalleDonacion obtenerDetalleDe(int idDonacion, int idDetalle) {
        asegurarExiste(idDonacion);
        return detalles.buscarPorId(idDetalle)
                .filter(detalle -> detalle.idDonacion() == idDonacion)
                .orElseThrow(() -> Errores.noEncontrado("Detalle de la donación " + idDonacion, idDetalle));
    }

    private void validarDetalle(DetalleDonacionRequest r) {
        Errores.validarReferencia(alimentos.existe(r.idAlimento()), "El alimento", r.idAlimento());
        Errores.validarReferencia(comercios.existe(r.idComercio()), "El comercio", r.idComercio());
    }

    private void asegurarExiste(int id) {
        if (!donaciones.existe(id)) {
            throw Errores.noEncontrado("Donación", id);
        }
    }
}
