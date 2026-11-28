const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const artwork = require('./arc-artwork.json');
const { arcs: appArcs } = require('./catalog-index.json');

async function prepare() {
  const source = process.argv[2];
  const destination = path.resolve(process.argv[3]);
  const catalog = source === '-' ? require('./bundled-catalog.json') : await readWebsiteCatalog(source);
  for (const { id, list, bundledEpisodes = [] } of appArcs) {
    if (!bundledEpisodes.length) continue;
    const arc = catalog.find(entry => entry.id === id && entry.list === list);
    if (!arc) throw new Error('Missing bundled catalogue arc: ' + id);
    for (const { number, quality } of bundledEpisodes) {
      if (arc.episodes.some(episode => episode.number === number)) continue;
      if (number !== arc.first + arc.episodes.length || !/^(480p|720p|1080p)$/.test(quality)) {
        throw new Error('Invalid bundled episode: ' + id + ':' + number);
      }
      arc.episodes.push({ id: id + ':' + number, number, quality });
    }
  }

  fs.mkdirSync(destination, { recursive: true });
  fs.copyFileSync(path.join(__dirname, 'player.js'), path.join(destination, 'player.js'));
  fs.copyFileSync(path.join(__dirname, 'intro-timings.json'), path.join(destination, 'intro-timings.json'));
  fs.copyFileSync(path.join(__dirname, 'spanish-subtitles.json'), path.join(destination, 'spanish-subtitles.json'));
  copySubtitleOverrides(destination);
  fs.writeFileSync(path.join(destination, 'catalog.json'), JSON.stringify(catalog));
  if (process.argv[4]) copyCovers(catalog, path.resolve(process.argv[4]));
}

async function readWebsiteCatalog(source) {
  const { arcs } = await import(pathToFileURL(path.resolve(source, 'catalog.js')).href);
  return arcs.map(arc => ({
    id: arc.id,
    list: new URL(arc.url).pathname.split('/').at(-1),
    title: arc.title,
    first: arc.chapters[0].number,
    episodes: arc.chapters.map(({ id, number, quality }) => ({ id, number, quality })),
  }));
}

function copySubtitleOverrides(destination) {
  const filename = 'caption-overrides.json';
  const overrides = JSON.parse(fs.readFileSync(path.join(__dirname, filename), 'utf8'));
  for (const { captions } of Object.values(overrides)) {
    if (!/^[a-z0-9-]+\.es-419\.json$/.test(captions)) throw new Error('Invalid subtitle asset name');
    fs.copyFileSync(path.join(__dirname, captions), path.join(destination, captions));
  }
  fs.copyFileSync(path.join(__dirname, filename), path.join(destination, filename));
}

function copyCovers(catalog, destination) {
  const names = new Set();
  const files = new Set();
  for (const { list, title } of catalog) {
    const name = 'arc_' + list.toLowerCase();
    const file = artwork[list];
    if (!file || files.has(file) || names.has(name)) {
      throw new Error('Missing or duplicate artwork for ' + title);
    }
    names.add(name);
    files.add(file);
    const source = path.join(__dirname, 'res', 'drawable-nodpi', file);
    if (!fs.existsSync(source)) throw new Error('Missing artwork file for ' + title + ': ' + file);
    fs.copyFileSync(source, path.join(destination, name + path.extname(file)));
  }
}

prepare().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
