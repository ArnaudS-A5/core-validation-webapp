/*
 * Latence : profil réseau de l'espace, appel unique avec numéro de tirage imposé et rafale d'appels, bloc chargé en 4 s, bouton activé tardivement,
 * réponses au compte-gouttes (flux et JSON), tableau rafraîchi automatiquement, script et style tardifs.
 */
(function ($) {
  'use strict';

  var params = new URLSearchParams(window.location.search);
  var delaiRessources = parseInt(params.get('ressources') || '3000', 10);

  // --- Profil réseau de l'espace ---
  function afficherProfil(p) {
    $('#profil-select').val(p.profil);
    $('#profil-actif')
      .text('Profil actif : ' + p.profil + (p.profil === 'aucun' ? '' : ', graine ' + p.graine))
      .removeClass('error')
      .attr('data-profil', p.profil)
      .attr('data-graine', p.graine);
  }

  function appliquerProfil(profil, graine) {
    var corps = { profil: profil };
    if (graine) {
      corps.graine = Number(graine);
    }
    return App.api('PUT', 'admin/profil', corps).done(afficherProfil).fail(function (jqXHR) {
      App.showResult('#profil-actif', App.errorMessage(jqXHR), true);
    });
  }

  $('#btn-profil-appliquer').on('click', function () {
    appliquerProfil($('#profil-select').val(), $('#graine-input').val());
  });

  // --- Appel unique avec un numéro de tirage imposé ---
  var appelUnique = 0;

  $('#btn-appel-unique').on('click', function () {
    var lancement = ++appelUnique;
    var numero = parseInt($('#tirage-impose-input').val(), 10) || 0;
    var debut = performance.now();
    $('#appel-unique-spinner').show();
    App.showResult('#appel-unique-resultat', 'Appel n°' + numero + ' en cours…', false);
    $('#appel-unique-resultat').attr({ 'data-etat': 'en-cours', 'data-numero': numero }).removeAttr('data-duree-ms');

    function terminer(etat, texte) {
      if (lancement !== appelUnique) {
        return; // appel relancé entre-temps : réponse obsolète ignorée
      }
      var duree = Math.round(performance.now() - debut);
      $('#appel-unique-spinner').hide();
      App.showResult('#appel-unique-resultat', 'Appel n°' + numero + ' : ' + texte + ' après ' + duree + ' ms', etat !== 'ok');
      $('#appel-unique-resultat').attr({ 'data-etat': etat, 'data-duree-ms': duree });
    }

    App.api('GET', 'ping?tirage=' + numero).done(function () {
      terminer('ok', 'OK');
    }).fail(function (jqXHR, textStatus) {
      if (jqXHR.status === 0 || textStatus === 'parsererror') {
        terminer('coupure', 'connexion coupée');
      } else {
        terminer('erreur', 'erreur HTTP ' + jqXHR.status);
      }
    });
  });

  // --- Rafale de 10 appels simultanés, chacun avec son numéro de tirage ---
  var rafale = 0;

  $('#btn-rafale').on('click', function () {
    var lancement = ++rafale;
    var bilan = { ok: 0, erreur: 0, coupure: 0 };
    var terminees = 0;
    $('#rafale-list').empty();
    $('#rafale-spinner').show();
    App.showResult('#rafale-resume', '0 / 10 appels terminés', false);
    $('#rafale-resume').attr('data-etat', 'en-cours');

    function terminer($li, etat, texte) {
      if (lancement !== rafale) {
        return; // rafale relancée entre-temps : réponse obsolète ignorée
      }
      $li.attr('data-etat', etat).text(texte);
      bilan[etat]++;
      terminees++;
      var resume = terminees + ' / 10 appels terminés';
      if (terminees === 10) {
        resume += ' : ' + bilan.ok + ' OK, ' + bilan.erreur + ' erreur(s), ' + bilan.coupure + ' coupure(s)';
        $('#rafale-spinner').hide();
        $('#rafale-resume').attr('data-etat', 'termine');
      }
      App.showResult('#rafale-resume', resume, false);
    }

    for (var i = 1; i <= 10; i++) {
      (function (numero) {
        var $li = $('<li>', { id: 'rafale-' + numero, 'data-etat': 'attente', text: 'Appel ' + numero + ' : en attente…' })
          .appendTo('#rafale-list');
        var debut = Date.now();
        App.api('GET', 'ping?tirage=' + numero).done(function () {
          terminer($li, 'ok', 'Appel ' + numero + ' : OK en ' + (Date.now() - debut) + ' ms');
        }).fail(function (jqXHR, textStatus) {
          // Connexion coupée : erreur réseau (statut 0), ou corps tronqué que certains navigateurs livrent tel quel
          if (jqXHR.status === 0 || textStatus === 'parsererror') {
            terminer($li, 'coupure', 'Appel ' + numero + ' : connexion coupée');
          } else {
            terminer($li, 'erreur', 'Appel ' + numero + ' : erreur HTTP ' + jqXHR.status);
          }
        });
      })(i);
    }
  });

  // --- Bloc chargé en 4 s ---
  var bloc = 0;

  function chargerBloc() {
    var lancement = ++bloc;
    var debut = performance.now();
    $('#bloc-long-spinner').show();
    App.showResult('#bloc-long', 'Chargement du bloc…', false);
    $('#bloc-long').attr('data-etat', 'chargement');
    App.api('GET', 'slow?delay=4000').done(function () {
      if (lancement === bloc) {
        var duree = Math.round(performance.now() - debut);
        App.showResult('#bloc-long', 'Bloc chargé en ' + (duree / 1000).toFixed(1) + ' s', false);
        $('#bloc-long').attr({ 'data-etat': 'charge', 'data-duree-ms': duree });
      }
    }).fail(function (jqXHR) {
      if (lancement === bloc) {
        App.showResult('#bloc-long', App.errorMessage(jqXHR), true);
        $('#bloc-long').attr('data-etat', 'erreur');
      }
    }).always(function () {
      if (lancement === bloc) {
        $('#bloc-long-spinner').hide();
      }
    });
  }

  $('#btn-bloc-recharger').on('click', chargerBloc);

  // --- Bouton activé 3,5 s après l'arrivée ---
  function activerBoutonTardif() {
    App.api('GET', 'slow?delay=3500').done(function () {
      $('#btn-active-tard').prop('disabled', false);
      // Instant d'activation, en ms depuis le début de la navigation vers la page
      $('#bouton-tardif-etat').text('activé').attr('data-active-ms', Math.round(performance.now()));
    }).fail(function (jqXHR) {
      $('#bouton-tardif-etat').text('activation impossible : ' + App.errorMessage(jqXHR));
    });
  }

  $('#btn-active-tard').on('click', function () {
    App.showResult('#bouton-tardif-resultat', 'Action différée exécutée', false);
  });

  // --- Réponses au compte-gouttes ---
  var goutte = 0;

  $('#btn-goutte-flux').on('click', function () {
    var lancement = ++goutte;
    var recus = 0;
    $('#goutte-list').empty();
    App.showResult('#goutte-statut', 'En attente du premier élément…', false);
    $('#goutte-statut').attr('data-etat', 'en-cours');
    fetch(App.apiUrl('latence/goutte?nombre=10&intervalle=500&format=ndjson'), { cache: 'no-store' }).then(function (reponse) {
      if (!reponse.ok) {
        throw new Error('Erreur HTTP ' + reponse.status);
      }
      var lecteur = reponse.body.getReader();
      var decodeur = new TextDecoder();
      var reste = '';

      function lire() {
        return lecteur.read().then(function (morceau) {
          if (lancement !== goutte) {
            lecteur.cancel();
            return undefined;
          }
          if (morceau.done) {
            App.showResult('#goutte-statut', recus + ' / 10 éléments reçus, flux terminé', false);
            $('#goutte-statut').attr('data-etat', 'termine');
            return undefined;
          }
          reste += decodeur.decode(morceau.value, { stream: true });
          var lignes = reste.split('\n');
          reste = lignes.pop();
          lignes.forEach(function (ligne) {
            if (ligne.trim()) {
              var element = JSON.parse(ligne);
              recus++;
              $('#goutte-list').append($('<li>', { id: 'goutte-item-' + element.rang, text: element.texte }));
            }
          });
          App.showResult('#goutte-statut', recus + ' / 10 éléments reçus', false);
          return lire();
        });
      }
      return lire();
    }).catch(function (erreur) {
      if (lancement === goutte) {
        App.showResult('#goutte-statut', 'Flux interrompu : ' + (erreur.message || erreur), true);
        $('#goutte-statut').attr('data-etat', 'erreur');
      }
    }).then(App.rafraichirTirages);
  });

  $('#btn-goutte-json').on('click', function () {
    App.showResult('#goutte-json-statut', 'En attente de la réponse complète…', false);
    $('#goutte-json-statut').attr('data-etat', 'en-cours');
    App.api('GET', 'latence/goutte?nombre=10&intervalle=500&format=json').done(function (elements) {
      App.showResult('#goutte-json-statut', 'Réponse complète : ' + elements.length + ' éléments', false);
      $('#goutte-json-statut').attr('data-etat', 'termine');
    }).fail(function (jqXHR) {
      App.showResult('#goutte-json-statut', App.errorMessage(jqXHR), true);
      $('#goutte-json-statut').attr('data-etat', 'erreur');
    });
  });

  // --- Tableau rafraîchi automatiquement toutes les 4 s ---
  var minuterie = null;
  var rafraichissements = 0;
  var requeteTableau = 0;
  var dernierLancement = null;

  function rafraichirTableau() {
    var numero = ++requeteTableau;
    // Intervalle entre deux lancements, mesuré dans la page (arrondi au-dessus : l'horloge du navigateur est grossière)
    var maintenant = performance.now();
    if (dernierLancement !== null) {
      $('#auto-refresh-count').attr('data-intervalle-ms', Math.ceil(maintenant - dernierLancement));
    }
    dernierLancement = maintenant;
    App.api('GET', 'items?page=0&size=5&sort=id&dir=asc').done(function (data) {
      if (numero !== requeteTableau) {
        return; // réponse obsolète
      }
      rafraichissements++;
      var $corps = $('#tableau-auto-body').empty();
      data.items.forEach(function (item) {
        $('<tr>', { id: 'auto-row-' + item.id }).append(
          $('<td>', { text: item.id }),
          $('<td>', { text: item.name }),
          $('<td>').append($('<button>', {
            type: 'button', id: 'auto-select-' + item.id, 'class': 'auto-select', 'data-id': item.id, text: 'Sélectionner'
          }))
        ).appendTo($corps);
      });
      $('#auto-refresh-count').attr('data-count', rafraichissements).text('Rafraîchissements : ' + rafraichissements);
    });
  }

  function reprendre() {
    if (minuterie === null) {
      rafraichirTableau();
      minuterie = setInterval(rafraichirTableau, 4000);
    }
  }

  $('#btn-auto-start').on('click', reprendre);
  $('#btn-auto-stop').on('click', function () {
    clearInterval(minuterie);
    minuterie = null;
    dernierLancement = null;
  });
  $('#tableau-auto-body').on('click', '.auto-select', function () {
    App.showResult('#auto-resultat', 'Article ' + $(this).data('id') + ' sélectionné (rafraîchissement n°' + rafraichissements + ')', false);
  });

  // --- Style tardif : le bouton qu'il révèle ---
  $('#btn-css-tardif').on('click', function () {
    App.showResult('#css-tardif-resultat', 'Bouton révélé par le style tardif cliqué', false);
  });

  // --- Script et style tardifs, insérés après le chargement complet de la page ---
  $(window).on('load', function () {
    setTimeout(function () {
      var style = document.createElement('link');
      style.rel = 'stylesheet';
      style.href = 'lent/latence-tardif.css?delai=' + delaiRessources;
      style.onload = function () {
        // Instant d'arrivée, en ms depuis le début de la navigation vers la page
        $('#etat-css').text('Style chargé').attr('data-arrivee-ms', Math.round(performance.now()));
      };
      document.head.appendChild(style);
      var script = document.createElement('script');
      script.src = 'lent/latence-tardif.js?delai=' + delaiRessources;
      document.body.appendChild(script);
    }, 0);
  });

  // --- Démarrage : profil demandé dans l'URL d'abord, puis les chargements de la page ---
  var profilUrl = params.get('profil');
  var pret = profilUrl
    ? appliquerProfil(profilUrl, params.get('graine'))
    : App.api('GET', 'admin/profil').done(afficherProfil);
  pret.always(function () {
    chargerBloc();
    activerBoutonTardif();
    reprendre();
  });
})(jQuery);
