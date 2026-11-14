package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import fr.socle.testapp.service.ProfilReseau;
import fr.socle.testapp.service.Reseau;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.Dimension;

/**
 * Panneau des tirages du réseau simulé, affiché sous l'en-tête de toutes les pages : profil, graine et
 * 5 derniers tirages (« n/a » sans profil). Il reste à l'écran quand le contenu de la page défile.
 */
@DisplayName("Panneau des tirages")
class A13PanneauTiragesIT extends SmokeTestBase {

    private static final int LIGNES = 5;

    @ParameterizedTest(name = "{0}")
    @Order(1)
    @ValueSource(strings = {"index.html", "login.html", "clicks.html", "forms.html", "dynamic.html", "tables.html",
            "windows.html", "frames.html", "files.html", "widgets.html", "gestes.html", "synchro.html", "calque.html"})
    void panneau_sans_profil_sur_chaque_page(String page) {
        open(page);
        assertThat(visible("#panneau-tirages")).as("panneau affiché").isTrue();
        assertThat(waitText("#tirage-profil", "^aucun$")).as("profil lu sur le serveur").isTrue();
        assertThat(text("#tirage-graine")).isEqualTo("n/a");
        assertThat(text("#tirages-effectues")).isEqualTo("n/a");
        assertThat($$("#tirages-body tr")).as("toujours 5 lignes").hasSize(LIGNES);
        for (int i = 0; i < LIGNES; i++) {
            assertThat(text("#tirage-numero-" + i)).as("ligne " + i).isEqualTo("n/a");
            assertThat(text("#tirage-latence-" + i)).as("ligne " + i).isEqualTo("n/a");
        }
    }

    @Test
    @Order(2)
    void panneau_reste_a_l_ecran_quand_la_page_defile() {
        Dimension taille = d.manage().window().getSize();
        try {
            // Fenêtre réduite : le contenu de la page dépasse forcément la zone visible
            d.manage().window().setSize(new Dimension(1000, 600));
            open("widgets.html");
            js("var s = document.querySelectorAll('main section.card'); s[s.length - 1].scrollIntoView({ block: 'end' });");
            assertThat(((Number) js("return document.querySelector('main').scrollTop")).intValue())
                    .as("le contenu a défilé").isPositive();
            assertThat(js("var r = document.getElementById('panneau-tirages').getBoundingClientRect();"
                    + "return r.top >= 0 && r.bottom <= window.innerHeight;"))
                    .as("panneau entièrement à l'écran").isEqualTo(true);
            assertThat(js("var r = document.getElementById('panneau-tirages').getBoundingClientRect();"
                    + "var e = document.elementFromPoint(r.left + 5, r.top + r.height / 2);"
                    + "return !!e && !!e.closest('#panneau-tirages');"))
                    .as("rien ne passe par-dessus le panneau").isEqualTo(true);
        } finally {
            d.manage().window().setSize(taille);
        }
    }

    @Test
    @Order(3)
    void tirage_de_la_connexion_affiche_et_conforme_a_la_graine() throws Exception {
        try {
            appliquerProfil("lent", 5);
            open("login.html");
            assertThat(waitText("#tirage-profil", "^lent$")).isTrue();
            assertThat(text("#tirage-graine")).isEqualTo("5");
            type("#login-username", "test");
            type("#login-password", "test");
            click("#login-submit");
            assertThat(waitUntil(drv -> exists("#welcome-panel:not(.hidden)"), 15)).as("connexion aboutie").isTrue();
            assertThat(waitText("#tirage-chemin-0", "^/api/login$", 10)).as("tirage de la connexion affiché").isTrue();
            long numero = Long.parseLong(text("#tirage-numero-0"));
            Reseau reseau = new Reseau();
            reseau.configurer(ProfilReseau.LENT, 5L);
            Reseau.Tirage attendu = reseau.calculer(numero);
            assertThat(attr("#tirage-ligne-0", "data-latence")).as("latence affichée = latence de la graine")
                    .isEqualTo(String.valueOf(attendu.latenceMs()));
            assertThat(text("#tirage-latence-0")).isEqualTo(attendu.latenceMs() + " ms");
            assertThat(text("#tirage-incident-0")).isEqualTo("aucun");
        } finally {
            resetApp();
        }
    }
}
