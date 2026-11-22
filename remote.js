const directions = {
  ArrowUp: [0, -1],
  ArrowDown: [0, 1],
  ArrowLeft: [-1, 0],
  ArrowRight: [1, 0],
};

export function focusControl(control) {
  if (!control) return;
  control.focus({ preventScroll: true });
  control.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

export function enableRemoteNavigation(defaultControl) {
  document.addEventListener('keydown', event => {
    if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
    const active = document.activeElement;
    if (active?.matches('input, select, textarea, [contenteditable]')) return;

    const [x, y] = directions[event.key] ?? [];
    if (x === undefined) {
      if (['Accept', 'Select', 'OK'].includes(event.key) && active?.matches('button, a[href]')) {
        event.preventDefault();
        if (!event.repeat) active.click();
      }
      return;
    }

    event.preventDefault();
    if (!active?.matches('button, a[href]')) {
      focusControl(defaultControl());
      return;
    }

    const rect = active.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const controls = document.querySelectorAll('button:not(:disabled), a[href]');
    let closest = null;
    let bestDistance = Infinity;
    for (const control of controls) {
      if (control === active || !control.getClientRects().length) continue;
      const target = control.getBoundingClientRect();
      const deltaX = target.left + target.width / 2 - centerX;
      const deltaY = target.top + target.height / 2 - centerY;
      const along = deltaX * x + deltaY * y;
      if (along < 2) continue;
      const overlap = x
        ? Math.min(rect.bottom, target.bottom) - Math.max(rect.top, target.top)
        : Math.min(rect.right, target.right) - Math.max(rect.left, target.left);
      const cross = Math.abs(deltaX * y + deltaY * x);
      const distance = along + (overlap > 1 ? 0 : 120 + cross * 3);
      if (distance < bestDistance) {
        closest = control;
        bestDistance = distance;
      }
    }
    focusControl(closest);
  });
}
