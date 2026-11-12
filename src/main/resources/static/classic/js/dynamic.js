(function ($) {
  'use strict';

  // 1. Chargement différé
  $('#btn-load-delayed').on('click', function () {
    var delay = Number($('#delay-input').val()) || 0;
    var $button = $(this).prop('disabled', true);
    $('#delayed-spinner').show();
    App.showResult('#delayed-content', '', false);
    App.api('GET', 'slow?delay=' + delay)
      .done(function (data) { App.showResult('#delayed-content', data.message, false); })
      .fail(function (jqXHR) { App.showResult('#delayed-content', App.errorMessage(jqXHR), true); })
      .always(function () {
        $('#delayed-spinner').hide();
        $button.prop('disabled', false);
      });
  });

  // 2. Calque bloquant
  $('#btn-blocking').on('click', function () {
    var $overlay = $('<div>', { id: 'blocking-overlay', 'class': 'overlay-full', text: 'Traitement en cours…' });
    $('body').append($overlay);
    setTimeout(function () { $overlay.remove(); }, 1500);
  });
  $('#btn-after-blocking').on('click', function () {
    App.showResult('#blocking-result', 'Bouton situé sous le calque cliqué', false);
  });

  // 3. Élément ré-rendu : chaque clic remplace le nœud DOM par un nouveau nœud portant le même id
  var renderCount = 1;
  var targetClicks = 0;
  function renderTarget() {
    var $fresh = $('<button>', { type: 'button', id: 'rerender-target', text: 'Bouton cible' });
    $fresh.on('click', function () {
      targetClicks++;
      $('#rerender-clicks').text(targetClicks);
    });
    $('#rerender-zone').empty().append($fresh);
  }
  renderTarget();
  $('#btn-rerender').on('click', function () {
    App.api('GET', 'clock').done(function () {
      renderCount++;
      $('#rerender-count').text(renderCount);
      renderTarget();
    });
  });

  // 4. Auto-complétion avec anti-rebond et ignorance des réponses obsolètes
  var autocompleteTimer = null;
  var autocompleteSeq = 0;
  function hideSuggestions() {
    $('#autocomplete-list').addClass('hidden').empty();
  }
  $('#autocomplete-input').on('input', function () {
    var query = $(this).val().trim();
    clearTimeout(autocompleteTimer);
    if (!query) {
      hideSuggestions();
      return;
    }
    autocompleteTimer = setTimeout(function () {
      var seq = ++autocompleteSeq;
      App.api('GET', 'search?q=' + encodeURIComponent(query)).done(function (data) {
        if (seq !== autocompleteSeq) {
          return; // réponse obsolète
        }
        var $list = $('#autocomplete-list').empty();
        $.each(data.results, function (index, text) {
          $list.append($('<li>', { id: 'suggestion-' + index, role: 'option', text: text }));
        });
        $list.toggleClass('hidden', data.results.length === 0);
      });
    }, 300);
  });
  $('#autocomplete-list').on('click', 'li', function () {
    var text = $(this).text();
    $('#autocomplete-input').val(text);
    $('#autocomplete-result').text('Sélection : ' + text);
    autocompleteSeq++;
    hideSuggestions();
  });

  // 5. Polling de l'horloge
  var clockTimer = null;
  function pollClock() {
    App.api('GET', 'clock').done(function (data) {
      $('#clock-value').text(data.time);
    });
  }
  $('#btn-start-clock').on('click', function () {
    if (clockTimer) { return; }
    $('#btn-start-clock').prop('disabled', true);
    $('#btn-stop-clock').prop('disabled', false);
    pollClock();
    clockTimer = setInterval(pollClock, 1000);
  });
  $('#btn-stop-clock').on('click', function () {
    clearInterval(clockTimer);
    clockTimer = null;
    $('#btn-start-clock').prop('disabled', false);
    $('#btn-stop-clock').prop('disabled', true);
  });

  // 6. Tâche longue
  function renderProgress(percent, status) {
    $('#progress-bar').css('width', percent + '%').attr('aria-valuenow', percent);
    $('#progress-text').text(percent + ' %');
    $('#job-status').text(status);
  }
  $('#btn-start-job').on('click', function () {
    var duration = Number($('#job-duration').val()) || 4000;
    var $button = $(this).prop('disabled', true);
    renderProgress(0, 'En cours');
    App.api('POST', 'jobs?duration=' + duration).done(function (job) {
      var poll = setInterval(function () {
        App.api('GET', 'jobs/' + job.jobId).done(function (state) {
          renderProgress(state.percent, state.status === 'DONE' ? 'Terminé' : 'En cours');
          if (state.status === 'DONE') {
            clearInterval(poll);
            $button.prop('disabled', false);
          }
        }).fail(function () {
          clearInterval(poll);
          $('#job-status').text('Erreur');
          $button.prop('disabled', false);
        });
      }, 500);
    }).fail(function () {
      $('#job-status').text('Erreur');
      $button.prop('disabled', false);
    });
  });

  // 7. Service instable
  var flakyKey = 'page-' + Date.now();
  $('#btn-flaky').on('click', function () {
    var $button = $(this).prop('disabled', true);
    App.api('GET', 'flaky?failures=2&key=' + encodeURIComponent(flakyKey))
      .done(function (data) { App.showResult('#flaky-status', data.message, false); })
      .fail(function (jqXHR) {
        var attempt = jqXHR.responseJSON ? jqXHR.responseJSON.attempt : '?';
        App.showResult('#flaky-status', 'Échec (HTTP ' + jqXHR.status + ') à la tentative ' + attempt, true);
      })
      .always(function () { $button.prop('disabled', false); });
  });
  $('#btn-flaky-new-key').on('click', function () {
    flakyKey = 'page-' + Date.now() + '-' + Math.floor(Math.random() * 1000);
    App.showResult('#flaky-status', 'Nouvelle série démarrée', false);
  });

  // 8. Charger la suite
  var loadMorePage = 0;
  var loadMoreCount = 0;
  $('#btn-load-more').on('click', function () {
    var $button = $(this).prop('disabled', true);
    App.api('GET', 'items?size=5&page=' + loadMorePage).done(function (data) {
      $.each(data.items, function (_, item) {
        $('#load-more-list').append($('<li>', { id: 'more-item-' + item.id, text: item.name + ' — ' + item.category }));
        loadMoreCount++;
      });
      $('#load-more-count').text(loadMoreCount);
      loadMorePage++;
      if (loadMorePage >= data.totalPages) {
        $button.text('Plus de résultats');
      } else {
        $button.prop('disabled', false);
      }
    }).fail(function () {
      $button.prop('disabled', false);
    });
  });

  // 9. Notification éphémère
  $('#btn-toast').on('click', function () {
    $('#toast').remove();
    var $toast = $('<div>', { id: 'toast', role: 'status', text: 'Opération réussie' });
    $('body').append($toast);
    setTimeout(function () { $toast.remove(); }, 3000);
  });

  // 10. Codes HTTP
  $('#btn-status').on('click', function () {
    var code = $('#select-status').val();
    App.api('GET', 'status/' + code)
      .done(function () { App.showResult('#status-result', 'Succès : HTTP ' + code, false); })
      .fail(function (jqXHR) { App.showResult('#status-result', 'Échec : HTTP ' + jqXHR.status, true); });
  });

  // 11. Requêtes parallèles
  $('#btn-parallel').on('click', function () {
    var $button = $(this).prop('disabled', true);
    $('#parallel-list').empty();
    App.showResult('#parallel-done', '', false);
    var delays = [1500, 500, 1000];
    var requests = $.map(delays, function (delay, index) {
      return App.api('GET', 'slow?delay=' + delay).done(function () {
        $('#parallel-list').append($('<li>', { id: 'parallel-' + (index + 1), text: 'Appel ' + (index + 1) + ' terminé (' + delay + ' ms)' }));
      });
    });
    $.when.apply($, requests).done(function () {
      App.showResult('#parallel-done', 'Les trois appels sont terminés', false);
    }).always(function () {
      $button.prop('disabled', false);
    });
  });
})(jQuery);
