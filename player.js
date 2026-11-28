(() => {
  if (window.onePacePlayback) return;

  let activeVideo = null;
  let activeItem = null;
  let endedVideo = null;
  let endSeekVideo = null;
  let finished = null;
  let attempt = null;
  let timelineEdit = null;
  let cinemaVideo = null;
  let originalStyle;
  let originalControls;
  const cinemaStyle = {
    position: 'fixed', top: '0', right: '0', bottom: '0', left: '0', display: 'block',
    width: '100vw', height: '100vh', 'max-width': 'none', 'max-height': 'none',
    margin: '0', padding: '0', border: '0', 'border-radius': '0',
    'object-fit': 'contain', background: '#000', 'z-index': '2147483647', 'pointer-events': 'none',
  };

  function currentItem() {
    const fragment = /^#item=(\d+)$/.exec(location.hash);
    return fragment ? Number(fragment[1]) : null;
  }

  function currentFile(video) {
    const source = /^https:\/\/(?:pixeldrain\.net|pixeldrain\.com)\/api\/file\/([a-zA-Z0-9_-]{6,16})(?:[?#].*)?$/
      .exec(video.currentSrc);
    return source?.[1] ?? null;
  }

  function observePlaying(video) {
    if (video !== document.querySelector('video')) return;
    activeVideo = video;
    activeItem = currentItem();
  }

  document.addEventListener('playing', event => observePlaying(event.target), true);
  // The TV app decides whether to load the next chapter after the current video ends.
  document.addEventListener('ended', event => {
    const video = event.target;
    const item = currentItem();
    if (video !== document.querySelector('video') || item === null) return;
    if (video.error || !Number.isFinite(video.duration) || video.currentTime < video.duration - 0.5) return;
    event.stopImmediatePropagation();
    endedVideo = video;
    if (video === activeVideo && item === activeItem && video !== endSeekVideo) finished = item;
    endSeekVideo = null;
    activeVideo = null;
    activeItem = null;
  }, true);

  function nextIn(video) {
    const remaining = video.duration - video.currentTime;
    return !video.ended && !video.seeking && remaining > 0 && remaining <= 5 ? Math.ceil(remaining) : 0;
  }

  function playbackSnapshot(video, item, ready) {
    return {
      path: location.pathname,
      item,
      file: ready ? currentFile(video) : null,
      time: ready ? video.currentTime : null,
      duration: ready ? video.duration : null,
      preview: ready && isTimelineVideo(video, item) ? timelineEdit.position : null,
      seeking: ready && Boolean(video.seeking),
      ended: ready && Boolean(video.ended),
    };
  }

  function pauseAndSnapshot() {
    const item = currentItem();
    const video = document.querySelector('video');
    if (video && !video.paused) video.pause();
    return playbackSnapshot(video, item, item !== null && video?.readyState >= 1 && !video.error);
  }

  function requestPlayback(video, item, resumeFile, resumeAt) {
    if (attempt?.video !== video || attempt?.item !== item) {
      const target = typeof resumeFile === 'string' && currentFile(video) === resumeFile
        && Number.isFinite(resumeAt) && Number.isFinite(video.duration)
        && resumeAt > 0 && resumeAt < video.duration ? resumeAt : null;
      attempt = { video, item, target, started: false, failed: false };
      if (target !== null && !jumpToCheckpoint(video, target)) attempt.failed = true;
    }
    const request = attempt;
    if (request.started || request.failed || video.seeking
        || (request.target !== null && Math.abs(video.currentTime - request.target) > 0.5)) return;
    request.started = true;
    if (video.paused) {
      try {
        Promise.resolve(video.play()).catch(() => { request.failed = true; });
      } catch {
        request.failed = true;
      }
    }
  }

  function jumpToCheckpoint(video, target) {
    try {
      if (!video.paused) video.pause();
      video.currentTime = target;
      return true;
    } catch {
      return false;
    }
  }

  function poll(wantedItem, resumeFile, resumeAt, expectedFile) {
    const item = currentItem();
    const video = document.querySelector('video');
    const hasMedia = item !== null && video?.readyState >= 1 && !video.error;
    const wrongFile = hasMedia && typeof expectedFile === 'string' && currentFile(video) !== expectedFile;
    if (wrongFile && !video.paused) video.pause();
    const ready = hasMedia && !wrongFile;
    if (!ready || !isTimelineVideo(video, item)) timelineEdit = null;
    const cinema = showCinema(ready ? video : null);
    const mediaChanged = video !== endedVideo || video?.currentTime < 1;
    if (wantedItem === item && ready && mediaChanged && !video.ended) {
      requestPlayback(video, item, resumeFile, resumeAt);
    }
    const starting = attempt?.video === video && attempt?.item === item && !attempt.started;
    const playing = Boolean(video && video.readyState >= 2 && !video.paused && !video.ended && !starting && !wrongFile);
    if (playing && item !== null && video !== activeVideo && video !== endedVideo) {
      observePlaying(video);
    }

    const result = {
      ...playbackSnapshot(video, item, ready),
      cinema,
      playing,
      restoring: starting && attempt.target !== null && !attempt.failed,
      nextIn: ready ? nextIn(video) : 0,
      finished: timelineEdit || wrongFile ? null : finished,
      failed: attempt?.item === item && attempt?.video === video && attempt.failed,
    };
    if (!timelineEdit) finished = null;
    return result;
  }

  function showCinema(video) {
    if (video === cinemaVideo) return video !== null;
    if (cinemaVideo) {
      for (const [property, value, priority] of originalStyle) {
        if (value) cinemaVideo.style.setProperty(property, value, priority);
        else cinemaVideo.style.removeProperty(property);
      }
      cinemaVideo.controls = originalControls;
    }
    cinemaVideo = video;
    if (!video) return false;

    originalControls = video.controls;
    originalStyle = Object.keys(cinemaStyle).map(property => [
      property, video.style.getPropertyValue(property), video.style.getPropertyPriority(property),
    ]);
    for (const [property, value] of Object.entries(cinemaStyle)) video.style.setProperty(property, value, 'important');
    video.controls = false;
    return true;
  }

  function seek(seconds) {
    const video = cinemaVideo;
    if (![-60, -10, 10, 60].includes(seconds)) return;
    if (!video || !Number.isFinite(video.duration) || video.error) return;
    seekTo(video, video.currentTime + seconds);
  }

  function clampTime(video, position) {
    return Math.max(0, Math.min(video.duration, position));
  }

  function seekTo(video, position) {
    const target = clampTime(video, position);
    endSeekVideo = target >= video.duration ? video : null;
    video.currentTime = target;
  }

  function timelineVideo(item) {
    const video = cinemaVideo;
    return video && item === currentItem() && video === document.querySelector('video')
      && !video.error && Number.isFinite(video.duration) && video.duration > 0 ? video : null;
  }

  function isTimelineVideo(video, item) {
    return timelineEdit !== null && timelineEdit.video === video && timelineEdit.item === item
      && timelineEdit.source === video.currentSrc;
  }

  function previewTimelineSeek(item, direction, heldMs) {
    const video = timelineVideo(item);
    if (!video || (direction !== -1 && direction !== 1)) return null;

    if (heldMs === 0) {
      timelineEdit = { video, item, source: video.currentSrc, direction, position: video.currentTime, lastStep: 0 };
    } else if (!isTimelineVideo(video, item) || timelineEdit.direction !== direction
        || heldMs < 400 || heldMs - timelineEdit.lastStep < 280) {
      return null;
    }

    const seconds = heldMs >= 2000 ? 60 : heldMs >= 1000 ? 30 : 10;
    timelineEdit.position = clampTime(video, timelineEdit.position + direction * seconds);
    timelineEdit.lastStep = heldMs;
    return playbackSnapshot(video, item, true);
  }

  function commitTimelineSeek(item, direction) {
    const video = timelineVideo(item);
    const edit = timelineEdit;
    const matches = video && isTimelineVideo(video, item) && edit.direction === direction;
    timelineEdit = null;
    if (!matches) return null;

    const shouldResume = finished === item && edit.position < video.duration;
    if (finished === item) finished = null;
    if (video.currentTime !== edit.position) seekTo(video, edit.position);
    if (shouldResume) control('play');
    return playbackSnapshot(video, item, true);
  }

  function cancelTimelineSeek() {
    timelineEdit = null;
  }

  function skipIntro(item, file, start, end) {
    const video = cinemaVideo;
    if (!video || video !== document.querySelector('video') || video.error || video.ended
        || currentItem() !== item || currentFile(video) !== file
        || !Number.isFinite(video.duration) || !Number.isFinite(start) || !Number.isFinite(end)
        || start < 0 || end >= video.duration || !(video.currentTime >= start && video.currentTime < end)) return;
    video.currentTime = end;
  }

  function control(action) {
    const video = document.querySelector('video');
    if (!video) return;
    if (action === 'pause' || (action === 'toggle' && !video.paused)) video.pause();
    else {
      if (video.ended) endSeekVideo = null;
      Promise.resolve(video.play()).catch(() => {});
    }
  }

  window.onePacePlayback = Object.freeze({ poll, control, seek, skipIntro, pauseAndSnapshot,
    previewTimelineSeek, commitTimelineSeek, cancelTimelineSeek });
})();
