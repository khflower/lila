// these statements are wrapped in an iife to prevent scope pollution and injected into the html body
// after the DOM as an inline <script>. no imports or site globals are available here.

let cols = 0;

/* Move the timeline to/from the bottom depending on screen width. */

function layout() {
  const lobby = document.querySelector<HTMLElement>('main.lobby');
  if (!lobby) return;

  const newCols = Number(window.getComputedStyle(lobby).getPropertyValue('---cols'));
  if (newCols === cols) return;

  cols = newCols;

  const timeline = lobby.querySelector('.lobby__timeline');
  const newParent = cols > 2 ? lobby.querySelector<HTMLElement>('.lobby__side') : lobby;
  if (timeline && newParent) newParent.append(timeline);
}

layout();
window.addEventListener('resize', layout);

function forceBootNavigation(hash: string) {
  const target = `${location.pathname || '/'}?any#${hash}`;
  if (`${location.pathname}${location.search}${location.hash}` === target) location.reload();
  else location.assign(target);
}

function attachBootFallback(selector: string, hash: string) {
  const button = document.querySelector<HTMLElement>(selector);
  if (!button) return;
  button.addEventListener('click', () => {
    if ((window as any).__lobbyBooted) return;
    location.hash = hash;
    const site = (window as any).site;
    if (!site?.asset?.loadEsmPage) {
      forceBootNavigation(hash);
      return;
    }
    Promise.resolve(site.asset.loadEsmPage('lobby')).catch(() => forceBootNavigation(hash));
    window.setTimeout(() => {
      if ((window as any).__lobbyBooted) return;
      if (document.querySelector('.dialog-content, .game-setup')) return;
      forceBootNavigation(hash);
    }, 700);
  });
}

attachBootFallback('.lobby__start__button--hook', 'hook');
attachBootFallback('.lobby__start__button--friend', 'friend');
attachBootFallback('.lobby__start__button--ai', 'ai');
