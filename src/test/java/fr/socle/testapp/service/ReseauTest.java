package fr.socle.testapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Réseau simulé : tirages reproductibles, bornes des profils, remise à zéro de l'espace. */
class ReseauTest {

    private static List<Reseau.Tirage> sequence(ProfilReseau profil, long graine, int taille) {
        Reseau reseau = new Reseau();
        reseau.configurer(profil, graine);
        List<Reseau.Tirage> tirages = new ArrayList<>();
        for (int i = 0; i < taille; i++) {
            tirages.add(reseau.tirer(null, "/api/ping"));
        }
        return tirages;
    }

    @Test
    void meme_graine_meme_sequence() {
        assertThat(sequence(ProfilReseau.CATASTROPHIQUE, 42, 200)).isEqualTo(sequence(ProfilReseau.CATASTROPHIQUE, 42, 200));
        assertThat(sequence(ProfilReseau.CATASTROPHIQUE, 42, 200)).isNotEqualTo(sequence(ProfilReseau.CATASTROPHIQUE, 43, 200));
    }

    @Test
    void numero_impose_independant_de_l_ordre_d_arrivee() {
        Reseau reseau = new Reseau();
        reseau.configurer(ProfilReseau.INSTABLE, 7L);
        Reseau.Tirage cinq = reseau.tirer(5L, "/api/ping");
        reseau.tirer(null, "/api/ping");
        reseau.tirer(9L, "/api/ping");
        assertThat(reseau.tirer(5L, "/api/ping")).isEqualTo(cinq);
        assertThat(cinq.numero()).isEqualTo(5L);
    }

    @Test
    void appliquer_le_profil_relance_la_sequence() {
        Reseau reseau = new Reseau();
        reseau.configurer(ProfilReseau.LENT, 3L);
        Reseau.Tirage premier = reseau.tirer(null, "/api/ping");
        reseau.tirer(null, "/api/ping");
        assertThat(reseau.snapshot()).containsEntry("tiragesEffectues", 2L);
        reseau.configurer(ProfilReseau.LENT, 3L);
        assertThat(reseau.tirer(null, "/api/ping")).isEqualTo(premier);
    }

    @Test
    void latences_et_incidents_conformes_au_profil() {
        for (ProfilReseau profil : ProfilReseau.values()) {
            Map<Reseau.Incident, Integer> compte = new EnumMap<>(Reseau.Incident.class);
            for (Reseau.Tirage t : sequence(profil, 11, 10_000)) {
                assertThat(t.latenceMs()).as(profil.nom()).isBetween(profil.latenceMinMs(), profil.latenceMaxMs());
                compte.merge(t.incident(), 1, Integer::sum);
            }
            double erreurs = compte.getOrDefault(Reseau.Incident.ERREUR, 0) / 10_000.0;
            double coupures = compte.getOrDefault(Reseau.Incident.COUPURE, 0) / 10_000.0;
            assertThat(erreurs).as(profil.nom() + " : erreurs").isBetween(profil.tauxErreur() - 0.02, profil.tauxErreur() + 0.02);
            assertThat(coupures).as(profil.nom() + " : coupures").isBetween(profil.tauxCoupure() - 0.02, profil.tauxCoupure() + 0.02);
        }
    }

    @Test
    void sans_graine_une_graine_aleatoire_est_choisie_et_restituee() {
        Reseau reseau = new Reseau();
        assertThat(reseau.actif()).isFalse();
        reseau.configurer(ProfilReseau.INSTABLE, null);
        assertThat(reseau.actif()).isTrue();
        assertThat(reseau.profil()).isEqualTo(ProfilReseau.INSTABLE);
        assertThat(reseau.snapshot()).containsEntry("profil", "instable").containsKeys("graine", "latenceMaxMs", "tauxErreur", "tauxCoupure");
        assertThat((Long) reseau.snapshot().get("graine")).isPositive();
    }

    @Test
    void profils_par_nom() {
        assertThat(ProfilReseau.parNom(" Catastrophique ")).contains(ProfilReseau.CATASTROPHIQUE);
        assertThat(ProfilReseau.parNom("lent")).contains(ProfilReseau.LENT);
        assertThat(ProfilReseau.parNom("inconnu")).isEmpty();
        assertThat(ProfilReseau.parNom(null)).isEmpty();
    }

    @Test
    void remise_a_zero_de_l_espace_retire_le_profil() {
        Espace espace = new Espace("e", new Latency(0, 0), 0L);
        espace.reseau().configurer(ProfilReseau.CATASTROPHIQUE, 1L);
        espace.reset();
        assertThat(espace.reseau().actif()).isFalse();
    }

    // ---------- Journal des tirages ----------

    @Test
    void chaque_tirage_est_journalise_du_plus_recent_au_plus_ancien() {
        Reseau reseau = new Reseau();
        reseau.configurer(ProfilReseau.CATASTROPHIQUE, 3L);
        reseau.tirer(null, "/api/ping");
        reseau.tirer(7L, "/api/login");
        assertThat(reseau.journal()).extracting(Reseau.Entree::numero, Reseau.Entree::chemin)
                .containsExactly(tuple(7L, "/api/login"), tuple(0L, "/api/ping"));
        Reseau.Entree login = reseau.journal().get(0);
        assertThat(login.latenceMs()).isEqualTo(reseau.calculer(7L).latenceMs());
        assertThat(login.incident()).isEqualTo(reseau.calculer(7L).incident());
        assertThat(login.horodatage()).isPositive();
    }

    @Test
    void journal_limite_aux_derniers_tirages_et_vide_quand_on_applique_un_profil() {
        Reseau reseau = new Reseau();
        reseau.configurer(ProfilReseau.LENT, 1L);
        for (int i = 0; i < Reseau.TAILLE_JOURNAL + 10; i++) {
            reseau.tirer(null, "/api/ping");
        }
        assertThat(reseau.journal()).hasSize(Reseau.TAILLE_JOURNAL);
        assertThat(reseau.journal().get(0).numero()).isEqualTo(Reseau.TAILLE_JOURNAL + 9L);
        reseau.configurer(ProfilReseau.LENT, 1L);
        assertThat(reseau.journal()).isEmpty();
    }

    @Test
    void calculer_un_tirage_ne_consomme_rien() {
        Reseau reseau = new Reseau();
        reseau.configurer(ProfilReseau.INSTABLE, 9L);
        Reseau.Tirage prevu = reseau.calculer(4L);
        assertThat(reseau.journal()).isEmpty();
        assertThat(reseau.snapshot()).containsEntry("tiragesEffectues", 0L);
        assertThat(reseau.tirer(4L, "/api/ping")).isEqualTo(prevu);
    }
}
