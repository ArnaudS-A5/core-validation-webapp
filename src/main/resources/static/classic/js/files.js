(function ($) {
  'use strict';

  // --- Téléchargement par script : récupération d'un blob puis clic sur un lien temporaire ---
  $('#btn-download-ajax').on('click', function () {
    var $button = $(this).prop('disabled', true);
    $('#download-spinner').show();
    App.showResult('#download-status', '', false);
    $.ajax({
      url: App.apiUrl('files/download/csv?delay=1500'),
      method: 'GET',
      xhrFields: { responseType: 'blob' }
    }).done(function (blob) {
      var link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = 'export-script.csv';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(link.href);
      App.showResult('#download-status', 'Téléchargement terminé (' + blob.size + ' octets)', false);
    }).fail(function (jqXHR) {
      App.showResult('#download-status', App.errorMessage(jqXHR), true);
    }).always(function () {
      $('#download-spinner').hide();
      $button.prop('disabled', false);
    });
  });

  // --- Envoi de fichier ---
  function upload(file, resultSelector, progressSelector, spinnerSelector) {
    var formData = new FormData();
    formData.append('file', file);
    App.showResult(resultSelector, '', false);
    if (spinnerSelector) { $(spinnerSelector).show(); }
    if (progressSelector) { setProgress(progressSelector, 0); }

    return $.ajax({
      url: App.apiUrl('files/upload'),
      method: 'POST',
      data: formData,
      processData: false,
      contentType: false,
      dataType: 'json',
      xhr: function () {
        var xhr = $.ajaxSettings.xhr();
        if (xhr.upload && progressSelector) {
          xhr.upload.addEventListener('progress', function (event) {
            if (event.lengthComputable) {
              setProgress(progressSelector, Math.round(event.loaded * 100 / event.total));
            }
          });
        }
        return xhr;
      }
    }).done(function (data) {
      if (progressSelector) { setProgress(progressSelector, 100); }
      App.showResult(resultSelector, 'Fichier « ' + data.name + ' » reçu (' + data.size + ' octets, SHA-256 ' + data.sha256.substring(0, 12) + '…)', false);
    }).fail(function (jqXHR) {
      App.showResult(resultSelector, App.errorMessage(jqXHR), true);
    }).always(function () {
      if (spinnerSelector) { $(spinnerSelector).hide(); }
    });
  }

  function setProgress(selector, percent) {
    $(selector).css('width', percent + '%').attr('aria-valuenow', percent);
  }

  $('#btn-upload').on('click', function () {
    var files = $('#upload-input')[0].files;
    if (!files.length) {
      App.showResult('#upload-result', 'Aucun fichier sélectionné', true);
      return;
    }
    var $button = $(this).prop('disabled', true);
    upload(files[0], '#upload-result', '#upload-progress', '#upload-spinner').always(function () {
      $button.prop('disabled', false);
    });
  });

  // --- Champ masqué : le bouton déclenche le champ ; l'envoi part dès la sélection ---
  $('#btn-choose-file').on('click', function () {
    $('#upload-hidden-input').trigger('click');
  });
  $('#upload-hidden-input').on('change', function () {
    var file = this.files[0];
    if (!file) { return; }
    $('#hidden-file-name').text(file.name);
    upload(file, '#hidden-upload-result');
  });

  // --- Zone de dépôt ---
  var $zone = $('#drop-zone');
  $zone.on('dragover dragenter', function (event) {
    event.preventDefault();
    $zone.addClass('over');
  });
  $zone.on('dragleave drop', function () {
    $zone.removeClass('over');
  });
  $zone.on('drop', function (event) {
    event.preventDefault();
    var files = event.originalEvent.dataTransfer.files;
    if (files.length) {
      upload(files[0], '#drop-result');
    }
  });
})(jQuery);
