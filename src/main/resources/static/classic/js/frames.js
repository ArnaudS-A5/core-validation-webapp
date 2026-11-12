(function ($) {
  'use strict';

  $('#btn-load-frame').on('click', function () {
    var $button = $(this).prop('disabled', true);
    App.showResult('#frame-delayed-status', 'Chargement de la frame dans 2 secondes…', false);
    $('#frame-delayed-zone').empty();
    setTimeout(function () {
      $('#frame-delayed-zone').append($('<iframe>', {
        id: 'frame-delayed',
        name: 'frameDelayed',
        title: 'Frame différée',
        src: 'frames/frame-delayed.html'
      }));
      App.showResult('#frame-delayed-status', 'Frame ajoutée à la page', false);
      $button.prop('disabled', false);
    }, 2000);
  });

  $('#btn-parent').on('click', function () {
    App.showResult('#parent-result', 'Bouton de la page parente cliqué', false);
  });
})(jQuery);
