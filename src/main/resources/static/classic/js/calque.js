/*
 * Calque permanent : les gestionnaires ne sont atteignables que par un clic JavaScript.
 */
(function ($) {
  'use strict';
  $('#btn-under-overlay').on('click', function () {
    App.showResult('#under-overlay-result', 'Bouton sous le calque cliqué (contournement par JavaScript)', false);
  });
  $('#link-under-overlay').on('click', function () {
    App.showResult('#under-overlay-result', 'Lien sous le calque cliqué (contournement par JavaScript)', false);
  });
})(jQuery);
