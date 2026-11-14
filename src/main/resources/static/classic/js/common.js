/*
 * Socle TestApp — utilitaires partagés (front « classic », jQuery).
 *
 * Contrat de localisation des éléments : l'attribut `id` fait foi. Le script recopie
 * automatiquement chaque `id` dans `data-testid` (y compris pour les éléments ajoutés dynamiquement).
 *
 * Paramètre d'URL `?delay=N` : force une latence de N ms sur tous les appels API émis par la page.
 *
 * Panneau des tirages (`#panneau-tirages`) sous l'en-tête de chaque page : profil réseau de l'espace, graine
 * et 5 derniers tirages, relus sur `/api/admin/tirages` après chaque appel d'API (« n/a » sans profil).
 */
(function ($) {
  'use strict';

  var scriptUrl = document.currentScript ? document.currentScript.src : window.location.href;
  var apiBase = new URL('../../api/', scriptUrl).href;
  var pageDelay = new URLSearchParams(window.location.search).get('delay');

  function mirrorTestIds(root) {
    $(root).find('[id]').addBack('[id]').each(function () {
      if (!this.hasAttribute('data-testid')) {
        this.setAttribute('data-testid', this.id);
      }
    });
  }

  // Version déployée, affichée en permanence dans l'en-tête (sans la latence simulée de la page)
  function showVersion() {
    var $header = $('.site-header');
    if ($header.length === 0) {
      return;
    }
    var $version = $('<span>', { id: 'app-version', 'class': 'app-version', text: 'version …' }).appendTo($header);
    $.getJSON(apiBase + 'version').done(function (v) {
      $version.text('v' + v.version + (v.buildTime ? ' · ' + v.buildTime : '') + ' · ' + v.commit)
        .attr('data-version', v.version);
    }).fail(function () {
      $version.text('version inconnue').attr('data-version', '');
    });
  }

  // Panneau des tirages du réseau simulé, sous l'en-tête : profil, graine et 5 derniers tirages (« n/a » sans profil).
  // L'en-tête et le panneau restent à l'écran, le contenu de la page défile en dessous.
  var LIGNES_TIRAGES = 5;
  var NA = 'n/a';
  var demandeTirages = 0;

  function creerPanneauTirages() {
    var $header = $('.site-header');
    if ($header.length === 0) {
      return;
    }
    var $panneau = $('<section>', { id: 'panneau-tirages', 'class': 'panneau-tirages', 'aria-label': 'Tirages du réseau simulé' });
    $('<div>', { 'class': 'tirages-resume' }).append(
      'Réseau simulé : profil ', $('<strong>', { id: 'tirage-profil', text: NA }),
      ' · graine ', $('<strong>', { id: 'tirage-graine', text: NA }),
      ' · tirages effectués ', $('<strong>', { id: 'tirages-effectues', text: NA })
    ).appendTo($panneau);
    var $corps = $('<tbody>', { id: 'tirages-body' });
    for (var i = 0; i < LIGNES_TIRAGES; i++) {
      var $ligne = $('<tr>', { id: 'tirage-ligne-' + i });
      ['numero', 'chemin', 'latence', 'incident', 'heure'].forEach(function (champ) {
        $('<td>', { id: 'tirage-' + champ + '-' + i, text: NA }).appendTo($ligne);
      });
      $ligne.appendTo($corps);
    }
    $('<table>', { id: 'tirages-table' }).append(
      $('<thead>').append($('<tr>').append(
        $('<th>', { text: 'N°' }), $('<th>', { text: 'Chemin' }), $('<th>', { text: 'Latence' }),
        $('<th>', { text: 'Incident' }), $('<th>', { text: 'Heure' }))),
      $corps
    ).appendTo($panneau);
    $panneau.insertAfter($header);
    $('body').addClass('avec-panneau');
  }

  function heure(ms) {
    var date = new Date(ms);
    return date.toTimeString().substring(0, 8) + '.' + String(date.getMilliseconds()).padStart(3, '0');
  }

  function afficherTirages(journal) {
    var actif = journal.profil !== 'aucun';
    $('#tirage-profil').text(journal.profil);
    $('#tirage-graine').text(actif ? journal.graine : NA);
    $('#tirages-effectues').text(actif ? journal.tiragesEffectues : NA);
    for (var i = 0; i < LIGNES_TIRAGES; i++) {
      var tirage = journal.tirages[i];
      var $ligne = $('#tirage-ligne-' + i);
      $('#tirage-numero-' + i).text(tirage ? tirage.numero : NA);
      $('#tirage-chemin-' + i).text(tirage ? tirage.chemin : NA);
      $('#tirage-latence-' + i).text(tirage ? tirage.latenceMs + ' ms' : NA);
      $('#tirage-incident-' + i).text(tirage ? tirage.incident : NA);
      $('#tirage-heure-' + i).text(tirage ? heure(tirage.horodatage) : NA);
      if (tirage) {
        $ligne.attr({ 'data-numero': tirage.numero, 'data-latence': tirage.latenceMs, 'data-incident': tirage.incident });
      } else {
        $ligne.removeAttr('data-numero data-latence data-incident');
      }
    }
  }

  /** Relit le journal des tirages ; une réponse dépassée par une demande plus récente est ignorée. */
  function rafraichirTirages() {
    if ($('#panneau-tirages').length === 0) {
      return;
    }
    var demande = ++demandeTirages;
    // global: false : cette lecture ne relance pas elle-même un rafraîchissement (voir ajaxComplete)
    $.ajax({ url: apiBase + 'admin/tirages', dataType: 'json', cache: false, global: false }).done(function (journal) {
      if (demande === demandeTirages) {
        afficherTirages(journal);
      }
    });
  }

  $(function () {
    creerPanneauTirages();
    rafraichirTirages();
    // Après chaque appel jQuery terminé (réussi, en erreur ou coupé), le panneau montre le tirage qu'il a reçu
    $(document).ajaxComplete(rafraichirTirages);
    showVersion();
    mirrorTestIds(document.body);
    new MutationObserver(function (mutations) {
      mutations.forEach(function (mutation) {
        $(mutation.addedNodes).each(function () {
          if (this.nodeType === 1) {
            mirrorTestIds(this);
          }
        });
      });
    }).observe(document.body, { childList: true, subtree: true });
  });

  function withDelay(url) {
    if (pageDelay === null || /[?&]delay=/.test(url)) {
      return url;
    }
    return url + (url.indexOf('?') === -1 ? '?' : '&') + 'delay=' + encodeURIComponent(pageDelay);
  }

  window.App = {
    /** Appel JSON vers l'API : App.api('GET', 'items?page=0') ou App.api('POST', 'login', {...}). */
    api: function (method, path, body) {
      var options = {
        url: withDelay(apiBase + path),
        method: method,
        dataType: 'json'
      };
      if (body !== undefined) {
        options.contentType = 'application/json';
        options.data = JSON.stringify(body);
      }
      return $.ajax(options);
    },

    apiUrl: function (path) {
      return apiBase + path;
    },

    /** Met à jour le panneau des tirages (à appeler après un appel qui ne passe pas par jQuery, comme fetch). */
    rafraichirTirages: rafraichirTirages,

    /** Message lisible à partir d'une erreur jqXHR. */
    errorMessage: function (jqXHR) {
      if (jqXHR.responseJSON && jqXHR.responseJSON.error) {
        return jqXHR.responseJSON.error + ' (HTTP ' + jqXHR.status + ')';
      }
      return jqXHR.status ? 'Erreur HTTP ' + jqXHR.status : 'Erreur réseau';
    },

    showResult: function (selector, text, isError) {
      $(selector).text(text).toggleClass('error', !!isError);
    }
  };
})(jQuery);
