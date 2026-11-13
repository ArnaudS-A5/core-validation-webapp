(function ($) {
  'use strict';

  // --- Compteur ---
  var counter = 0;
  function renderCounter() {
    $('#counter-value').text(counter);
  }
  $('#btn-increment').on('click', function () { counter++; renderCounter(); });
  $('#btn-decrement').on('click', function () { counter--; renderCounter(); });
  $('#btn-reset-counter').on('click', function () { counter = 0; renderCounter(); });

  // --- Souris ---
  $('#btn-double-click').on('dblclick', function () {
    App.showResult('#double-click-result', 'Double-clic détecté', false);
  });
  $('#btn-right-click').on('contextmenu', function (event) {
    event.preventDefault();
    App.showResult('#right-click-result', 'Clic droit détecté', false);
  });
  $('#hover-target')
    .on('mouseenter', function () { $('#hover-tooltip').show(); })
    .on('mouseleave', function () { $('#hover-tooltip').hide(); });

  // --- États ---
  $('#btn-disabled').on('click', function () {
    App.showResult('#disabled-result', 'Bouton désormais actif : clic pris en compte', false);
  });
  $('#btn-enable-disabled').on('click', function () {
    var $button = $(this).prop('disabled', true);
    App.api('GET', 'slow?delay=1500').done(function () {
      $('#btn-disabled').prop('disabled', false);
      App.showResult('#disabled-result', 'Bouton activé après chargement', false);
    }).always(function () {
      $button.prop('disabled', false);
    });
  });

  $('#btn-reveal').on('click', function () {
    App.api('GET', 'slow?delay=1200').done(function () {
      $('#hidden-btn').removeClass('hidden');
    });
  });
  $('#hidden-btn').on('click', function () {
    App.showResult('#reveal-result', 'Bouton révélé cliqué', false);
  });

  $('#btn-toggle').on('click', function () {
    var pressed = $(this).attr('aria-pressed') !== 'true';
    $(this).attr('aria-pressed', String(pressed)).text('Mode : ' + (pressed ? 'activé' : 'désactivé'));
  });

  // --- Éléments mobiles / recouverts ---
  $('#btn-move').on('click', function () {
    $('#moving-btn').toggleClass('moved');
  });
  $('#moving-btn').on('click', function () {
    App.showResult('#moving-result', 'Bouton mobile cliqué', false);
  });

  setTimeout(function () { $('#cover').remove(); }, 2000);
  // Bouton recouvert en permanence : le calque n'est jamais retiré
  $('#btn-always-covered').on('click', function () {
    App.showResult('#always-covered-result', 'Bouton recouvert en permanence cliqué (contournement par JavaScript)', false);
  });

  $('#btn-covered').on('click', function () {
    App.showResult('#covered-result', 'Bouton recouvert cliqué', false);
  });

  // --- Appel serveur ---
  $('#btn-server-click').on('click', function () {
    var $button = $(this).prop('disabled', true);
    $('#server-click-spinner').show();
    App.showResult('#server-click-result', '', false);
    App.api('GET', 'clock')
      .done(function (data) {
        App.showResult('#server-click-result', 'Heure serveur : ' + data.time, false);
      })
      .fail(function (jqXHR) {
        App.showResult('#server-click-result', App.errorMessage(jqXHR), true);
      })
      .always(function () {
        $('#server-click-spinner').hide();
        $button.prop('disabled', false);
      });
  });

  // --- Lignes identiques ---
  $('#row-list').on('click', 'button', function () {
    var $row = $(this).closest('li');
    App.showResult('#row-result', $row.attr('id') + ' supprimée', false);
    $row.remove();
  });
})(jQuery);
