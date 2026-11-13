/*
 * Socle TestApp — utilitaires partagés (front « classic », jQuery).
 *
 * Contrat de localisation des éléments : l'attribut `id` fait foi. Le script recopie
 * automatiquement chaque `id` dans `data-testid` (y compris pour les éléments ajoutés dynamiquement).
 *
 * Paramètre d'URL `?delay=N` : force une latence de N ms sur tous les appels API émis par la page.
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

  $(function () {
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
