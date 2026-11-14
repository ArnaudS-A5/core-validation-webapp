/*
 * Script servi en retard (?delai=N), inséré après le chargement complet de la page :
 * jusqu'à son arrivée, le bouton #btn-script-tardif est affiché mais ne fait rien.
 */
(function ($) {
  'use strict';

  $('#btn-script-tardif').on('click', function () {
    App.showResult('#script-tardif-resultat', 'Action exécutée par le script tardif', false);
  });
  $('#etat-script').text('Script chargé').attr('data-pret', 'true');
})(jQuery);
