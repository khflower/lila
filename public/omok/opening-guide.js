const BOARD_SIZE = 15;
const FILES = 'ABCDEFGHIJKLMNO';
const CENTER = 'H8';
const SVG_SIZE = 100;
const SVG_INSET = 7;
const STEP = (SVG_SIZE - SVG_INSET * 2) / (BOARD_SIZE - 1);
const STAR_POINTS = [3, 7, 11];
const TRANSFORMS = ['identity', 'rot90', 'rot180', 'rot270', 'mirrorX', 'mirrorY', 'diag', 'antiDiag'];

const app = document.getElementById('omok-opening-guide-app');

if (app) {
  init().catch(error => {
    console.error(error);
    app.innerHTML = `<div class="omok-opening-guide-empty">Failed to load opening guide data.<br>${String(error.message || error)}</div>`;
  });
}

async function init() {
  const res = await fetch(app.dataset.guideUrl, { cache: 'no-store' });
  if (!res.ok) throw new Error(`guide data request failed: ${res.status}`);
  const data = expandSymmetry(await res.json());
  const state = {
    data,
    moves: [CENTER],
    selectedLine: null,
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

function currentStage(state) {
  switch (state.moves.length) {
    case 1:
      return 'place-second';
    case 2:
      return 'place-third';
    case 3:
      return 'choose-fourth';
    case 4:
      return 'choose-fifth';
    default:
      return 'done';
  }
}

function rangeRadius(state) {
  const stage = currentStage(state);
  if (stage === 'place-second') return 1;
  if (stage === 'place-third') return 2;
  return null;
}

function inCenterRange(key, radius) {
  const center = parseKey(CENTER);
  const pos = parseKey(key);
  return Math.max(Math.abs(center.col - pos.col), Math.abs(center.row - pos.row)) <= radius;
}

function prefix3(state) {
  return state.moves.slice(0, 3).join(' ');
}

function prefix4(state) {
  return state.moves.slice(0, 4).join(' ');
}

function fourthCandidates(state) {
  return state.data.prefix3[prefix3(state)] || [];
}

function fifthCandidates(state) {
  return state.data.prefix4[prefix4(state)] || [];
}

function clickableKeys(state) {
  const taken = occupied(state);
  const stage = currentStage(state);
  if (stage === 'place-second') return allKeys().filter(key => !taken.has(key) && inCenterRange(key, 1));
  if (stage === 'place-third') return allKeys().filter(key => !taken.has(key) && inCenterRange(key, 2));
  if (stage === 'choose-fourth') return fourthCandidates(state).map(item => item.move).filter(key => !taken.has(key));
  if (stage === 'choose-fifth') return fifthCandidates(state).map(item => item.move).filter(key => !taken.has(key));
  return [];
}

function allKeys() {
  const keys = [];
  for (let row = 0; row < BOARD_SIZE; row += 1) {
    for (let col = 0; col < BOARD_SIZE; col += 1) keys.push(`${FILES[col]}${row + 1}`);
  }
  return keys;
}

function expandSymmetry(data) {
  return {
    ...data,
    prefix3: expandPrefixMap(data.prefix3, transformFourthItem),
    prefix4: expandPrefixMap(data.prefix4, transformFifthItem),
  };
}

function expandPrefixMap(source, transformItem) {
  const expanded = {};
  for (const [prefix, items] of Object.entries(source || {})) {
    const baseMoves = String(prefix).split(' ');
    for (const transform of TRANSFORMS) {
      const transformedPrefix = transformMoves(baseMoves, transform).join(' ');
      const bucket = (expanded[transformedPrefix] ||= new Map());
      for (const item of items) {
        const transformed = transformItem(item, transform);
        const prev = bucket.get(transformed.move);
        if (!prev || compareExpandedItems(transformed, prev) < 0) bucket.set(transformed.move, transformed);
      }
    }
  }
  return Object.fromEntries(
    Object.entries(expanded).map(([prefix, bucket]) => [
      prefix,
      [...bucket.values()].sort((a, b) => compareExpandedItems(a, b)),
    ]),
  );
}

function compareExpandedItems(a, b) {
  if (typeof a.rank === 'number' || typeof b.rank === 'number') {
    return (a.rank ?? Number.MAX_SAFE_INTEGER) - (b.rank ?? Number.MAX_SAFE_INTEGER) || a.move.localeCompare(b.move);
  }
  return (a.bestEvalIndex ?? Number.MAX_SAFE_INTEGER) - (b.bestEvalIndex ?? Number.MAX_SAFE_INTEGER) || a.move.localeCompare(b.move);
}

function transformFourthItem(item, transform) {
  return {
    ...item,
    move: transformKey(item.move, transform),
  };
}

function transformFifthItem(item, transform) {
  return {
    ...item,
    move: transformKey(item.move, transform),
  };
}

function transformMoves(moves, transform) {
  return moves.map(move => transformKey(move, transform));
}

function transformKey(key, transform) {
  const center = parseKey(CENTER);
  const pos = parseKey(key);
  const dx = pos.col - center.col;
  const dy = pos.row - center.row;
  const [tx, ty] = applyTransform(dx, dy, transform);
  const col = center.col + tx;
  const row = center.row + ty;
  if (col < 0 || col >= BOARD_SIZE || row < 0 || row >= BOARD_SIZE) return key;
  return `${FILES[col]}${row + 1}`;
}

function applyTransform(dx, dy, transform) {
  switch (transform) {
    case 'identity':
      return [dx, dy];
    case 'rot90':
      return [-dy, dx];
    case 'rot180':
      return [-dx, -dy];
    case 'rot270':
      return [dy, -dx];
    case 'mirrorX':
      return [dx, -dy];
    case 'mirrorY':
      return [-dx, dy];
    case 'diag':
      return [dy, dx];
    case 'antiDiag':
      return [-dy, -dx];
    default:
      return [dx, dy];
  }
}

function instruction(state) {
  const stage = currentStage(state);
  if (stage === 'place-second') return 'Place White move 2 inside the 3x3 range centered on H8.';
  if (stage === 'place-third') return 'Place Black move 3 inside the 5x5 range centered on H8.';
  if (stage === 'choose-fourth') return 'Choose one of the recorded 4th moves from the workbook.';
  if (stage === 'choose-fifth') return 'Recorded 5th moves are ranked from strongest for Black.';
  return 'The selected 5-move line is complete. Use Undo or Reset to explore another branch.';
}

function statusTitle(state) {
  const stage = currentStage(state);
  if (stage === 'place-second') return 'Move 2';
  if (stage === 'place-third') return 'Move 3';
  if (stage === 'choose-fourth') return 'Move 4';
  if (stage === 'choose-fifth') return 'Move 5';
  return 'Line complete';
}

function legendHtml(state) {
  return `
    <div class="omok-opening-guide-legend">
      <h2>Guide legend</h2>
      <div class="omok-opening-guide-pills">
        <span class="omok-opening-guide-pill is-0">A: Black win</span>
        <span class="omok-opening-guide-pill is-2">B: Black better</span>
        <span class="omok-opening-guide-pill is-4">M: Balanced</span>
        <span class="omok-opening-guide-pill is-6">W: White better</span>
        <span class="omok-opening-guide-pill is-8">Z: White win</span>
      </div>
    </div>
  `;
}

function sequenceHtml(state) {
  const items = state.moves
    .map((move, index) => {
      const color = stoneColor(index) === 'black' ? 'Black' : 'White';
      return `<li class="omok-opening-guide-sequence-item"><strong>${index + 1}. ${escapeHtml(move)}</strong><span>${color}</span></li>`;
    })
    .join('');
  return `
    <section class="omok-opening-guide-sequence">
      <h2>Current sequence</h2>
      <ol class="omok-opening-guide-sequence-list">${items}</ol>
    </section>
  `;
}

function fourthListHtml(state) {
  const moves = fourthCandidates(state);
  if (!moves.length) return `<div class="omok-opening-guide-empty">No workbook entry matches the current 3-move prefix.</div>`;
  return `
    <section class="omok-opening-guide-list">
      <h2>Recorded 4th moves</h2>
      <ul class="omok-opening-guide-move-list">
        ${moves
          .map(
            item => `
              <li class="omok-opening-guide-move-item">
                <strong>${escapeHtml(item.move)}</strong>
                <span>Best outlook: ${escapeHtml(item.bestEvalLabel)} / 5th-move candidates: ${item.candidateCount}</span>
              </li>`,
          )
          .join('')}
      </ul>
    </section>
  `;
}

function fifthListHtml(state) {
  const moves = fifthCandidates(state);
  if (!moves.length) return `<div class="omok-opening-guide-empty">No recorded 5th move candidates exist for this 4-move prefix.</div>`;
  return `
    <section class="omok-opening-guide-list">
      <h2>Recorded 5th moves</h2>
      <ul class="omok-opening-guide-move-list">
        ${moves
          .map(
            item => `
              <li class="omok-opening-guide-move-item">
                <strong>${evalCode(item)}. ${escapeHtml(item.move)} - ${escapeHtml(item.evalLabel)}</strong>
                <span>${escapeHtml(item.title || 'Untitled opening')} / page ${item.page || '-'} / catalog ${item.catalog || '-'}</span>
              </li>`,
          )
          .join('')}
      </ul>
    </section>
  `;
}

function finalLineHtml(state) {
  if (!state.selectedLine) return `<div class="omok-opening-guide-empty">Select one of the ranked 5th moves to inspect its record.</div>`;
  const item = state.selectedLine;
  return `
    <section class="omok-opening-guide-list">
      <h2>Selected 5-move line</h2>
      <div class="omok-opening-guide-move-item">
        <strong>${evalCode(item)}. ${escapeHtml(item.move)} - ${escapeHtml(item.evalLabel)}</strong>
        <span>${escapeHtml(item.summary)}</span>
        <span>Title: ${escapeHtml(item.title || 'Untitled opening')}</span>
        <span>PDF page: ${item.page || '-'} / Catalog: ${item.catalog || '-'}</span>
      </div>
    </section>
  `;
}

function sidebarHtml(state) {
  const stage = currentStage(state);
  const body =
    stage === 'choose-fourth'
      ? fourthListHtml(state)
      : stage === 'choose-fifth'
        ? fifthListHtml(state)
        : stage === 'done'
          ? finalLineHtml(state)
          : `<div class="omok-opening-guide-empty">Place moves 2 and 3 to reveal the workbook-backed continuations.</div>`;

  return `
    <aside class="omok-opening-guide-side">
      <div class="omok-opening-guide-status">
        <strong>${statusTitle(state)}</strong>
        <span>${escapeHtml(instruction(state))}</span>
      </div>
      <div class="omok-opening-guide-actions-row">
        <button class="button button-empty js-guide-undo" type="button"${state.moves.length <= 1 ? ' disabled' : ''}>Undo</button>
        <button class="button button-empty js-guide-reset" type="button">Reset</button>
      </div>
      ${sequenceHtml(state)}
      ${body}
      ${legendHtml(state)}
      <div class="omok-opening-guide-footer-note">This page is for offline opening study only. It should not be shown during live play.</div>
    </aside>
  `;
}

function render(state) {
  app.className = 'omok-opening-guide-app';
  app.innerHTML = `
    <section class="omok-opening-guide-board-panel">${boardHtml(state)}</section>
    ${sidebarHtml(state)}
  `;

  app.querySelector('.js-guide-reset')?.addEventListener('click', () => {
    state.moves = [CENTER];
    state.selectedLine = null;
    state.hoverKey = null;
    render(state);
  });

  app.querySelector('.js-guide-undo')?.addEventListener('click', () => {
    if (state.moves.length <= 1) return;
    state.moves = state.moves.slice(0, -1);
    if (state.moves.length < 5) state.selectedLine = null;
    state.hoverKey = null;
    render(state);
  });

  const clickable = new Set(clickableKeys(state));
  const svg = app.querySelector('.js-guide-svg');
  const surface = app.querySelector('.js-guide-surface');
  surface?.addEventListener('mousemove', event => {
    const key = nearestKeyFromPointer(event, svg);
    const nextHover = key && clickable.has(key) ? key : null;
    if (state.hoverKey === nextHover) return;
    state.hoverKey = nextHover;
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
    if (!key || !clickable.has(key)) return;
    handleMove(state, key);
    render(state);
  });
  surface?.addEventListener('click', event => {
    event.preventDefault();
    const key = nearestKeyFromPointer(event, svg);
    if (!key || !clickable.has(key)) return;
    handleMove(state, key);
    render(state);
  });
}

function handleMove(state, key) {
  const stage = currentStage(state);
  if (stage === 'place-second' || stage === 'place-third' || stage === 'choose-fourth') {
    state.moves = [...state.moves, key];
    state.selectedLine = null;
    state.hoverKey = null;
    return;
  }
  if (stage === 'choose-fifth') {
    const item = fifthCandidates(state).find(candidate => candidate.move === key);
    if (!item) return;
    state.moves = [...state.moves, key];
    state.selectedLine = item;
    state.hoverKey = null;
  }
}

function boardHtml(state) {
  const lines = [];
  for (let i = 0; i < BOARD_SIZE; i += 1) {
    const pos = SVG_INSET + i * STEP;
    lines.push(`<line class="omok-opening-guide-grid" x1="${SVG_INSET}" y1="${pos}" x2="${SVG_SIZE - SVG_INSET}" y2="${pos}"></line>`);
    lines.push(`<line class="omok-opening-guide-grid" x1="${pos}" y1="${SVG_INSET}" x2="${pos}" y2="${SVG_SIZE - SVG_INSET}"></line>`);
  }

  const stars = STAR_POINTS.flatMap(row =>
    STAR_POINTS.map(col => {
      const pt = coordFor(col, row);
      return `<circle class="omok-opening-guide-star" cx="${pt.x}" cy="${pt.y}" r="0.62"></circle>`;
    }),
  );

  const range = rangeRadius(state);
  const rangeRect = range == null ? '' : (() => {
    const center = parseKey(CENTER);
    const topLeft = coordFor(center.col - range, center.row + range);
    const size = STEP * range * 2;
    return `<rect class="omok-opening-guide-range" x="${topLeft.x - STEP / 2}" y="${topLeft.y - STEP / 2}" width="${size + STEP}" height="${size + STEP}" rx="1.4" ry="1.4"></rect>`;
  })();

  const stones = state.moves
    .map((move, index) => {
      const pos = parseKey(move);
      const pt = coordFor(pos.col, pos.row);
      const cls = stoneColor(index) === 'black' ? 'omok-opening-guide-stone-black' : 'omok-opening-guide-stone-white';
      return `<circle class="${cls}" cx="${pt.x}" cy="${pt.y}" r="2.86"></circle>`;
    })
    .join('');

  const stage = currentStage(state);
  const taken = occupied(state);
  const fourthMarks =
    stage === 'choose-fourth'
      ? fourthCandidates(state)
          .map(item => {
            const pos = parseKey(item.move);
            const pt = coordFor(pos.col, pos.row);
            return `<circle class="omok-opening-guide-candidate-fourth" cx="${pt.x}" cy="${pt.y}" r="2.6"></circle>`;
          })
          .join('')
      : '';

  const fifthMarks =
    stage === 'choose-fifth'
      ? fifthCandidates(state)
          .map(item => {
            const pos = parseKey(item.move);
            const pt = coordFor(pos.col, pos.row);
            return `<g><circle class="omok-opening-guide-candidate-fifth" cx="${pt.x}" cy="${pt.y}" r="2.9"></circle><text class="omok-opening-guide-rank-label" x="${pt.x}" y="${pt.y + 0.18}">${evalCode(item)}</text></g>`;
          })
          .join('')
      : '';

  const preview = state.hoverKey && !taken.has(state.hoverKey) ? (() => {
    const pos = parseKey(state.hoverKey);
    const pt = coordFor(pos.col, pos.row);
    const previewClass = stoneColor(state.moves.length) === 'black' ? 'omok-opening-guide-preview-black' : 'omok-opening-guide-preview-white';
    return `<g><circle class="${previewClass}" cx="${pt.x}" cy="${pt.y}" r="2.86"></circle><circle class="omok-opening-guide-hover" cx="${pt.x}" cy="${pt.y}" r="3.25"></circle></g>`;
  })() : '';

  const topCoords = [...FILES].map(ch => `<span>${ch}</span>`).join('');
  const sideCoords = Array.from({ length: BOARD_SIZE }, (_, i) => `<span>${BOARD_SIZE - i}</span>`).join('');

  return `
    <div class="omok-opening-guide-board-wrap">
      <div></div>
      <div class="omok-opening-guide-coords-horizontal">${topCoords}</div>
      <div></div>
      <div class="omok-opening-guide-coords-vertical">${sideCoords}</div>
      <div class="omok-opening-guide-board-box">
        <svg class="omok-opening-guide-svg js-guide-svg" viewBox="0 0 ${SVG_SIZE} ${SVG_SIZE}" preserveAspectRatio="none" aria-label="Omok opening guide board">
          <rect x="0" y="0" width="${SVG_SIZE}" height="${SVG_SIZE}" rx="2.8" ry="2.8" fill="transparent"></rect>
          ${rangeRect}
          ${lines.join('')}
          ${stars.join('')}
          ${fourthMarks}
          ${fifthMarks}
          ${stones}
          ${preview}
          <rect class="omok-opening-guide-surface js-guide-surface" x="0" y="0" width="${SVG_SIZE}" height="${SVG_SIZE}" rx="2.8" ry="2.8"></rect>
        </svg>
      </div>
      <div class="omok-opening-guide-coords-vertical">${sideCoords}</div>
      <div></div>
      <div class="omok-opening-guide-coords-horizontal">${topCoords}</div>
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

function evalCode(item) {
  const index = Number(item?.evalIndex);
  if (index === 0) return 'A';
  if (index >= 1 && index <= 3) return 'B';
  if (index === 4) return 'M';
  if (index >= 5 && index <= 7) return 'W';
  if (index === 8) return 'Z';
  return '?';
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
