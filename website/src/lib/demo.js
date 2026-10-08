// Drives an AppScreen demo like a player would: show a pane, tap a button (a ring where the
// finger lands, the button pressed in), wait. Pauses while the demo is off screen, and doesn't
// run at all for people who asked for reduced motion (they see the first pane).

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function demo(root, script) {
  const app = root.querySelector('[data-app]');
  const ring = root.querySelector('[data-tap]');
  let visible = false;
  new IntersectionObserver(([e]) => { visible = e.isIntersecting; }, { threshold: 0.25 }).observe(root);

  const api = {
    $: (sel) => root.querySelector(sel),
    $$: (sel) => [...root.querySelectorAll(sel)],
    show(name) {
      for (const p of root.querySelectorAll('.pane')) {
        const on = p.dataset.pane === name;
        if (on && !p.classList.contains('on')) {
          p.classList.remove('swap');
          void p.offsetWidth;
          p.classList.add('swap');
        }
        p.classList.toggle('on', on);
      }
    },
    async tap(sel) {
      const el = root.querySelector(sel);
      if (!el) return;
      const a = app.getBoundingClientRect();
      const b = el.getBoundingClientRect();
      const k = a.width / 560 || 1;
      ring.style.left = `${(b.left + b.width / 2 - a.left) / k}px`;
      ring.style.top = `${(b.top + b.height / 2 - a.top) / k}px`;
      ring.classList.remove('go');
      void ring.offsetWidth;
      ring.classList.add('go');
      el.classList.add('pressed');
      await sleep(170);
      el.classList.remove('pressed');
      await sleep(120);
    },
    async wait(ms) {
      await sleep(ms);
      while (!visible) await sleep(250);
    },
  };

  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  (async () => {
    while (!visible) await sleep(250);
    for (;;) await script(api);
  })();
}
