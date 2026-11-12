package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@DisplayName("Tableaux")
class A05TableauxIT extends SmokeTestBase {

    private static final String FIRST_NAME = "return (document.querySelector('#items-body tr td:nth-child(3)') || {}).textContent";

    @Test
    @Order(1)
    void chargement_initial() {
        open("tables.html");
        assertThat(waitUntil(drv -> exists("#row-1"), 6)).isTrue();
        assertThat($$("#items-body tr")).as("10 lignes").hasSize(10);
        assertThat(waitText("#table-status", "57 résultat")).as("statut 57 résultats").isTrue();
    }

    @Test
    @Order(2)
    void tri_par_nom_decroissant() {
        click("#sort-name");
        pause(600);
        click("#sort-name");
        assertThat(waitUntil(drv -> "descending".equals(attr("#sort-name", "aria-sort"))
                && "Article 057".equals(js(FIRST_NAME)), 5)).as("tri par nom décroissant").isTrue();
    }

    @Test
    @Order(3)
    void pagination() {
        click("#page-next");
        assertThat(waitUntil(drv -> "Article 047".equals(js(FIRST_NAME)), 5)).as("page suivante").isTrue();
        click("#page-link-6");
        assertThat(waitUntil(drv -> $$("#items-body tr").size() == 7, 5)).as("dernière page = 7 lignes").isTrue();
    }

    @Test
    @Order(4)
    void recherche() {
        type("#table-search", "Jardin");
        click("#btn-search");
        assertThat(waitText("#table-status", "11 résultat")).as("recherche « Jardin » = 11").isTrue();
    }

    @Test
    @Order(5)
    void taille_de_page() {
        click("#page-link-1");
        assertThat(waitUntil(drv -> Boolean.FALSE.equals(
                js("return document.getElementById('items-body').classList.contains('loading')")), 5)).isTrue();
        select("#page-size", "5");
        assertThat(waitUntil(drv -> $$("#items-body tr").size() == 5, 5)).as("taille de page 5").isTrue();
    }

    @Test
    @Order(6)
    void selection_et_suppression_groupee() {
        List<String> ids = $$("#items-body tr").stream().map(e -> e.getDomAttribute("id")).toList();
        click("#" + ids.get(0).replace("row-", "row-check-"));
        click("#" + ids.get(1).replace("row-", "row-check-"));
        assertThat(text("#selection-count")).as("compteur de sélection").isEqualTo("2 sélectionné(s)");
        click("#btn-delete-selected");
        assertThat(waitVisible("#confirm-modal", 2)).as("modale de confirmation").isTrue();
        click("#confirm-no");
        assertThat(waitUntil(drv -> !visible("#confirm-modal"), 2)).as("annulation").isTrue();
        click("#btn-delete-selected");
        click("#confirm-yes");
        assertThat(waitText("#table-message", "2 article\\(s\\) supprimé")).as("suppression groupée").isTrue();
    }

    @Test
    @Order(7)
    void edition_en_ligne() {
        assertThat(waitText("#table-status", "9 résultat")).isTrue();
        String id = $("#items-body tr").getDomAttribute("id").replace("row-", "");
        click("#edit-" + id);
        clearAndType("#edit-input-" + id, "Jardin édité");
        click("#save-" + id);
        assertThat(waitText("#table-message", "modifié")).as("édition en ligne").isTrue();
        assertThat(waitText("#name-" + id, "^Jardin édité$", 4)).as("nom modifié affiché").isTrue();
    }

    @Test
    @Order(8)
    void ajout() {
        type("#add-name", "Nouvel article");
        select("#add-category", "Sport");
        type("#add-price", "12.5");
        click("#add-submit");
        assertThat(waitText("#table-message", "Nouvel article.*ajouté")).as("ajout").isTrue();
        $("#add-name").clear();
        click("#add-submit");
        assertThat(waitText("#add-error", "obligatoire")).as("ajout sans nom refusé").isTrue();
    }
}
