const test = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');

const script = readFileSync(require('node:path').join(__dirname, 'player.js'), 'utf8');

function sampleVideo() {
  const style = new Map();
  return {
    paused: true, ended: false, readyState: 4, duration: 100, currentTime: 0, calls: 0, controls: true,
    style: {
      getPropertyValue: property => style.get(property)?.value ?? '',
      getPropertyPriority: property => style.get(property)?.priority ?? '',
      setProperty: (property, value, priority) => style.set(property, { value, priority }),
      removeProperty: property => style.delete(property),
    },
    play() { this.calls++; this.paused = false; return Promise.resolve(); },
    pause() { this.paused = true; },
  };
}

function scene() {
  const window = {};
  const location = { hash: '#item=0', pathname: '/l/LC22RWvq' };
  const listeners = new Map();
  let video = sampleVideo();
  runInNewContext(script, {
    window, location,
    document: { querySelector: () => video, addEventListener: (event, handler) => listeners.set(event, handler) },
  });
  return {
    controller: window.onePacePlayback,
    video: () => video,
    dispatch(type) {
      let intercepted = false;
      listeners.get(type)({ target: video, stopImmediatePropagation: () => { intercepted = true; } });
      return intercepted;
    },
    select: (item, replacement = sampleVideo()) => { location.hash = '#item=' + item; video = replacement; },
  };
}

test('an exact saved position is restored before playing the matching item', () => {
  const view = scene();
  const video = view.video();
  video.currentSrc = 'https://pixeldrain.net/api/file/demoFile?preview';
  let mediaTime = 0;
  Object.defineProperty(video, 'currentTime', {
    get: () => mediaTime,
    set: time => { mediaTime = time; video.seeking = true; },
  });
  assert.equal(view.controller.poll(1, 'demoFile', 42.375).playing, false);
  assert.equal(mediaTime, 0);
  const preparing = view.controller.poll(0, 'demoFile', 42.375);
  assert.equal(preparing.restoring, true);
  assert.equal(preparing.playing, false);
  assert.equal(mediaTime, 42.375);
  assert.equal(video.calls, 0);
  video.seeking = false;
  assert.equal(view.controller.poll(0, 'demoFile', 42.375).playing, true);
  assert.equal(video.calls, 1);
  mediaTime = 43;
  view.controller.poll(0, 'demoFile', 42.375);
  assert.equal(mediaTime, 43);
});

test('a Spanish playlist slot must match the verified file before playback', () => {
  const view = scene();
  const video = view.video();
  view.select(3, video);
  video.currentSrc = 'https://pixeldrain.net/api/file/englishFile';
  video.paused = false;
  assert.equal(view.controller.poll(3, null, null, 'spanishFile').playing, false);
  assert.equal(video.paused, true);
  assert.equal(video.calls, 0);
  video.currentSrc = 'https://pixeldrain.net/api/file/spanishFile';
  assert.equal(view.controller.poll(3, 'spanishFile', 58, 'spanishFile').playing, true);
  assert.equal(video.calls, 1);
  assert.equal(video.currentTime, 58);
});

test('a tap on the timeline previews ten seconds and seeks once when released', () => {
  const view = scene();
  const video = view.video();
  view.controller.poll(0);
  video.currentTime = 42.125;
  const ahead = view.controller.previewTimelineSeek(0, 1, 0);
  assert.equal(ahead.preview, 52.125);
  assert.equal(view.controller.poll(-1).preview, 52.125);
  assert.equal(video.currentTime, 42.125);
  assert.equal(view.controller.commitTimelineSeek(0, 1).time, 52.125);
  assert.equal(view.controller.poll(-1).preview, null);
  assert.equal(view.controller.commitTimelineSeek(0, 1), null);
  assert.equal(view.controller.previewTimelineSeek(0, -1, 0).preview, 42.125);
  assert.equal(view.controller.commitTimelineSeek(0, -1).time, 42.125);
});

test('timeline repeats accelerate from ten seconds to thirty and sixty without seeking early', () => {
  const view = scene();
  const video = view.video();
  video.duration = 300;
  let position = 7;
  let seeks = 0;
  Object.defineProperty(video, 'currentTime', {
    get: () => position,
    set: time => { position = time; seeks++; },
  });
  view.controller.poll(0);
  assert.equal(view.controller.previewTimelineSeek(0, 1, 0).preview, 17);
  assert.equal(view.controller.previewTimelineSeek(0, 1, 300), null);
  assert.equal(view.controller.previewTimelineSeek(0, 1, 450).preview, 27);
  assert.equal(view.controller.previewTimelineSeek(0, 1, 500), null);
  assert.equal(view.controller.previewTimelineSeek(0, 1, 1000).preview, 57);
  assert.equal(view.controller.previewTimelineSeek(0, 1, 2000).preview, 117);
  assert.equal(seeks, 0);
  assert.equal(view.controller.commitTimelineSeek(0, 1).time, 117);
  assert.equal(seeks, 1);
});

test('timeline bounds, cancellation and media changes discard stale edits', () => {
  const view = scene();
  view.controller.poll(0);
  const video = view.video();
  video.currentTime = 3;
  assert.equal(view.controller.previewTimelineSeek(0, -1, 0).preview, 0);
  view.controller.cancelTimelineSeek();
  assert.equal(view.controller.commitTimelineSeek(0, -1), null);
  assert.equal(video.currentTime, 3);
  video.currentTime = 98;
  assert.equal(view.controller.previewTimelineSeek(0, 1, 0).preview, 100);
  view.select(1);
  assert.equal(view.controller.commitTimelineSeek(0, 1), null);
  assert.equal(view.video().currentTime, 0);
  view.controller.poll(1);
  assert.equal(view.controller.previewTimelineSeek(1, 1, 0).preview, 10);
  view.video().currentSrc = 'https://pixeldrain.net/api/file/newVideo';
  assert.equal(view.controller.commitTimelineSeek(1, 1), null);
  assert.equal(view.video().currentTime, 0);
});

test('finishing during a preview lets release rewind or lets cancellation advance normally', () => {
  for (const commit of [true, false]) {
    const view = scene();
    view.controller.poll(0);
    view.dispatch('playing');
    const video = view.video();
    video.currentTime = 99;
    assert.equal(view.controller.previewTimelineSeek(0, -1, 0).preview, 89);
    video.currentTime = 100;
    video.ended = video.paused = true;
    assert.equal(view.dispatch('ended'), true);
    assert.equal(view.controller.poll(-1).finished, null);
    if (commit) {
      assert.equal(view.controller.commitTimelineSeek(0, -1).time, 89);
      assert.equal(video.calls, 2);
      assert.equal(view.controller.poll(-1).finished, null);
    } else {
      view.controller.cancelTimelineSeek();
      assert.equal(view.controller.poll(-1).finished, 0);
      assert.equal(video.calls, 1);
    }
  }
});

test('a checkpoint for a different file or beyond the duration starts at the beginning', () => {
  for (const [file, time] of [['otherFile', 45], ['demoFile', 120]]) {
    const view = scene();
    view.video().currentSrc = 'https://pixeldrain.net/api/file/demoFile';
    assert.equal(view.controller.poll(0, file, time).playing, true);
    assert.equal(view.video().currentTime, 0);
  }
});

test('leaving playback pauses and captures the current file and exact elapsed time', () => {
  const view = scene();
  const video = view.video();
  video.currentSrc = 'https://pixeldrain.net/api/file/demoFile';
  view.controller.poll(0);
  video.currentTime = 73.512;
  const snapshot = view.controller.pauseAndSnapshot();
  assert.equal(video.paused, true);
  assert.equal(snapshot.path, '/l/LC22RWvq');
  assert.equal(snapshot.item, 0);
  assert.equal(snapshot.file, 'demoFile');
  assert.equal(snapshot.time, 73.512);
  assert.equal(snapshot.duration, 100);
  assert.equal(snapshot.ended, false);
});

test('a natural ending is consumed once, then only the ready next item plays', () => {
  const view = scene();
  view.controller.poll(0);
  view.dispatch('playing');
  const old = view.video();
  old.currentTime = 100;
  old.ended = old.paused = true;
  assert.equal(view.dispatch('ended'), true);
  const finished = view.controller.poll(-1);
  assert.equal(finished.item, 0);
  assert.equal(finished.finished, 0);
  assert.equal(view.controller.poll(-1).finished, null);
  assert.equal(old.calls, 1);

  view.select(1);
  view.video().readyState = 0;
  view.controller.poll(1);
  assert.equal(view.video().calls, 0);
  view.video().readyState = 1;
  view.controller.poll(1);
  view.video().readyState = 4;
  assert.equal(view.controller.poll(1).playing, true);
  assert.equal(view.video().calls, 1);
});

test('the app receives the natural ending before Pixeldrain can change chapters', () => {
  const view = scene();
  view.controller.poll(0);
  view.dispatch('playing');
  view.video().currentTime = 100;
  view.video().ended = view.video().paused = true;
  if (!view.dispatch('ended')) view.select(1);
  const finished = view.controller.poll(-1);
  assert.equal(finished.finished, 0);
  assert.equal(finished.item, 0);
  assert.equal(view.controller.poll(-1).finished, null);
  assert.equal(view.video().calls, 1);
});

test('the five-second countdown follows video time, pauses and hides when seeking back', () => {
  const view = scene();
  view.controller.poll(0);
  const video = view.video();
  video.currentTime = 94.9;
  assert.equal(view.controller.poll(-1).nextIn, 0);
  video.currentTime = 95;
  assert.equal(view.controller.poll(-1).nextIn, 5);
  video.currentTime = 97.7;
  assert.equal(view.controller.poll(-1).nextIn, 3);
  video.pause();
  assert.equal(view.controller.poll(-1).nextIn, 3);
  assert.equal(view.controller.poll(-1).nextIn, 3);
  video.seeking = true;
  assert.equal(view.controller.poll(-1).nextIn, 0);
  video.seeking = false;
  video.currentTime = 83;
  assert.equal(view.controller.poll(-1).nextIn, 0);
});

test('seeking straight to the end leaves playback on that chapter', () => {
  const view = scene();
  view.controller.poll(0);
  view.dispatch('playing');
  view.video().currentTime = 95;
  view.controller.seek(10);
  assert.equal(view.video().currentTime, 100);
  view.video().ended = view.video().paused = true;
  assert.equal(view.dispatch('ended'), true);
  const state = view.controller.poll(-1);
  assert.equal(state.finished, null);
  assert.equal(state.nextIn, 0);
  assert.equal(state.item, 0);
});

test('changing the selected file or pausing early does not count as a completion', () => {
  const view = scene();
  view.controller.poll(0);
  view.dispatch('playing');
  view.video().pause();
  view.video().currentTime = 23;
  view.dispatch('ended');
  assert.equal(view.controller.poll(-1).finished, null);
  assert.equal(view.video().calls, 1);
  view.select(1);
  view.controller.poll(-1);
  assert.equal(view.video().calls, 0);
});

test('a failed media request never marks the episode complete', () => {
  const view = scene();
  view.controller.poll(0);
  view.dispatch('playing');
  view.video().error = { code: 2 };
  view.video().currentTime = 100;
  view.dispatch('ended');
  assert.equal(view.controller.poll(-1).finished, null);
});

test('reports rejected autoplay and does not keep issuing play requests', async () => {
  const view = scene();
  const video = view.video();
  video.play = () => { video.calls++; return Promise.reject(new Error('play blocked')); };
  view.controller.poll(0);
  await new Promise(setImmediate);
  assert.equal(view.controller.poll(0).failed, true);
  assert.equal(video.calls, 1);
});

test('cinema view fills the viewport, preserves aspect ratio, and restores native controls on error', () => {
  const view = scene();
  const video = view.video();
  video.style.setProperty('width', '480px', '');
  assert.equal(view.controller.poll(-1).cinema, true);
  assert.equal(video.style.getPropertyValue('width'), '100vw');
  assert.equal(video.style.getPropertyValue('object-fit'), 'contain');
  assert.equal(video.controls, false);
  video.error = { code: 2 };
  assert.equal(view.controller.poll(-1).cinema, false);
  assert.equal(video.style.getPropertyValue('width'), '480px');
  assert.equal(video.controls, true);
});

test('seeking jumps ten seconds without exceeding the ends of the video', () => {
  const view = scene();
  view.controller.poll(-1);
  view.video().currentTime = 20;
  view.controller.seek(10);
  assert.equal(view.video().currentTime, 30);
  view.controller.seek(-10);
  assert.equal(view.video().currentTime, 20);
  view.video().currentTime = 3;
  view.controller.seek(-10);
  assert.equal(view.video().currentTime, 0);
  view.video().currentTime = 95;
  view.controller.seek(10);
  assert.equal(view.video().currentTime, 100);
});

test('seeking jumps one minute and clamps at both ends of the video', () => {
  const view = scene();
  view.controller.poll(-1);
  const video = view.video();
  video.currentTime = 20;
  view.controller.seek(60);
  assert.equal(video.currentTime, 80);
  view.controller.seek(-60);
  assert.equal(video.currentTime, 20);
  view.controller.seek(-60);
  assert.equal(video.currentTime, 0);
  video.currentTime = 90;
  view.controller.seek(60);
  assert.equal(video.currentTime, 100);
  view.controller.seek(-30);
  assert.equal(video.currentTime, 100);
});

test('intro control seeks to the measured boundary only for the current file and item', () => {
  const view = scene();
  const video = view.video();
  video.currentSrc = 'https://pixeldrain.net/api/file/introFile?preview';
  video.currentTime = 12;
  assert.equal(view.controller.poll(-1).file, 'introFile');
  view.controller.skipIntro(1, 'introFile', 10, 62.25);
  view.controller.skipIntro(0, 'otherFile', 10, 62.25);
  view.controller.skipIntro(0, 'introFile', 15, 62.25);
  view.controller.skipIntro(0, 'introFile', 10, 100);
  assert.equal(video.currentTime, 12);
  view.controller.skipIntro(0, 'introFile', 10, 62.25);
  assert.equal(video.currentTime, 62.25);
  assert.equal(video.paused, true);

  video.currentTime = 70;
  view.controller.skipIntro(0, 'introFile', 10, 62.25);
  assert.equal(video.currentTime, 70);
  video.currentSrc = 'https://example.com/path/api/file/introFile';
  assert.equal(view.controller.poll(-1).file, null);
  video.currentTime = 15;
  view.controller.skipIntro(0, 'introFile', 10, 62.25);
  assert.equal(video.currentTime, 15);
});

test('an intro already selected cannot seek a stale video after a chapter change', () => {
  const view = scene();
  const video = view.video();
  video.currentSrc = 'https://pixeldrain.net/api/file/introFile';
  video.currentTime = 15;
  view.controller.poll(-1);
  view.select(1);
  view.controller.skipIntro(0, 'introFile', 10, 62.25);
  assert.equal(video.currentTime, 15);
  assert.equal(view.video().currentTime, 0);
});
