package fr.socle.testapp.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import fr.socle.testapp.service.ProfilReseau;
import fr.socle.testapp.service.Reseau;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openqa.selenium.WebElement;

/**
 * Profils réseau : chaque appel reçoit la latence et l'incident que la graine et le numéro de tirage imposent.
 * Le test lit le tirage dans le panneau de la page, le recalcule avec {@link Reseau} et vérifie que l'appel
 * a duré au moins cette latence (plus la latence de base de l'espace quand l'appel atteint l'API ; une erreur 503
 * ou une coupure surviennent avant). Pas de maximum : le délai d'attente du test en tient lieu.
 * Le chronomètre démarre avant le geste et le test guette aussitôt le résultat.
 */
@DisplayName("Profils réseau")
class L04ProfilsReseauLT extends LatenceTestBase {

    private static final long GRAINE_LENT = 5;
    /** Graine des appels uniques : le numéro de tirage est cherché pour obtenir chaque cas. */
    private static final long GRAINE_CAS = 42;
    /** Latence maximale retenue pour les appels uniques et la rafale (durée du test maîtrisée). */
    private static final int LATENCE_MAX_RETENUE_MS = 9_000;

    /**
     * Graine dont les tirages 1 à 10 du profil catastrophique contiennent au moins une erreur 503 et une
     * connexion coupée, avec des latences raisonnables pour garder un test de durée maîtrisée.
     */
    private static final long GRAINE_CATASTROPHE = grainePour(ProfilReseau.CATASTROPHIQUE);

    private static long grainePour(ProfilReseau profil) {
        for (long graine = 1; graine < 1_000_000; graine++) {
            List<Reseau.Tirage> tirages = tirages(profil, graine);
            boolean erreur = tirages.stream().anyMatch(t -> t.incident() == Reseau.Incident.ERREUR);
            boolean coupure = tirages.stream().anyMatch(t -> t.incident() == Reseau.Incident.COUPURE);
            if (erreur && coupure && tirages.stream().allMatch(t -> t.latenceMs() <= LATENCE_MAX_RETENUE_MS)) {
                return graine;
            }
        }
        throw new IllegalStateException("aucune graine adaptée");
    }

    /** Tirages 1 à 10, tels que le serveur les calculera pour la rafale. */
    private static List<Reseau.Tirage> tirages(ProfilReseau profil, long graine) {
        List<Reseau.Tirage> tirages = new ArrayList<>();
        for (long numero = 1; numero <= 10; numero++) {
            tirages.add(tirageAttendu(profil, graine, numero));
        }
        return tirages;
    }

    /** Premier numéro de tirage qui produit cet incident avec ce profil, à une latence raisonnable. */
    private static long numeroPour(ProfilReseau profil, Reseau.Incident incident) {
        for (long numero = 0; numero < 100_000; numero++) {
            Reseau.Tirage t = tirageAttendu(profil, GRAINE_CAS, numero);
            if (t.incident() == incident && t.latenceMs() <= LATENCE_MAX_RETENUE_MS) {
                return numero;
            }
        }
        throw new IllegalStateException("aucun tirage " + incident + " pour " + profil.nom());
    }

    /** État affiché par la page pour l'issue d'un appel. */
    private static String etatAttendu(Reseau.Incident incident) {
        return switch (incident) {
            case AUCUN -> "ok";
            case ERREUR -> "erreur";
            case COUPURE -> "coupure";
        };
    }

    private List<String> lancerRafale() {
        click("#btn-rafale");
        assertThat(waitUntil(drv -> "termine".equals(attr("#rafale-resume", "data-etat")), 60))
                .as("les 10 appels de la rafale sont terminés").isTrue();
        return $$("#rafale-list li").stream().map(li -> li.getDomAttribute("data-etat")).toList();
    }

    @Test
    @Order(1)
    void profil_lent_choisi_dans_la_page() {
        open("latence.html");
        select("#profil-select", "lent");
        clearAndType("#graine-input", String.valueOf(GRAINE_LENT));
        click("#btn-profil-appliquer");
        assertThat(waitText("#profil-actif", "Profil actif : lent, graine " + GRAINE_LENT)).isTrue();
        assertThat(waitText("#tirage-profil", "^lent$")).as("panneau à jour").isTrue();
        assertThat(text("#tirage-graine")).isEqualTo(String.valueOf(GRAINE_LENT));
    }

    @Test
    @Order(2)
    void connexion_sous_reseau_lent() throws Exception {
        int base = latenceDeBase();
        open("login.html");
        type("#login-username", "test");
        type("#login-password", "test");
        long avantClic = System.currentTimeMillis();
        click("#login-submit");
        assertThat(waitUntil(drv -> exists("#welcome-panel:not(.hidden)"), 20)).as("connexion aboutie malgré le réseau lent").isTrue();
        long duree = System.currentTimeMillis() - avantClic;
        assertThat(text("#welcome-message")).contains("Utilisateur Test");
        assertThat(waitUntil(drv -> ligneDuPanneau("/api/login").isPresent(), 10)).as("tirage de la connexion affiché").isTrue();
        LigneTirage ligne = ligneDuPanneau("/api/login").orElseThrow();
        Reseau.Tirage attendu = tirageAttendu(ProfilReseau.LENT, GRAINE_LENT, ligne.numero());
        assertThat(ligne.latenceMs()).as("latence affichée = latence calculée d'après la graine").isEqualTo(attendu.latenceMs());
        assertThat(duree).as("connexion après au moins la latence tirée + la latence de base")
                .isGreaterThanOrEqualTo(attendu.latenceMs() + (long) base);
    }

    @Test
    @Order(3)
    void listes_dependantes_sous_reseau_lent() throws Exception {
        int base = latenceDeBase();
        long avantOuverture = System.currentTimeMillis();
        open("forms.html");
        assertThat(waitText("#select-country", "Allemagne", 20)).as("liste des pays chargée").isTrue();
        long dureePays = System.currentTimeMillis() - avantOuverture;
        assertThat(waitUntil(drv -> ligneDuPanneau("/api/countries").isPresent(), 10)).as("tirage des pays affiché").isTrue();
        LigneTirage pays = ligneDuPanneau("/api/countries").orElseThrow();
        Reseau.Tirage attenduPays = tirageAttendu(ProfilReseau.LENT, GRAINE_LENT, pays.numero());
        assertThat(pays.latenceMs()).as("latence affichée = latence calculée d'après la graine").isEqualTo(attenduPays.latenceMs());
        assertThat(dureePays).as("pays affichés après au moins la latence tirée + la latence de base")
                .isGreaterThanOrEqualTo(attenduPays.latenceMs() + (long) base);

        long avantChoix = System.currentTimeMillis();
        select("#select-country", "IT");
        assertThat(waitUntil(drv -> $("#select-city").isEnabled(), 20)).as("villes chargées").isTrue();
        long dureeVilles = System.currentTimeMillis() - avantChoix;
        assertThat($$("#select-city option").stream().map(WebElement::getText)).contains("Milan");
        assertThat(waitUntil(drv -> ligneDuPanneau("/api/countries/IT/cities").isPresent(), 10)).as("tirage des villes affiché").isTrue();
        LigneTirage villes = ligneDuPanneau("/api/countries/IT/cities").orElseThrow();
        Reseau.Tirage attendu = tirageAttendu(ProfilReseau.LENT, GRAINE_LENT, villes.numero());
        assertThat(villes.latenceMs()).isEqualTo(attendu.latenceMs());
        assertThat(dureeVilles).as("villes affichées après au moins la latence tirée + la latence de base")
                .isGreaterThanOrEqualTo(attendu.latenceMs() + (long) base);
    }

    static Stream<Arguments> cas() {
        return Stream.of(
                Arguments.of(ProfilReseau.LENT, Reseau.Incident.AUCUN),
                Arguments.of(ProfilReseau.INSTABLE, Reseau.Incident.AUCUN),
                Arguments.of(ProfilReseau.INSTABLE, Reseau.Incident.ERREUR),
                Arguments.of(ProfilReseau.CATASTROPHIQUE, Reseau.Incident.AUCUN),
                Arguments.of(ProfilReseau.CATASTROPHIQUE, Reseau.Incident.ERREUR),
                Arguments.of(ProfilReseau.CATASTROPHIQUE, Reseau.Incident.COUPURE));
    }

    @ParameterizedTest(name = "{0} : {1}")
    @MethodSource("cas")
    @Order(4)
    void appel_unique_conforme_au_tirage(ProfilReseau profil, Reseau.Incident incident) throws Exception {
        long numero = numeroPour(profil, incident);
        Reseau.Tirage attendu = tirageAttendu(profil, GRAINE_CAS, numero);
        // L'appel n'atteint l'API (et sa latence de base) que s'il n'y a pas d'incident
        long minimum = attendu.latenceMs() + (incident == Reseau.Incident.AUCUN ? (long) latenceDeBase() : 0L);
        appliquerProfil(profil.nom(), GRAINE_CAS);
        open("latence.html");
        // Plus aucun autre appel après le nôtre : le tableau auto-rafraîchi est suspendu
        click("#btn-auto-stop");
        assertThat(waitText("#tirage-profil", "^" + profil.nom() + "$")).isTrue();
        clearAndType("#tirage-impose-input", String.valueOf(numero));

        long avantClic = System.currentTimeMillis();
        click("#btn-appel-unique");
        assertThat(waitUntil(drv -> {
            String etat = attr("#appel-unique-resultat", "data-etat");
            return etat != null && !"en-cours".equals(etat) && !"attente".equals(etat);
        }, 30)).as("appel terminé").isTrue();
        long duree = System.currentTimeMillis() - avantClic;

        assertThat(attr("#appel-unique-resultat", "data-etat")).as("issue de l'appel").isEqualTo(etatAttendu(incident));
        assertThat(duree).as("durée vue par le test ≥ " + minimum + " ms").isGreaterThanOrEqualTo(minimum);
        assertThat(Long.parseLong(attr("#appel-unique-resultat", "data-duree-ms"))).as("durée mesurée dans la page")
                .isGreaterThanOrEqualTo(minimum);
        assertThat(waitUntil(drv -> lignesDuPanneau().stream().findFirst()
                .filter(l -> l.numero() == numero && l.chemin().equals("/api/ping")).isPresent(), 10))
                .as("tirage n°" + numero + " affiché en tête du panneau").isTrue();
        LigneTirage ligne = lignesDuPanneau().get(0);
        assertThat(ligne.latenceMs()).as("latence affichée").isEqualTo(attendu.latenceMs());
        assertThat(ligne.incident()).as("incident affiché").isEqualTo(incident.name().toLowerCase(Locale.ROOT));
    }

    @Test
    @Order(5)
    void rafale_catastrophique_conforme_a_la_graine_et_rejouable() throws Exception {
        appliquerProfil("catastrophique", GRAINE_CATASTROPHE);
        open("latence.html");
        assertThat(waitText("#profil-actif", "catastrophique, graine " + GRAINE_CATASTROPHE, 15)).isTrue();
        // Le tableau auto-rafraîchi occuperait les connexions du navigateur pendant toute la rafale
        click("#btn-auto-stop");
        List<String> attendus = tirages(ProfilReseau.CATASTROPHIQUE, GRAINE_CATASTROPHE).stream()
                .map(t -> etatAttendu(t.incident())).toList();

        List<String> premiere = lancerRafale();
        assertThat(premiere).as("issue de chaque appel conforme au tirage").isEqualTo(attendus);
        assertThat(premiere).contains("ok", "erreur", "coupure");

        List<String> seconde = lancerRafale();
        assertThat(seconde).as("même graine, même séquence").isEqualTo(premiere);
    }

    @Test
    @Order(6)
    void retour_a_un_reseau_normal() {
        select("#profil-select", "aucun");
        click("#btn-profil-appliquer");
        assertThat(waitText("#profil-actif", "Profil actif : aucun$")).isTrue();
        assertThat(waitText("#tirage-graine", "^n/a$")).as("panneau sans profil").isTrue();
        click("#btn-rafale");
        assertThat(waitText("#rafale-resume", "10 OK, 0 erreur\\(s\\), 0 coupure\\(s\\)", 10)).isTrue();
    }
}
