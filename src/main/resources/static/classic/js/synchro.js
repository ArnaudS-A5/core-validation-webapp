/*
 * Synchro : défilement infini, animation longue, flux SSE, seuil, Shadow DOM fermé et imbriqué, bandeau fixe.
 */
(function ($) {
  'use strict';

  // --- Défilement infini : 10 articles par appel, chargés à l'approche du bas de la zone ---
  var page = 0;
  var loading = false;
  var finished = false;
  var loaded = 0;

  function loadMore() {
    if (loading || finished) {
      return;
    }
    loading = true;
    $('#infinite-spinner').show();
    App.api('GET', 'items?page=' + page + '&size=10&sort=id&dir=asc').done(function (data) {
      data.items.forEach(function (item) {
        $('#infinite-list').append($('<li>', { id: 'infinite-item-' + item.id, text: item.name }));
      });
      loaded += data.items.length;
      page++;
      finished = page >= data.totalPages;
      App.showResult('#infinite-status', finished
        ? 'Tous les articles sont chargés (' + loaded + ')'
        : loaded + ' / ' + data.totalElements + ' articles chargés', false);
    }).fail(function (jqXHR) {
      App.showResult('#infinite-status', App.errorMessage(jqXHR), true);
    }).always(function () {
      loading = false;
      $('#infinite-spinner').hide();
    });
  }

  $('#infinite-zone').on('scroll', function () {
    if (this.scrollTop + this.clientHeight >= this.scrollHeight - 40) {
      loadMore();
    }
  });
  loadMore();

  // --- Animation longue ---
  $('#btn-animate').on('click', function () {
    $('#anim-target').attr('data-state', 'moving').toggleClass('moved');
    App.showResult('#anim-result', 'Cible en mouvement…', false);
  });
  $('#anim-target').on('transitionend', function () {
    $(this).attr('data-state', 'idle');
  }).on('click', function () {
    var moving = $(this).attr('data-state') === 'moving';
    App.showResult('#anim-result', 'Cible cliquée ' + (moving ? '(en mouvement)' : '(à l\'arrêt)'), moving);
  });

  // --- Flux SSE ---
  var source = null;
  var received = 0;

  function stopFeed(message) {
    if (source) {
      source.close();
      source = null;
    }
    App.showResult('#feed-status', message, false);
  }

  $('#btn-feed-start').on('click', function () {
    stopFeed('Flux redémarré');
    received = 0;
    $('#feed-list').empty();
    source = new EventSource(App.apiUrl('stream/events?count=12&interval=600'));
    App.showResult('#feed-status', 'Flux démarré, en attente du premier événement…', false);
    source.addEventListener('message', function (event) {
      var data = JSON.parse(event.data);
      received++;
      $('#feed-list').append($('<li>', { id: 'feed-item-' + data.seq, text: data.text }));
      $('#feed-list li').slice(0, -5).remove();
      App.showResult('#feed-status', received + ' événement(s) reçu(s)', false);
    });
    source.addEventListener('end', function () {
      stopFeed('Flux terminé (' + received + ' événements)');
    });
    source.onerror = function () {
      if (source && source.readyState === EventSource.CLOSED) {
        stopFeed('Flux interrompu');
      }
    };
  });
  $('#btn-feed-stop').on('click', function () {
    stopFeed('Flux arrêté (' + received + ' événements)');
  });

  // --- Valeur qui évolue jusqu'à un seuil (pas aléatoires) ---
  var thresholdTimer = null;
  $('#btn-threshold-start').on('click', function () {
    clearInterval(thresholdTimer);
    var value = 0;
    $('#btn-threshold-action').prop('disabled', true);
    App.showResult('#threshold-result', '', false);
    thresholdTimer = setInterval(function () {
      value = Math.min(100, value + 3 + Math.floor(Math.random() * 10));
      $('#threshold-value').text('Progression : ' + value + ' %').attr('data-value', value);
      $('#btn-threshold-action').prop('disabled', value < 80);
      if (value >= 100) {
        clearInterval(thresholdTimer);
      }
    }, 300);
  });
  $('#btn-threshold-action').on('click', function () {
    App.showResult('#threshold-result', 'Action réalisée à ' + $('#threshold-value').attr('data-value') + ' %', false);
  });

  // --- Shadow DOM fermé ---
  var closedRoot = document.getElementById('shadow-closed-host').attachShadow({ mode: 'closed' });
  closedRoot.innerHTML = '<button type="button" style="font: inherit; padding: .5rem 1rem;">Bouton dans un Shadow DOM fermé</button>';
  closedRoot.querySelector('button').addEventListener('click', function () {
    App.showResult('#shadow-closed-result', 'Bouton du Shadow DOM fermé cliqué', false);
  });

  // --- Shadow DOM imbriqué (ouvert), interne créé après 1 s ---
  var outerRoot = document.getElementById('shadow-outer-host').attachShadow({ mode: 'open' });
  outerRoot.innerHTML = '<p id="shadow-outer-text" data-testid="shadow-outer-text">Shadow DOM externe</p>'
    + '<div id="shadow-inner-host" data-testid="shadow-inner-host"></div>';
  setTimeout(function () {
    var innerRoot = outerRoot.getElementById('shadow-inner-host').attachShadow({ mode: 'open' });
    innerRoot.innerHTML = '<button type="button" id="shadow-inner-button" data-testid="shadow-inner-button">Bouton du Shadow DOM interne</button>'
      + '<span id="shadow-inner-result" data-testid="shadow-inner-result"></span>';
    innerRoot.getElementById('shadow-inner-button').addEventListener('click', function () {
      innerRoot.getElementById('shadow-inner-result').textContent = 'Bouton du Shadow DOM imbriqué cliqué';
    });
  }, 1000);

  // --- Bandeau fixe ---
  $('#btn-banner-accept').on('click', function () {
    $('#sticky-banner').remove();
  });
  $('#btn-under-banner').on('click', function () {
    App.showResult('#banner-result', 'Bouton en bas de page cliqué', false);
  });
})(jQuery);
