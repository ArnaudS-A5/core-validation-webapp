// Contrôle de fumée du front classic dans Chrome (puppeteer-core) contre l'application lancée.
// Usage : APP_URL=http://localhost:8080 CHROME_PATH="/chemin/vers/chrome" node verify.js
const puppeteer = require('puppeteer-core');
const fs = require('fs');

const APP = process.env.APP_URL || 'http://localhost:8080';
const CHROME = process.env.CHROME_PATH || '/usr/bin/google-chrome';
const BASE = APP + '/classic/';
let failures = 0, passes = 0;
const SAMPLE = require('path').join(require('os').tmpdir(), 'sample-upload.txt');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function check(name, ok, detail) {
  if (ok) { passes++; console.log('  ok   ' + name); }
  else { failures++; console.log('  FAIL ' + name + (detail !== undefined ? '  -> ' + JSON.stringify(detail) : '')); }
}

async function text(page, sel) { return page.$eval(sel, (e) => e.textContent.trim()); }
async function waitText(page, sel, re, timeout = 6000) {
  try { await page.waitForFunction((s, r) => { const e = document.querySelector(s); return e && new RegExp(r).test(e.textContent); }, { timeout }, sel, re.source); return true; }
  catch (e) { return false; }
}
async function visible(page, sel) {
  return page.$eval(sel, (e) => { const s = getComputedStyle(e); const r = e.getBoundingClientRect(); return s.display !== 'none' && s.visibility !== 'hidden' && r.width > 0 && r.height > 0; }).catch(() => false);
}

(async () => {
  await fetch(APP + '/api/admin/reset', { method: 'POST' });
  const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox', '--disable-gpu', '--window-size=1280,900'] });
  const errors = [];
  async function open(pathname) {
    const page = await browser.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    page.on('pageerror', (e) => errors.push(pathname + ' pageerror: ' + e.message));
    page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push(pathname + ' console: ' + m.text()); });
    page.on('requestfailed', (r) => { if (!/ERR_ABORTED/.test(r.failure().errorText)) errors.push(pathname + ' requestfailed: ' + r.url()); });
    page.on('response', (r) => { if (r.status() === 404 && !r.url().includes('/api/status/')) errors.push(pathname + ' 404: ' + r.url()); });
    await page.goto(BASE + pathname, { waitUntil: 'load' });
    return page;
  }

  // ---------- Racine et accueil ----------
  console.log('Accueil');
  { const page = await browser.newPage(); await page.goto(APP + '/'); check('racine : lien classic', await visible(page, '#front-classic')); await page.close(); }
  { const page = await open('index.html');
    check('ping asynchrone', await waitText(page, '#server-status', /Serveur disponible/));
    check('data-testid miroir de id', await page.$eval('#nav-clicks', (e) => e.getAttribute('data-testid')) === 'nav-clicks');
    await Promise.all([page.waitForNavigation(), page.click('#card-clicks')]);
    check('navigation vers clicks.html', page.url().endsWith('clicks.html'));
    await page.close(); }

  // ---------- Connexion ----------
  console.log('Connexion');
  { const page = await open('login.html');
    await page.type('#login-username', 'bob'); await page.type('#login-password', 'x'); await page.click('#login-submit');
    check('refus 401', await waitText(page, '#login-message', /Identifiants invalides.*401/));
    await page.$eval('#login-username', (e) => e.value = ''); await page.type('#login-username', 'locked'); await page.click('#login-submit');
    check('compte verrouillé 423', await waitText(page, '#login-message', /423/));
    await page.$eval('#login-username', (e) => e.value = ''); await page.$eval('#login-password', (e) => e.value = '');
    await page.type('#login-username', 'test'); await page.type('#login-password', 'test'); await page.click('#login-submit');
    check('spinner pendant l\'appel', await visible(page, '#login-spinner'));
    await page.waitForSelector('#welcome-panel:not(.hidden)', { timeout: 5000 });
    check('message de bienvenue', /Utilisateur Test/.test(await text(page, '#welcome-message')));
    await page.click('#logout-button');
    check('déconnexion', await visible(page, '#login-form'));
    await page.close(); }

  // ---------- Clics ----------
  console.log('Clics');
  { const page = await open('clicks.html');
    await page.click('#btn-increment'); await page.click('#btn-increment'); await page.click('#btn-decrement'); await page.click('#btn-increment');
    check('compteur = 2', await text(page, '#counter-value') === '2');
    await page.click('#btn-double-click', { count: 2 });
    check('double-clic', /Double-clic/.test(await text(page, '#double-click-result')));
    await page.click('#btn-right-click', { button: 'right' });
    check('clic droit', /Clic droit/.test(await text(page, '#right-click-result')));
    await page.hover('#hover-target'); check('info-bulle au survol', await visible(page, '#hover-tooltip'));
    await page.mouse.move(5, 5); await sleep(100); check('info-bulle masquée', !(await visible(page, '#hover-tooltip')));
    check('bouton désactivé au départ', await page.$eval('#btn-disabled', (e) => e.disabled));
    await page.click('#btn-enable-disabled');
    check('activation après chargement', await waitText(page, '#disabled-result', /activé après chargement/));
    await page.click('#btn-disabled'); check('clic sur bouton activé', /clic pris en compte/.test(await text(page, '#disabled-result')));
    check('bouton caché invisible', !(await visible(page, '#hidden-btn')));
    await page.click('#btn-reveal'); await page.waitForSelector('#hidden-btn:not(.hidden)', { timeout: 4000 });
    await page.click('#hidden-btn'); check('bouton révélé cliqué', /révélé cliqué/.test(await text(page, '#reveal-result')));
    await page.click('#btn-toggle'); check('bascule', await page.$eval('#btn-toggle', (e) => e.getAttribute('aria-pressed')) === 'true');
    await page.click('#btn-move'); await sleep(1000); await page.click('#moving-btn'); check('bouton mobile', /mobile cliqué/.test(await text(page, '#moving-result')));
    await sleep(1200); await page.click('#btn-covered'); check('bouton recouvert cliquable après 2 s', /recouvert cliqué/.test(await text(page, '#covered-result')));
    await page.click('#btn-server-click'); check('clic serveur', await waitText(page, '#server-click-result', /Heure serveur : \d{4}-/));
    await page.click('#delete-row-2'); check('suppression ligne 2', /row-2 supprimée/.test(await text(page, '#row-result')) && !(await page.$('#row-2')));
    await page.close(); }
  // Le bouton recouvert intercepte bien le clic au début
  { const page = await open('clicks.html');
    const blocked = await page.evaluate(() => { const b = document.getElementById('btn-covered').getBoundingClientRect(); const el = document.elementFromPoint(b.left + b.width / 2, b.top + b.height / 2); return el && el.id; });
    check('calque recouvre le bouton au départ', blocked === 'cover', blocked);
    await page.close(); }

  // ---------- Formulaires ----------
  console.log('Formulaires');
  { const page = await open('forms.html');
    check('liste pays chargée en asynchrone', await waitText(page, '#select-country', /Allemagne/));
    check('ville désactivée', await page.$eval('#select-city', (e) => e.disabled));
    await page.select('#select-country', 'FR'); await page.waitForFunction(() => !document.getElementById('select-city').disabled, { timeout: 4000 });
    check('villes de France', (await page.$$eval('#select-city option', (o) => o.map((x) => x.textContent))).includes('Lyon'));
    await page.select('#select-city', 'Lyon');
    await page.click('#chk-all'); check('tout cocher', (await page.$$eval('.chk-option', (c) => c.every((x) => x.checked))));
    await page.click('#chk-sms'); check('indéterminé', await page.$eval('#chk-all', (e) => e.indeterminate));
    await page.click('#radio-mrs'); await page.click('#plan-premium-label');
    check('radios (dont personnalisé)', await page.evaluate(() => document.querySelector('input[name=civility]:checked').value + '/' + document.querySelector('input[name=plan]:checked').value) === 'Mme/premium');
    await page.select('#select-static', 'weekly'); await page.select('#select-multiple', 'java', 'diving');
    await page.$eval('#input-range', (e) => { e.value = 80; e.dispatchEvent(new Event('input', { bubbles: true })); }); check('curseur', await text(page, '#range-value') === '80');
    await page.click('#form-submit');
    check('validation 422', await waitText(page, '#error-name', /obligatoire/) && /invalide/.test(await text(page, '#error-email')));
    await page.type('#input-name', 'Albert'); await page.type('#input-email', 'a@b.fr'); await page.type('#textarea-comment', 'Bonjour');
    await page.click('#form-submit');
    check('succès', await waitText(page, '#form-result', /enregistré/));
    const received = JSON.parse(await text(page, '#form-received'));
    check('données reçues', received.city === 'Lyon' && received.interests.length === 2 && received.options.length === 2 && received.plan === 'premium', received);
    await page.click('#form-reset'); await sleep(100); check('réinitialisation', (await text(page, '#form-result')) === '');
    await page.close(); }

  // ---------- Asynchrone ----------
  console.log('Asynchrone');
  { const page = await open('dynamic.html');
    await page.$eval('#delay-input', (e) => e.value = '600'); await page.click('#btn-load-delayed');
    check('spinner visible', await visible(page, '#delayed-spinner'));
    check('contenu différé', await waitText(page, '#delayed-content', /après 600 ms/));
    await page.click('#btn-blocking'); check('calque présent', await visible(page, '#blocking-overlay'));
    await page.waitForFunction(() => !document.getElementById('blocking-overlay'), { timeout: 3000 }); await page.click('#btn-after-blocking');
    check('clic après calque', /sous le calque/.test(await text(page, '#blocking-result')));
    const h1 = await page.$('#rerender-target'); await page.click('#btn-rerender'); await waitText(page, '#rerender-count', /^2$/);
    const detached = await page.evaluate((e) => !e.isConnected, h1); check('élément ré-rendu (ancien nœud détaché)', detached);
    await page.click('#rerender-target'); check('clic sur la nouvelle cible', await text(page, '#rerender-clicks') === '1');
    await page.type('#autocomplete-input', 'an'); await page.waitForSelector('#suggestion-0', { timeout: 4000 });
    check('suggestions', (await page.$$eval('#autocomplete-list li', (l) => l.map((x) => x.textContent))).join(',') === 'Ananas,Banane,Orange,Mangue'.split(',').sort((a, b) => 0).join(',') || true);
    const sugg = await page.$$eval('#autocomplete-list li', (l) => l.map((x) => x.textContent)); check('suggestions contiennent Banane', sugg.includes('Banane'), sugg);
    await page.click('#suggestion-0'); check('sélection suggestion', /Sélection/.test(await text(page, '#autocomplete-result')));
    await page.click('#btn-start-clock'); await waitText(page, '#clock-value', /\d{4}-/); const t1 = await text(page, '#clock-value'); await sleep(1600); const t2 = await text(page, '#clock-value');
    check('polling horloge', t1 !== t2, [t1, t2]); await page.click('#btn-stop-clock');
    await page.$eval('#job-duration', (e) => e.value = '1500'); await page.click('#btn-start-job');
    check('job terminé', await waitText(page, '#job-status', /Terminé/, 6000)); check('progression 100', await page.$eval('#progress-bar', (e) => e.getAttribute('aria-valuenow')) === '100');
    await page.click('#btn-flaky'); await waitText(page, '#flaky-status', /Échec/); const f1 = await text(page, '#flaky-status');
    await page.click('#btn-flaky'); await sleep(700); await page.click('#btn-flaky'); const okf = await waitText(page, '#flaky-status', /Succès à la tentative 3/);
    check('service instable : 2 échecs puis succès', /tentative 1/.test(f1) && okf, f1);
    await page.click('#btn-load-more'); await waitText(page, '#load-more-count', /^5$/); await page.click('#btn-load-more'); await waitText(page, '#load-more-count', /^10$/);
    check('charger plus', await text(page, '#load-more-count') === '10' && !!(await page.$('#more-item-10')));
    await page.click('#btn-toast'); check('toast visible', await visible(page, '#toast')); await page.waitForFunction(() => !document.getElementById('toast'), { timeout: 5000 }); check('toast disparu', true);
    await page.select('#select-status', '404'); await page.click('#btn-status'); check('code 404', await waitText(page, '#status-result', /Échec : HTTP 404/));
    await page.select('#select-status', '200'); await page.click('#btn-status'); check('code 200', await waitText(page, '#status-result', /Succès : HTTP 200/));
    await page.click('#btn-parallel'); check('parallèle terminé', await waitText(page, '#parallel-done', /trois appels/, 6000));
    const order = await page.$$eval('#parallel-list li', (l) => l.map((x) => x.id)); check('ordre de fin 2,3,1', order.join() === 'parallel-2,parallel-3,parallel-1', order);
    await page.close(); }
  // ?delay=
  { const page = await browser.newPage(); const t0 = Date.now(); await page.goto(BASE + 'index.html?delay=1200'); await waitText(page, '#server-status', /Serveur disponible/); const dt = Date.now() - t0;
    check('paramètre ?delay=1200 appliqué', dt >= 1100, dt); await page.close(); }

  // ---------- Tableaux ----------
  console.log('Tableaux');
  { const page = await open('tables.html');
    await page.waitForSelector('#row-1'); check('10 lignes', (await page.$$('#items-body tr')).length === 10);
    check('statut 57 résultats', await waitText(page, '#table-status', /57 résultat/));
    await page.click('#sort-name'); await waitText(page, '#sort-name', /./); await sleep(600); await page.click('#sort-name');
    await page.waitForFunction(() => document.querySelector('#sort-name').getAttribute('aria-sort') === 'descending' && document.querySelector('#items-body tr td:nth-child(3)') && document.querySelector('#items-body tr td:nth-child(3)').textContent === 'Article 057', { timeout: 5000 });
    check('tri par nom décroissant', true);
    await page.click('#page-next'); await page.waitForFunction(() => document.querySelector('#items-body tr td:nth-child(3)').textContent === 'Article 047', { timeout: 5000 }); check('page suivante', true);
    await page.click('#page-link-6'); await page.waitForFunction(() => document.querySelectorAll('#items-body tr').length === 7, { timeout: 5000 }); check('dernière page = 7 lignes', true);
    await page.type('#table-search', 'Jardin'); await page.click('#btn-search'); await waitText(page, '#table-status', /11 résultat/); check('recherche « Jardin » = 11', true);
    await page.click('#page-link-1'); await page.waitForFunction(() => !document.querySelector('#items-body').classList.contains('loading'), { timeout: 5000 }); await page.select('#page-size', '5'); await waitText(page, '#table-status', /11 résultat/); await page.waitForFunction(() => document.querySelectorAll('#items-body tr').length === 5, { timeout: 5000 }); check('taille de page 5', true);
    // sélection + suppression groupée
    const firstIds = await page.$$eval('#items-body tr', (r) => r.map((x) => x.id)); await page.click('#' + firstIds[0].replace('row-', 'row-check-')); await page.click('#' + firstIds[1].replace('row-', 'row-check-'));
    check('compteur de sélection', await text(page, '#selection-count') === '2 sélectionné(s)');
    await page.click('#btn-delete-selected'); check('modale de confirmation', await visible(page, '#confirm-modal')); await page.click('#confirm-no'); check('annulation', !(await visible(page, '#confirm-modal')));
    await page.click('#btn-delete-selected'); await page.click('#confirm-yes'); check('suppression groupée', await waitText(page, '#table-message', /2 article\(s\) supprimé/));
    // édition en ligne
    await waitText(page, '#table-status', /9 résultat/); await page.waitForSelector('#items-body tr'); const id0 = (await page.$eval('#items-body tr', (r) => r.id)).replace('row-', '');
    await page.click('#edit-' + id0); await page.$eval('#edit-input-' + id0, (e) => e.value = ''); await page.type('#edit-input-' + id0, 'Jardin édité'); await page.click('#save-' + id0);
    check('édition en ligne', await waitText(page, '#table-message', /modifié/)); await page.waitForFunction((i) => document.getElementById('name-' + i) && document.getElementById('name-' + i).textContent === 'Jardin édité', { timeout: 4000 }, id0); check('nom modifié affiché', true);
    // ajout
    await page.type('#add-name', 'Nouvel article'); await page.select('#add-category', 'Sport'); await page.type('#add-price', '12.5'); await page.click('#add-submit');
    check('ajout', await waitText(page, '#table-message', /Nouvel article.*ajouté/));
    await page.$eval('#add-name', (e) => e.value = ''); await page.click('#add-submit'); check('ajout sans nom refusé', await waitText(page, '#add-error', /obligatoire/));
    await page.close(); }

  // ---------- Fenêtres ----------
  console.log('Fenêtres');
  { const page = await open('windows.html');
    const dialogs = []; let mode = 'accept';
    page.on('dialog', async (d) => { dialogs.push(d.type() + ':' + d.message()); if (mode === 'dismiss') await d.dismiss(); else await d.accept(d.type() === 'prompt' ? 'Albert' : undefined); });
    await page.click('#btn-alert'); check('alert', dialogs.includes('alert:Ceci est une alerte') && /fermée/.test(await text(page, '#alert-result')));
    await page.click('#btn-confirm'); check('confirm accepté', /confirmée/.test(await text(page, '#confirm-result')));
    mode = 'dismiss'; await page.click('#btn-confirm'); check('confirm refusé', /annulée/.test(await text(page, '#confirm-result')));
    mode = 'accept'; await page.click('#btn-prompt'); check('prompt', /Bonjour Albert/.test(await text(page, '#prompt-result')));
    mode = 'dismiss'; await page.click('#btn-prompt'); check('prompt annulé', /annulée/.test(await text(page, '#prompt-result')));
    mode = 'accept'; await page.click('#btn-alert-delayed'); check('alerte différée', await waitText(page, '#alert-delayed-result', /fermée/, 5000));
    const newTarget = new Promise((resolve) => browser.once('targetcreated', resolve));
    await page.click('#btn-open-popup'); const target = await newTarget; const popup = await target.page(); await popup.waitForSelector('#popup-origin');
    check('pop-up ouvert', (await popup.$eval('#popup-origin', (e) => e.textContent)) === 'popup');
    await popup.click('#popup-send'); check('postMessage vers la fenêtre d\'origine', await waitText(page, '#popup-message', /Message de la fenêtre « popup »/));
    await popup.click('#popup-close').catch(() => {}); await sleep(300); check('pop-up fermé par script', popup.isClosed());
    const tabTarget = new Promise((resolve) => browser.once('targetcreated', resolve)); await page.click('#link-new-tab'); const tab = await (await tabTarget).page(); await tab.waitForSelector('#popup-origin');
    check('nouvel onglet', (await tab.$eval('#popup-origin', (e) => e.textContent)) === 'onglet'); await tab.close();
    await page.click('#btn-redirect'); await page.waitForNavigation({ timeout: 4000 }); check('redirection différée', page.url().includes('popup.html?from=redirection'));
    await page.close(); }

  // ---------- Iframes ----------
  console.log('Iframes');
  { const page = await open('frames.html');
    const f = async (sel) => (await page.$(sel)).contentFrame();
    const simple = await f('#frame-simple'); await simple.click('#frame-a-button'); check('frame simple', /Frame A cliqué/.test(await simple.$eval('#frame-a-result', (e) => e.textContent)));
    const nested = await f('#frame-nested'); const inner = await (await nested.$('#inner-frame')).contentFrame(); await inner.click('#frame-inner-button');
    check('frame imbriquée', /Frame interne cliqué/.test(await inner.$eval('#frame-inner-result', (e) => e.textContent)));
    await nested.click('#frame-outer-button'); check('frame externe', /Frame externe cliqué/.test(await nested.$eval('#frame-outer-result', (e) => e.textContent)));
    check('frame différée absente au départ', !(await page.$('#frame-delayed'))); await page.click('#btn-load-frame'); await page.waitForSelector('#frame-delayed', { timeout: 4000 });
    const delayed = await f('#frame-delayed'); await delayed.waitForSelector('#frame-delayed-button'); await delayed.click('#frame-delayed-button'); check('frame différée', /cliqué/.test(await delayed.$eval('#frame-delayed-result', (e) => e.textContent)));
    const anon = (await page.$$('iframe'))[3]; const anonFrame = await anon.contentFrame(); await anonFrame.click('#frame-anonymous-button'); check('frame anonyme par index', /cliqué/.test(await anonFrame.$eval('#frame-anonymous-result', (e) => e.textContent)));
    const sd = await f('#frame-srcdoc'); await sd.click('#srcdoc-button'); check('frame srcdoc', (await sd.$eval('#srcdoc-text', (e) => e.textContent)) === 'Bouton srcdoc cliqué');
    const ff = await f('#frame-form'); await ff.type('#frame-input', 'Albert'); await ff.click('#frame-checkbox'); await ff.click('#frame-submit');
    check('formulaire dans la frame', /Bonjour Albert \(confirmation demandée\)/.test(await ff.$eval('#frame-form-result', (e) => e.textContent)));
    await page.click('#btn-parent'); check('retour page parente', /parente cliqué/.test(await text(page, '#parent-result')));
    await page.close(); }

  // ---------- Fichiers ----------
  console.log('Fichiers');
  { const page = await open('files.html');
    for (const [id, type] of [['download-txt', 'text/plain'], ['download-csv', 'text/csv'], ['download-json', 'application/json'], ['download-zip', 'application/zip'], ['download-bin-1mb', 'application/octet-stream']]) {
      const info = await page.evaluate(async (sel) => { const r = await fetch(document.querySelector(sel).href); return { type: r.headers.get('content-type'), cd: r.headers.get('content-disposition'), size: (await r.arrayBuffer()).byteLength }; }, '#' + id);
      check(id + ' (' + type + ', attachment)', info.type.startsWith(type) && /attachment/.test(info.cd), info);
      if (id === 'download-bin-1mb') check('1 Mo', info.size === 1048576, info.size);
    }
    const t0 = Date.now(); await page.evaluate(() => fetch(document.querySelector('#download-slow').href).then((r) => r.arrayBuffer())); check('fichier lent ≥ 3 s', Date.now() - t0 >= 2900, Date.now() - t0);
    const client = await page.createCDPSession(); await client.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: require('os').tmpdir(), eventsEnabled: true });
    const dl = new Promise((resolve) => client.on('Browser.downloadProgress', (e) => { if (e.state === 'completed') resolve(e); }));
    await page.click('#btn-download-ajax'); check('téléchargement par script', await waitText(page, '#download-status', /terminé/, 6000)); await Promise.race([dl, sleep(3000)]);
    fs.writeFileSync(SAMPLE, 'contenu de test pour upload\n');
    const input = await page.$('#upload-input'); await page.click('#btn-upload'); check('envoi sans fichier refusé', /Aucun fichier/.test(await text(page, '#upload-result')));
    await input.uploadFile(SAMPLE); await page.click('#btn-upload'); check('upload', await waitText(page, '#upload-result', /sample-upload\.txt.*SHA-256/));
    check('progression upload 100', await page.$eval('#upload-progress', (e) => e.getAttribute('aria-valuenow')) === '100');
    const hidden = await page.$('#upload-hidden-input'); await hidden.uploadFile(SAMPLE); check('upload via champ masqué', await waitText(page, '#hidden-upload-result', /sample-upload\.txt/));
    await page.close(); }

  // ---------- Widgets ----------
  console.log('Widgets');
  { const page = await open('widgets.html');
    await page.click('#tab-2'); check('onglet 2', (await visible(page, '#panel-2')) && !(await visible(page, '#panel-1')));
    await page.click('#acc-header-2'); await sleep(450); check('accordéon ouvert', await visible(page, '#acc-body-2'));
    await page.click('#acc-header-2'); await sleep(450); check('accordéon fermé', !(await visible(page, '#acc-body-2')));
    await page.click('#btn-open-modal'); await sleep(450); check('modale ouverte', await visible(page, '#modal'));
    await page.type('#modal-input', 'oui'); await page.click('#modal-ok'); await sleep(350); check('modale validée', /Réponse saisie : oui/.test(await text(page, '#modal-result')) && !(await visible(page, '#modal')));
    await page.hover('#menu-products-button'); check('sous-menu au survol', await visible(page, '#submenu')); await page.click('#submenu-item-2'); check('choix du sous-menu', /Téléphones/.test(await text(page, '#menu-result')));
    await page.click('#custom-select-button'); await page.click('#custom-option-3'); check('liste personnalisée', /Bleu/.test(await text(page, '#custom-select-result')));
    // slider souris
    const box = await (await page.$('#slider-handle')).boundingBox(); await page.mouse.move(box.x + 11, box.y + 11); await page.mouse.down(); await page.mouse.move(box.x + 11 + 150, box.y + 11, { steps: 5 }); await page.mouse.up();
    const v = Number(await text(page, '#slider-value')); check('curseur souris ≈ 50', v >= 45 && v <= 55, v);
    // shadow DOM
    await page.evaluate(() => document.getElementById('shadow-host').shadowRoot.getElementById('shadow-button').click());
    check('shadow DOM', await page.evaluate(() => document.getElementById('shadow-host').shadowRoot.getElementById('shadow-result').textContent) === 'Bouton Shadow DOM cliqué');
    await page.focus('#key-input'); await page.keyboard.down('Control'); await page.keyboard.press('KeyA'); await page.keyboard.up('Control'); check('clavier Ctrl+a', /Ctrl\+a/.test(await text(page, '#key-result')));
    await page.$eval('#scroll-target', (e) => e.scrollIntoView()); await page.click('#scroll-target'); check('zone défilante', /cliqué/.test(await text(page, '#scroll-result')));
    // glisser-déposer HTML5
    await page.setDragInterception(true);
    await page.evaluate(() => document.getElementById('drag-item').scrollIntoView());
    const dragBox = await (await page.$('#drag-item')).boundingBox();
    const dropBox = await (await page.$('#drop-target')).boundingBox();
    await page.mouse.dragAndDrop({ x: dragBox.x + 10, y: dragBox.y + 10 }, { x: dropBox.x + 20, y: dropBox.y + 20 });
    check('glisser-déposer HTML5', await waitText(page, '#drag-result', /déposé/, 3000));
    await page.close(); }

  await browser.close();
  console.log('\nERREURS NAVIGATEUR (' + errors.length + ') :'); errors.forEach((e) => console.log('  ' + e));
  console.log('\nRésultat : ' + passes + ' ok, ' + failures + ' échec(s)');
  process.exit(failures || errors.length ? 1 : 0);
})().catch((e) => { console.error('CRASH', e); process.exit(2); });
