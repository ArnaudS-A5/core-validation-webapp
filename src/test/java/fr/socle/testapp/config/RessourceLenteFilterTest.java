package fr.socle.testapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Filtre des ressources lentes : chemins concernés et lecture du délai. */
class RessourceLenteFilterTest {

    private final RessourceLenteFilter filtre = new RessourceLenteFilter();

    @Test
    void seuls_les_dossiers_lent_des_fronts_sont_retardes() {
        assertThat(filtre.shouldNotFilter(new MockHttpServletRequest("GET", "/classic/lent/latence-tardif.js"))).isFalse();
        assertThat(filtre.shouldNotFilter(new MockHttpServletRequest("GET", "/classic/js/latence.js"))).isTrue();
        assertThat(filtre.shouldNotFilter(new MockHttpServletRequest("GET", "/api/lent/x"))).isTrue();
    }

    @Test
    void delai_lu_dans_la_requete() {
        assertThat(RessourceLenteFilter.delai(null)).isEqualTo(RessourceLenteFilter.DELAI_PAR_DEFAUT_MS);
        assertThat(RessourceLenteFilter.delai(" ")).isEqualTo(RessourceLenteFilter.DELAI_PAR_DEFAUT_MS);
        assertThat(RessourceLenteFilter.delai("abc")).isEqualTo(RessourceLenteFilter.DELAI_PAR_DEFAUT_MS);
        assertThat(RessourceLenteFilter.delai(" 1200 ")).isEqualTo(1200);
    }
}
