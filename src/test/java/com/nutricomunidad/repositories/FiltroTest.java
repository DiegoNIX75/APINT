package com.nutricomunidad.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FiltroTest {

    @Test
    void sinValoresNoGeneraWhere() {
        Filtro filtro = new Filtro().igual("id", null).contiene("  ", "nombre").igualSinMayusculas("estado", "");

        assertThat(filtro.where()).isEmpty();
        assertThat(filtro.parametros()).isEmpty();
    }

    @Test
    void combinaCondicionesConParametros() {
        Filtro filtro = new Filtro().igual("id_categoria", 3).igualSinMayusculas("estado", " Activo ");

        assertThat(filtro.where()).isEqualTo(" WHERE id_categoria = ? AND LOWER(estado) = LOWER(?)");
        assertThat(filtro.parametros()).containsExactly(3, "Activo");
    }

    @Test
    void contieneBuscaEnVariasColumnasYEscapaComodines() {
        Filtro filtro = new Filtro().contiene("50%_x", "a", "b");

        assertThat(filtro.where()).isEqualTo(" WHERE (a ILIKE ? OR b ILIKE ?)");
        assertThat(filtro.parametros()).containsExactly("%50\\%\\_x%", "%50\\%\\_x%");
    }

    @Test
    void elTextoDelUsuarioNuncaFormaParteDelSql() {
        String ataque = "'; DROP TABLE usuarios; --";
        Filtro filtro = new Filtro().igualSinMayusculas("correo", ataque).contiene(ataque, "nombre");

        assertThat(filtro.where()).doesNotContain("DROP");
        assertThat(filtro.parametros()).hasSize(2);
    }
}
