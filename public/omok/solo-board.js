const BOARD_SIZE = 15;
const FILES = 'ABCDEFGHIJKLMNO';
const SVG_SIZE = 100;
const SVG_INSET = 7;
const STEP = (SVG_SIZE - SVG_INSET * 2) / (BOARD_SIZE - 1);
const STAR_POINTS = [3, 7, 11];

const root = document.getElementById('omok-solo-board-app');

if (root) {
  const state = {
    moves: [],
    hoverKey: null,
  };
  render(state);
}

function parseKey(key) {
  const col = FILES.indexOf(key[0].toUpperCase());
  const row = Number(key.slice(1)) - 1;
  return { key, col, row };
}

function coordFor(col, row) {
  return {
    x: SVG_INSET + col * STEP,
    y: SVG_INSET + (BOARD_SIZE - 1 - row) * STEP,
  };
}

function stoneColor(index) {
  return index % 2 === 0 ? 'black' : 'white';
}

function occupied(state) {
  return new Set(state.moves);
}

function instruction(state) {
  const next = stoneColor(state.moves.length) === 'black' ? 'Black' : 'White';
  return `${next} to place. Stones alternate automatically after every move.`;
}

function render(state) {
  root.className = 'omok-solo-board-app';
  root.innerHTML = `
    <section class="omok-solo-board-panel">${boardHtml(state)}</section>
    <aside class="omok-solo-board-side">
      <div class="omok-solo-board-status">
        <strong>Solo board</strong>
        <span>${escapeHtml(instruction(state))}</span>
      </div>
      <div class="omok-solo-board-actions">
        <button class="button button-empty js-solo-undo" type="button"${state.moves.length ? '' : ' disabled'}>Undo</button>
        <button class="button button-empty js-solo-reset" type="button">Reset</button>
      </div>
      <section class="omok-solo-board-sequence">
        <h2>Move sequence</h2>
        ${
          state.moves.length
            ? `<ol>${state.moves
                .map(
                  (move, index) =>
                    `<li><strong>${index + 1}. ${escapeHtml(move)}</strong><span>${stoneColor(index) === 'black' ? 'Black' : 'White'}</span></li>`,
                )
                .join('')}</ol>`
            : '<div class="omok-solo-board-empty">Place a stone anywhere on the board to begin.</div>'
        }
      </section>
    </aside>
  `;

  root.querySelector('.js-solo-reset')?.addEventListener('click', () => {
    state.moves = [];
    state.hoverKey = null;
    render(state);
  });

  root.querySelector('.js-solo-undo')?.addEventListener('click', () => {
    if (!state.moves.length) return;
    state.moves = state.moves.slice(0, -1);
    state.hoverKey = null;
    render(state);
  });

  const taken = occupied(state);
  const svg = root.querySelector('.js-solo-svg');
  const surface = root.querySelector('.js-solo-surface');
  surface?.addEventListener('mousemove', event => {
    const key = nearestKeyFromPointer(event, svg);
    const next = key && !taken.has(key) ? key : null;
    if (state.hoverKey === next) return;
    state.hoverKey = next;
    render(state);
  });
  surface?.addEventListener('mouseleave', () => {
    if (state.hoverKey == null) return;
    state.hoverKey = null;
    render(state);
  });
  surface?.addEventListener('pointerdown', event => {
    event.preventDefault();
    const key = nearestKeyFromPointer(event, svg);
    if (!key || taken.has(key)) return;
    state.moves = [...state.moves, key];
    state.hoverKey = null;
    render(state);
  });
}

function boardHtml(state) {
  const lines = [];
  for (let i = 0; i < BOARD_SIZE; i += 1) {
    const pos = SVG_INSET + i * STEP;
    lines.push(`<line class="omok-solo-board-grid" x1="${SVG_INSET}" y1="${pos}" x2="${SVG_SIZE - SVG_INSET}" y2="${pos}"></line>`);
    lines.push(`<line class="omok-solo-board-grid" x1="${pos}" y1="${SVG_INSET}" x2="${pos}" y2="${SVG_SIZE - SVG_INSET}"></line>`);
  }

  const stars = STAR_POINTS.flatMap(row =>
    STAR_POINTS.map(col => {
      const pt = coordFor(col, row);
      return `<circle class="omok-solo-board-star" cx="${pt.x}" cy="${pt.y}" r="0.62"></circle>`;
    }),
  );

  const stones = state.moves
    .map((move, index) => {
      const pos = parseKey(move);
      const pt = coordFor(pos.col, pos.row);
      const cls = stoneColor(index) === 'black' ? 'omok-solo-board-stone-black' : 'omok-solo-board-stone-white';
      return `<circle class="${cls}" cx="${pt.x}" cy="${pt.y}" r="2.86"></circle>`;
    })
    .join('');

  const preview = state.hoverKey ? (() => {
    const pos = parseKey(state.hoverKey);
    const pt = coordFor(pos.col, pos.row);
    const previewClass = stoneColor(state.moves.length) === 'black' ? 'omok-solo-board-preview-black' : 'omok-solo-board-preview-white';
    return `<g><circle class="${previewClass}" cx="${pt.x}" cy="${pt.y}" r="2.86"></circle><circle class="omok-solo-board-hover" cx="${pt.x}" cy="${pt.y}" r="3.25"></circle></g>`;
  })() : '';

  const topCoords = [...FILES].map(ch => `<span>${ch}</span>`).join('');
  const sideCoords = Array.from({ length: BOARD_SIZE }, (_, i) => `<span>${BOARD_SIZE - i}</span>`).join('');

  return `
    <div class="omok-solo-board-wrap">
      <div></div>
      <div class="omok-solo-board-coords-horizontal">${topCoords}</div>
      <div></div>
      <div class="omok-solo-board-coords-vertical">${sideCoords}</div>
      <div class="omok-solo-board-box">
        <svg class="omok-solo-board-svg js-solo-svg" viewBox="0 0 ${SVG_SIZE} ${SVG_SIZE}" preserveAspectRatio="none" aria-label="Omok solo board">
          <rect x="0" y="0" width="${SVG_SIZE}" height="${SVG_SIZE}" rx="2.8" ry="2.8" fill="transparent"></rect>
          ${lines.join('')}
          ${stars.join('')}
          ${stones}
          ${preview}
          <rect class="omok-solo-board-surface js-solo-surface" x="0" y="0" width="${SVG_SIZE}" height="${SVG_SIZE}" rx="2.8" ry="2.8"></rect>
        </svg>
      </div>
      <div class="omok-solo-board-coords-vertical">${sideCoords}</div>
      <div></div>
      <div class="omok-solo-board-coords-horizontal">${topCoords}</div>
      <div></div>
    </div>
  `;
}

function nearestKeyFromPointer(event, svg) {
  if (!(svg instanceof SVGElement)) return null;
  const rect = svg.getBoundingClientRect();
  if (!rect.width || !rect.height) return null;
  const x = ((event.clientX - rect.left) / rect.width) * SVG_SIZE;
  const y = ((event.clientY - rect.top) / rect.height) * SVG_SIZE;
  const col = clamp(Math.round((x - SVG_INSET) / STEP), 0, BOARD_SIZE - 1);
  const rowFromTop = clamp(Math.round((y - SVG_INSET) / STEP), 0, BOARD_SIZE - 1);
  const row = BOARD_SIZE - 1 - rowFromTop;
  return `${FILES[col]}${row + 1}`;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
