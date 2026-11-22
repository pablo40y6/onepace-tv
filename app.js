import { arcById, arcs, chapterById, nextChapter } from './catalog.js';
import { enableRemoteNavigation, focusControl } from './remote.js';
import {
  finishChapter, initialProgress, openChapter, PROGRESS_KEY, readProgress,
  selectChapter, writeProgress,
} from './progress.js';

const TV_KEY = 'onepace-tv-preview-tv-mode';
const $ = id => document.getElementById(id);
const home = $('home');
const arcsScreen = $('arcs');
const chaptersScreen = $('chapters');
const arcList = $('arc-list');
const chapterList = $('chapter-list');
const play = $('play');
const advance = $('advance');
const choose = $('choose');
const mark = $('mark');
const tvButton = $('tv-mode');
const installButton = $('install');
let progress = readProgress();
let canPersist = true;
let tvMode = readTVMode();
let returningFromPlayer = false;
let installPrompt;
let lastBrowsedArc;

function readTVMode() {
  try {
    const preference = localStorage.getItem(TV_KEY);
    if (preference !== null) return preference === 'on';
  } catch {
    // The toggle still works for this visit.
  }
  return true;
}

function applyTVMode() {
  document.body.classList.toggle('tv-mode', tvMode);
  tvButton.setAttribute('aria-pressed', String(tvMode));
}

function updateProgress(change) {
  progress = change(canPersist ? readProgress() : progress);
  canPersist = writeProgress(progress);
  $('storage-status').hidden = canPersist;
}

function currentChapter() {
  return chapterById.get(progress.currentId);
}

function currentArc() {
  return arcById.get(progress.pendingArcId ?? currentChapter()?.arcId) ?? arcs[0];
}

function arcHash(arc) {
  return '#arco/' + arc.id;
}

function chapterLabel(chapter) {
  return 'Capítulo ' + String(chapter.number).padStart(2, '0');
}

function watchedLabel(count, total) {
  return count + ' de ' + total + ' vistos';
}

function renderHome(watched) {
  const arc = currentArc();
  const chapter = currentChapter();
  const opened = chapter && progress.openedId === chapter.id;
  const next = chapter && nextChapter(chapter);
  const completed = chapter && watched.has(chapter.id);

  $('arc-title').textContent = arc.title;
  $('subtitle').textContent = chapter ? 'Subtítulos en inglés · ' + chapter.quality : 'Subtítulos en inglés';
  $('chapter-title').textContent = chapter ? chapterLabel(chapter) : '';
  $('migration').hidden = Boolean(chapter);
  $('current-arc').href = arcHash(arc);
  $('current-arc').hidden = !chapter;

  choose.hidden = Boolean(chapter);
  choose.href = arcHash(arc);
  play.hidden = !chapter;
  if (chapter) play.href = chapter.url;
  play.textContent = completed ? 'Volver a ver' : opened || watched.size ? 'Continuar' : 'Ver capítulo';
  play.classList.toggle('secondary', Boolean(opened && next));
  play.classList.toggle('primary', !opened || !next);
  advance.hidden = !opened || !next;
  if (next) advance.href = next.url;
  mark.hidden = !opened;
  mark.textContent = completed ? 'Terminar' : 'Marcar como visto';

  const upNext = $('up-next');
  upNext.hidden = !(opened && next) && !(completed && !next && !opened);
  if (opened && next) {
    const nextArc = arcById.get(next.arcId);
    upNext.textContent = nextArc === arc
      ? 'Siguiente · ' + chapterLabel(next)
      : 'Siguiente · ' + nextArc.title + ' · ' + chapterLabel(next);
  } else if (completed && !next && !opened) {
    upNext.textContent = 'Último capítulo marcado como visto.';
  }

  const count = arc.chapters.filter(item => watched.has(item.id)).length;
  $('arc-progress').hidden = !chapter;
  $('progress-bar').max = arc.chapters.length;
  $('progress-bar').value = count;
  $('progress-label').textContent = watchedLabel(count, arc.chapters.length);
}

function card(control, title, detail) {
  const item = document.createElement('li');
  const label = document.createElement('strong');
  const meta = document.createElement('span');
  control.className = 'catalog-card';
  label.textContent = title;
  meta.textContent = detail;
  control.append(label, meta);
  item.append(control);
  return item;
}

function renderArcs(watched) {
  arcList.replaceChildren(...arcs.map(arc => {
    const control = document.createElement('a');
    control.href = arcHash(arc);
    if (arc === currentArc()) control.setAttribute('aria-current', 'true');
    const count = arc.chapters.filter(item => watched.has(item.id)).length;
    const detail = count
      ? watchedLabel(count, arc.chapters.length)
      : arc.chapters.length + (arc.chapters.length === 1 ? ' capítulo' : ' capítulos');
    return card(control, arc.title, detail);
  }));
}

function renderChapters(arc, watched) {
  $('chapters-title').textContent = arc.title;
  $('external-list').href = arc.url;
  chapterList.replaceChildren(...arc.chapters.map(chapter => {
    const control = document.createElement('button');
    control.type = 'button';
    control.dataset.chapter = chapter.id;
    if (chapter.id === progress.currentId) control.setAttribute('aria-current', 'true');
    const status = watched.has(chapter.id) ? 'Visto' : chapter.id === progress.currentId ? 'Actual' : '';
    return card(control, chapterLabel(chapter), status ? chapter.quality + ' · ' + status : chapter.quality);
  }));
}

function defaultControl() {
  if (!arcsScreen.hidden) {
    return (lastBrowsedArc && arcList.querySelector('a[href="' + arcHash(lastBrowsedArc) + '"]'))
      ?? arcList.querySelector('[aria-current]');
  }
  if (!chaptersScreen.hidden) return chapterList.querySelector('[aria-current]') ?? chapterList.querySelector('button');
  return choose.hidden ? play : choose;
}

function focusAfterPlayback() {
  const action = advance.hidden ? mark : advance;
  focusControl(!home.hidden && !action.hidden ? action : defaultControl());
}

function render(focus = false) {
  const hash = location.hash;
  const selectedArc = hash.startsWith('#arco/') ? arcById.get(hash.slice(6)) : null;
  arcsScreen.hidden = hash !== '#arcos';
  chaptersScreen.hidden = !selectedArc;
  home.hidden = !arcsScreen.hidden || !chaptersScreen.hidden;
  const watched = new Set(progress.watchedIds);

  if (!home.hidden) renderHome(watched);
  if (!arcsScreen.hidden) renderArcs(watched);
  if (selectedArc) {
    lastBrowsedArc = selectedArc;
    renderChapters(selectedArc, watched);
  }
  if (focus) focusControl(defaultControl());
}

function finishCurrent(openNext) {
  const chapter = currentChapter();
  if (!chapter || progress.openedId !== chapter.id) return;

  updateProgress(saved => finishChapter(saved, chapter, openNext));
  if (openNext) {
    returningFromPlayer = true;
  } else {
    render(true);
    $('announcement').textContent = chapterLabel(chapter) + ' marcado como visto.';
  }
}

play.addEventListener('click', () => {
  const chapter = currentChapter();
  if (!chapter) return;
  updateProgress(saved => openChapter(saved, chapter));
  returningFromPlayer = true;
});
advance.addEventListener('click', () => finishCurrent(true));
mark.addEventListener('click', () => finishCurrent(false));

chapterList.addEventListener('click', event => {
  const control = event.target.closest('[data-chapter]');
  const chapter = chapterById.get(control?.dataset.chapter);
  if (!chapter) return;
  updateProgress(saved => selectChapter(saved, chapter));
  location.hash = '';
});

$('reset').addEventListener('click', () => {
  if (!window.confirm('¿Reiniciar el progreso de One Pace en este navegador?')) return;
  updateProgress(initialProgress);
  location.hash = '';
});

tvButton.addEventListener('click', () => {
  tvMode = !tvMode;
  try {
    localStorage.setItem(TV_KEY, tvMode ? 'on' : 'off');
  } catch {
    // The current page can still switch layouts.
  }
  applyTVMode();
  if (tvMode) focusControl(defaultControl());
});

window.addEventListener('hashchange', () => render(true));
window.addEventListener('storage', event => {
  if (event.key !== PROGRESS_KEY && event.key !== null) return;
  const active = document.activeElement;
  const hadFocus = active?.closest('.screen');
  progress = readProgress();
  render();
  if (hadFocus && !active.getClientRects().length) focusControl(defaultControl());
});
window.addEventListener('pageshow', event => {
  if (!event.persisted) return;
  if (canPersist) progress = readProgress();
  render();
  if (tvMode && returningFromPlayer) focusAfterPlayback();
  returningFromPlayer = false;
});

window.addEventListener('beforeinstallprompt', event => {
  event.preventDefault();
  installPrompt = event;
  installButton.hidden = false;
});
installButton.addEventListener('click', async () => {
  if (!installPrompt) return;
  await installPrompt.prompt();
  installPrompt = null;
  if (document.activeElement === installButton) focusControl(defaultControl());
  installButton.hidden = true;
});
window.addEventListener('appinstalled', () => {
  installPrompt = null;
  installButton.hidden = true;
});

applyTVMode();
render();
if (tvMode) {
  const navigation = performance.getEntriesByType('navigation')[0];
  if (navigation?.type === 'back_forward') focusAfterPlayback();
  else focusControl(defaultControl());
}
enableRemoteNavigation(defaultControl);

if ('serviceWorker' in navigator) {
  // Viewing still works on browsers without PWA support or with it disabled.
  navigator.serviceWorker.register('./sw.js').catch(() => {});
}
