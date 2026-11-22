// List order matches the Pixeldrain gallery. Keep arc IDs stable for saved progress.
// For mixed lists, hd contains inclusive ranges of episode numbers in 720p.
const lists = [
  { id: "romance-dawn", title: "Romance Dawn", listId: "LC22RWvq", count: 4 },
  { id: "orange-town", title: "Orange Town", listId: "DB6yzXoa", count: 3 },
  { id: "syrup-village", title: "Syrup Village", listId: "WN5T2jSr", count: 7 },
  { id: "gaimon", title: "Gaimon", listId: "phZ3h3bW", count: 1 },
  { id: "baratie", title: "Baratie", listId: "hPQyyjTf", count: 8 },
  { id: "arlong-park", title: "Arlong Park", listId: "gXjJTaYK", count: 10 },
  { id: "the-adventures-of-buggys-crew", title: "The Adventures of Buggy's Crew", listId: "FqWWwsFD", count: 1 },
  { id: "loguetown", title: "Loguetown", listId: "sng6aHYQ", count: 3 },
  { id: "reverse-mountain", title: "Reverse Mountain", listId: "Gxeezgxo", count: 2 },
  { id: "whisky-peak", title: "Whisky Peak", listId: "5d4WRz1y", count: 2 },
  { id: "the-trials-of-koby-meppo", title: "The Trials of Koby-Meppo", listId: "81KWfWcq", count: 1, quality: "720p" },
  { id: "little-garden", title: "Little Garden", listId: "uGAb7TWj", count: 5 },
  { id: "drum-island", title: "Drum Island", listId: "4juJksu7", count: 8, quality: "720p" },
  { id: "alabasta", title: "Alabasta", listId: "t8zvG6gH", count: 21, hd: [[3, 14], [16, 16]] },
  { id: "jaya", title: "Jaya", listId: "P6mnmZpN", count: 8, quality: "720p" },
  { id: "skypiea", title: "Skypiea", listId: "1bDwFCf9", count: 25, hd: [[1, 10], [14, 14]] },
  { id: "long-ring-long-land", title: "Long Ring Long Land", listId: "7ky9W8GE", count: 7, first: 0, hd: [[1, 6]] },
  { id: "water-seven", title: "Water Seven", listId: "o8YkGt9i", count: 20, quality: "720p" },
  { id: "enies-lobby", title: "Enies Lobby", listId: "PxxTYRGz", count: 25, hd: [[11, 25]] },
  { id: "post-enies-lobby", title: "Post-Enies Lobby", listId: "upyN5vVk", count: 6, quality: "720p" },
  { id: "thriller-bark", title: "Thriller Bark", listId: "Cq9faLGv", count: 22, quality: "720p" },
  { id: "sabaody-archipelago", title: "Sabaody Archipelago", listId: "jhpKUqF8", count: 11, quality: "720p" },
  { id: "amazon-lily", title: "Amazon Lily", listId: "Bsr9gKsn", count: 5, quality: "720p" },
  { id: "impel-down", title: "Impel Down", listId: "y5ywnHdF", count: 10, quality: "720p" },
  { id: "if-you-could-go-anywhere-the-adventures-of-the-straw-hats", title: "If You Could Go Anywhere... The Adventures of the Straw Hats", listId: "QWXq5X3x", count: 1 },
  { id: "marineford", title: "Marineford", listId: "uFuFpnpi", count: 17, quality: "720p" },
  { id: "post-war", title: "Post-War", listId: "7EANMSA7", count: 8, quality: "720p" },
  { id: "return-to-sabaody", title: "Return to Sabaody", listId: "igysH62b", count: 3, quality: "720p" },
  { id: "fishman-island", title: "Fishman Island", listId: "o6ZzNwzp", count: 24, quality: "720p" },
  { id: "punk-hazard", title: "Punk Hazard", listId: "wJoipMZu", count: 22, quality: "720p" },
  { id: "dressrosa", title: "Dressrosa", listId: "DMuj2Pqe", count: 48, quality: "720p" },
  { id: "zou", title: "Zou", listId: "nSkEY2Cq", count: 10, quality: "720p" },
  { id: "whole-cake-island", title: "Whole Cake Island", listId: "phsTwRmF", count: 39, quality: "720p" },
  { id: "reverie", title: "Reverie", listId: "Md2atmnr", count: 3 },
  { id: "wano", title: "Wano", listId: "6rSQPvNJ", count: 62, hd: [[5, 38]] },
  { id: "egghead", title: "Egghead", listId: "PRTmztoz", count: 22 },
  { id: "special-one-piece-fan-letter", title: "Special: One Piece Fan Letter", listId: "JGkNAa1V", count: 1 },
  { id: "special-warship-island-01-april-fools-2025", title: "Special: Warship Island 01 (April Fools 2025)", listId: "YTERtTLz", count: 1 },
];

export const arcs = lists.map(({ id, title, listId, count, first = 1, quality = "480p", hd = [] }) => {
  const url = `https://pixeldrain.net/l/${listId}`;
  const chapters = Array.from({ length: count }, (_, index) => {
    const number = first + index;
    return {
      id: `${id}:${number}`,
      arcId: id,
      number,
      quality: hd.some(([start, end]) => start <= number && number <= end) ? "720p" : quality,
      url: `${url}#item=${index}`,
    };
  });
  return { id, title, url, chapters };
});

export const chapters = arcs.flatMap(arc => arc.chapters);
export const arcById = new Map(arcs.map(arc => [arc.id, arc]));
export const chapterById = new Map(chapters.map(chapter => [chapter.id, chapter]));

export function nextChapter(chapter) {
  return chapters[chapters.indexOf(chapter) + 1] ?? null;
}
