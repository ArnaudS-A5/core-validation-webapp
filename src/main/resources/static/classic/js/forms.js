(function ($) {
  'use strict';

  // --- Curseur ---
  $('#input-range').on('input change', function () {
    $('#range-value').text(this.value);
  });

  // --- Cases à cocher ---
  var $options = $('.chk-option');
  function syncSelectAll() {
    var checked = $options.filter(':checked').length;
    var $all = $('#chk-all');
    $all.prop('checked', checked === $options.length);
    $all.prop('indeterminate', checked > 0 && checked < $options.length);
  }
  $('#chk-all').on('change', function () {
    $options.prop('checked', this.checked);
    $(this).prop('indeterminate', false);
  });
  $options.on('change', syncSelectAll);

  // --- Pays / villes (chargés par le serveur) ---
  App.api('GET', 'countries').done(function (countries) {
    var $select = $('#select-country').empty().append($('<option>').val('').text('— Choisir un pays —'));
    $.each(countries, function (_, country) {
      $select.append($('<option>').val(country.code).text(country.name));
    });
  }).fail(function (jqXHR) {
    $('#select-country').empty().append($('<option>').val('').text(App.errorMessage(jqXHR)));
  });

  $('#select-country').on('change', function () {
    var code = $(this).val();
    var $city = $('#select-city');
    if (!code) {
      $city.prop('disabled', true).empty().append($('<option>').val('').text('Choisir d\'abord un pays'));
      return;
    }
    $city.prop('disabled', true).empty().append($('<option>').val('').text('Chargement…'));
    App.api('GET', 'countries/' + encodeURIComponent(code) + '/cities').done(function (cities) {
      $city.empty().append($('<option>').val('').text('— Choisir une ville —'));
      $.each(cities, function (_, city) {
        $city.append($('<option>').val(city).text(city));
      });
      $city.prop('disabled', false);
    });
  });

  // --- Soumission ---
  function collectPayload() {
    return {
      name: $('#input-name').val(),
      email: $('#input-email').val(),
      quantity: Number($('#input-number').val()),
      date: $('#input-date').val(),
      range: Number($('#input-range').val()),
      comment: $('#textarea-comment').val(),
      civility: $('input[name=civility]:checked').val() || null,
      plan: $('input[name=plan]:checked').val() || null,
      options: $('.chk-option:checked').map(function () { return this.value; }).get(),
      country: $('#select-country').val() || null,
      city: $('#select-city').val() || null,
      frequency: $('#select-static').val() || null,
      interests: $('#select-multiple').val() || []
    };
  }

  $('#demo-form').on('submit', function (event) {
    event.preventDefault();
    $('#error-name, #error-email').text('');
    App.showResult('#form-result', '', false);
    $('#form-received').text('');
    $('#form-submit').prop('disabled', true);
    $('#form-spinner').show();

    App.api('POST', 'forms/submit', collectPayload())
      .done(function (data) {
        App.showResult('#form-result', data.message, false);
        $('#form-received').text(JSON.stringify(data.received, null, 2));
      })
      .fail(function (jqXHR) {
        var errors = jqXHR.responseJSON && jqXHR.responseJSON.errors;
        if (errors) {
          $('#error-name').text(errors.name || '');
          $('#error-email').text(errors.email || '');
          App.showResult('#form-result', 'Le formulaire contient des erreurs', true);
        } else {
          App.showResult('#form-result', App.errorMessage(jqXHR), true);
        }
      })
      .always(function () {
        $('#form-spinner').hide();
        $('#form-submit').prop('disabled', false);
      });
  });

  $('#demo-form').on('reset', function () {
    setTimeout(function () {
      $('#range-value').text($('#input-range').val());
      $('#select-city').prop('disabled', true).empty().append($('<option>').val('').text('Choisir d\'abord un pays'));
      syncSelectAll();
      $('#error-name, #error-email').text('');
      App.showResult('#form-result', '', false);
      $('#form-received').text('');
    }, 0);
  });
})(jQuery);
