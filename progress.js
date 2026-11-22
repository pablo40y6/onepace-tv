import { arcById, chapterById, chapters, nextChapter } from './catalog.js';

export const PROGRESS_KEY = 'onepace-tv-preview-chapter-progress-v2';
const LEGACY_KEY = 'onepace-tv-preview-current-index-v1';

// These indexes belonged to v1. Keep this mapping even if the catalogue changes.
const legacyArcIds = [
  'romance-dawn', 'orange-town', 'syrup-village', 'gaimon', 'baratie', 'arlong-park',
  'the-adventures-of-buggys-crew', 'loguetown', 'reverse-mountain', 'whisky-peak',
  'the-trials-of-koby-meppo', 'little-garden', 'drum-island', 'alabasta', 'jaya',
  'skypiea', 'long-ring-long-land', 'water-seven', 'enies-lobby', 'post-enies-lobby',
  'thriller-bark', 'sabaody-archipelago', 'amazon-lily', 'impel-down',
  'if-you-could-go-anywhere-the-adventures-of-the-straw-hats', 'marineford', 'post-war',
  'return-to-sabaody', 'fishman-island', 'punk-hazard', 'dressrosa', 'zou',
  'whole-cake-island', 'reverie', 'wano', 'egghead', 'special-one-piece-fan-letter',
  'special-warship-island-01-april-fools-2025',
];

export function initialProgress() {
  return { currentId: chapters[0].id, pendingArcId: null, openedId: null, watchedIds: [] };
}

export function readProgress(storage) {
  try {
    const source = storage ?? globalThis.localStorage;
    let saved;
    try {
      saved = JSON.parse(source.getItem(PROGRESS_KEY));
    } catch {
      // Try the old data if this record is malformed.
    }
    if (saved && Array.isArray(saved.watchedIds)) {
      const pendingArcId = arcById.has(saved.pendingArcId) ? saved.pendingArcId : null;
      const currentId = chapterById.has(saved.currentId) ? saved.currentId : chapters[0].id;
      return {
        currentId: pendingArcId ? null : currentId,
        pendingArcId,
        openedId: !pendingArcId && saved.openedId === currentId ? currentId : null,
        watchedIds: [...new Set(saved.watchedIds.filter(id => chapterById.has(id)))],
      };
    }
    const legacy = source.getItem(LEGACY_KEY);
    const arcId = /^\d+$/.test(legacy) ? legacyArcIds[Number(legacy)] : null;
    if (arcById.has(arcId)) {
      return { ...initialProgress(), currentId: null, pendingArcId: arcId };
    }
  } catch {
    // A blocked store or an invalid value must not prevent viewing the catalogue.
  }
  return initialProgress();
}

export function writeProgress(progress, storage) {
  try {
    (storage ?? globalThis.localStorage).setItem(PROGRESS_KEY, JSON.stringify(progress));
    return true;
  } catch {
    return false;
  }
}

export function selectChapter(progress, chapter) {
  return {
    ...progress,
    currentId: chapter.id,
    pendingArcId: null,
    openedId: progress.currentId === chapter.id ? progress.openedId : null,
  };
}

export function openChapter(progress, chapter) {
  return { ...selectChapter(progress, chapter), openedId: chapter.id };
}

export function finishChapter(progress, chapter, openNext) {
  const next = nextChapter(chapter);
  return {
    currentId: next?.id ?? chapter.id,
    pendingArcId: null,
    openedId: openNext && next ? next.id : null,
    watchedIds: progress.watchedIds.includes(chapter.id)
      ? progress.watchedIds
      : [...progress.watchedIds, chapter.id],
  };
}
