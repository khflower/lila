const BOARD_SIZE = 15;
const FILES = 'ABCDEFGHIJKLMNO';
const SVG_SIZE = 100;
const SVG_INSET = 7;
const STEP = (SVG_SIZE - SVG_INSET * 2) / (BOARD_SIZE - 1);
const STAR_POINTS = [3, 7, 11];
const MAX_LABEL_LENGTH = 3;
const FILE_FORMAT = 'omok-solo2/v1';
const BINARY_IMPORT_URL = '/api/omok/solo2/import';
const BINARY_EXPORT_URL = '/api/omok/solo2/export';

const root = document.getElementById('omok-solo-board-2-app');

if (root) {
  const state = createInitialState();
  render(state);
}

function createInitialState() {
  return {
    rootNode: createNode('root', null, null),
    currentId: 'root',
    nextId: 1,
    hoverKey: null,
    mode: 'stone',
    pendingText: '',
    filename: 'solo-board.ret',
    currentLabelDraft: '',
    nodeIndex: new Map(),
  };
}

function createNode(id, parentId, moveKey) {
  return {
    id,
    parentId,
    moveKey,
    text: '',
    boxText: '',
    children: [],
  };
}

function refreshIndex(state) {
  const map = new Map();
  walkTree(state.rootNode, node => {
    map.set(node.id, node);
  });
  state.nodeIndex = map;
}

function walkTree(node, fn) {
  fn(node);
  node.children.forEach(child => walkTree(child, fn));
}

function getNode(state, id) {
  return state.nodeIndex.get(id) || null;
}

function getCurrentNode(state) {
  return getNode(state, state.currentId) || state.rootNode;
}

function pathNodes(state) {
  const nodes = [];
  let cursor = getCurrentNode(state);
  while (cursor) {
    nodes.push(cursor);
    cursor = cursor.parentId ? getNode(state, cursor.parentId) : null;
  }
  return nodes.reverse();
}

function pathMoveNodes(state) {
  return pathNodes(state).slice(1);
}

function stoneColor(moveNumber) {
  return moveNumber % 2 === 1 ? 'black' : 'white';
}

function nodeMoveNumber(state, node) {
  let depth = 0;
  let cursor = node;
  while (cursor && cursor.parentId) {
    depth += 1;
    cursor = getNode(state, cursor.parentId);
  }
  return depth;
}

function occupiedOnPath(state) {
  return new Set(pathMoveNodes(state).map(node => node.moveKey));
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

function formatNodeLabel(state, node) {
  if (!node.moveKey) return 'Root';
  const moveNumber = nodeMoveNumber(state, node);
  const suffix = node.text ? ` [${node.text}]` : '';
  return `${moveNumber}. ${node.moveKey}${suffix}`;
}

function nextMoveNumber(state) {
  return pathMoveNodes(state).length + 1;
}

function normalizeLabel(text) {
  return String(text || '').trim().slice(0, MAX_LABEL_LENGTH);
}

function createChildAtCurrent(state, key, text = '') {
  const current = getCurrentNode(state);
  const existing = current.children.find(child => child.moveKey === key);
  if (existing) {
    state.currentId = existing.id;
    state.currentLabelDraft = existing.text || '';
    return true;
  }
  if (occupiedOnPath(state).has(key)) return false;
  const id = `n${state.nextId++}`;
  const child = createNode(id, current.id, key);
  child.text = normalizeLabel(text);
  current.children.push(child);
  state.currentId = child.id;
  state.currentLabelDraft = child.text || '';
  return true;
}

function deleteCurrentNode(state) {
  const current = getCurrentNode(state);
  if (!current.parentId) return;
  const parent = getNode(state, current.parentId);
  parent.children = parent.children.filter(child => child.id !== current.id);
  state.currentId = parent.id;
  state.currentLabelDraft = parent.text || '';
  state.hoverKey = null;
}

function goToParent(state) {
  const current = getCurrentNode(state);
  if (!current.parentId) return false;
  state.currentId = current.parentId;
  state.currentLabelDraft = getCurrentNode(state).text || '';
  state.hoverKey = null;
  return true;
}

function goToFirstChild(state) {
  const current = getCurrentNode(state);
  if (!current.children.length) return;
  state.currentId = current.children[0].id;
  state.currentLabelDraft = getCurrentNode(state).text || '';
  state.hoverKey = null;
}

function resetState(state) {
  const fresh = createInitialState();
  state.rootNode = fresh.rootNode;
  state.currentId = fresh.currentId;
  state.nextId = fresh.nextId;
  state.hoverKey = fresh.hoverKey;
  state.mode = fresh.mode;
  state.pendingText = fresh.pendingText;
  state.filename = fresh.filename;
  state.currentLabelDraft = fresh.currentLabelDraft;
  state.nodeIndex = fresh.nodeIndex;
}

function serializeNode(node) {
  return {
    moveKey: node.moveKey,
    text: node.text || '',
    boxText: node.boxText || '',
    children: node.children.map(serializeNode),
  };
}

function hydrateNode(raw, parentId, state) {
  const id = parentId == null ? 'root' : `n${state.nextId++}`;
  const node = createNode(id, parentId, raw.moveKey || null);
  node.text = normalizeLabel(raw.text || '');
  node.boxText = String(raw.boxText || '');
  node.children = Array.isArray(raw.children) ? raw.children.map(child => hydrateNode(child, id, state)) : [];
  return node;
}

function exportState(state) {
  return {
    format: FILE_FORMAT,
    version: 1,
    currentPath: pathMoveNodes(state).map(node => node.moveKey),
    root: serializeNode(state.rootNode),
  };
}

function resolveCurrentIdFromPath(state, currentPath) {
  let cursor = state.rootNode;
  for (const moveKey of Array.isArray(currentPath) ? currentPath : []) {
    const next = cursor.children.find(child => child.moveKey === moveKey);
    if (!next) break;
    cursor = next;
  }
  return cursor.id;
}

function applyImportedState(state, raw) {
  if (!raw || raw.format !== FILE_FORMAT || !raw.root) {
    throw new Error('Unsupported file');
  }
  const fresh = createInitialState();
  fresh.nextId = 1;
  fresh.rootNode = hydrateNode(raw.root, null, fresh);
  fresh.currentId = resolveCurrentIdFromPath(fresh, raw.currentPath);
  fresh.mode = 'stone';
  fresh.pendingText = '';
  fresh.filename = state.filename;
  fresh.currentLabelDraft = (getNode(fresh, fresh.currentId) || fresh.rootNode).text || '';
  state.rootNode = fresh.rootNode;
  state.currentId = fresh.currentId;
  state.nextId = fresh.nextId;
  state.hoverKey = null;
  state.mode = fresh.mode;
  state.pendingText = fresh.pendingText;
  state.currentLabelDraft = fresh.currentLabelDraft;
  state.nodeIndex = fresh.nodeIndex;
}

function downloadBlob(filename, blob) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

async function exportBinaryFile(state, filename) {
  const response = await fetch(`${BINARY_EXPORT_URL}?filename=${encodeURIComponent(filename)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/octet-stream',
    },
    body: JSON.stringify(exportState(state)),
  });
  if (!response.ok) {
    throw new Error(await response.text() || 'Failed to export app file');
  }
  downloadBlob(filename, await response.blob());
}

async function importBinaryFile(file) {
  const form = new FormData();
  form.append('file', file, file.name || 'solo-board.ret');
  const response = await fetch(BINARY_IMPORT_URL, {
    method: 'POST',
    body: form,
    headers: { Accept: 'application/json' },
  });
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.ok || !payload?.data) {
    throw new Error(payload?.error || 'Failed to load app file');
  }
  return payload.data;
}

function hasFilenameExtension(name) {
  return /\.[^./\\]+$/.test(name);
}

function normalizeFilename(name) {
  return String(name || '').trim() || 'solo-board';
}

function normalizeSaveFilename(name) {
  const normalized = normalizeFilename(name);
  return hasFilenameExtension(normalized) ? normalized : `${normalized}.ret`;
}

function promptForSaveFilename(currentName) {
  const response = window.prompt('Save filename', normalizeSaveFilename(currentName || 'solo-board'));
  if (response == null) return null;
  return normalizeSaveFilename(response);
}

function render(state) {
  refreshIndex(state);
  const current = getCurrentNode(state);
  const currentPath = pathMoveNodes(state);
  const childBadges = current.children.length
    ? current.children.map(child => {
        const label = child.text || child.moveKey;
        return `<button class="omok-solo2__branch${child.id === state.currentId ? ' omok-solo2__branch--active' : ''}" type="button" data-action="goto-node" data-node-id="${escapeHtml(child.id)}">${escapeHtml(label)}</button>`;
      }).join('')
    : '<div class="omok-solo2__empty">No child branches yet. Click the board to extend the line, or switch to text mode to add a labeled branch.</div>';

  root.className = 'omok-solo2';
  root.innerHTML = `
    <section class="omok-solo2__panel">
      <div class="omok-solo2__toolbar">
        <div class="omok-solo2__toolbar-group">
          <button class="button button-empty" type="button" data-action="go-root">Root</button>
          <button class="button button-empty" type="button" data-action="go-parent"${current.parentId ? '' : ' disabled'}>Back</button>
          <button class="button button-empty" type="button" data-action="go-child"${current.children.length ? '' : ' disabled'}>Forward</button>
          <button class="button button-empty" type="button" data-action="delete-current"${current.parentId ? '' : ' disabled'}>Delete branch</button>
          <button class="button button-empty" type="button" data-action="reset-file">New file</button>
        </div>
        <div class="omok-solo2__toolbar-group omok-solo2__toolbar-group--grow">
          <button class="button ${state.mode === 'text' ? 'button-metal' : 'button-empty'}" type="button" data-action="toggle-mode">${state.mode === 'text' ? 'Text mode' : 'Stone mode'}</button>
          <button class="button button-metal" type="button" data-action="save-app-file">Save file</button>
          <button class="button button-empty" type="button" data-action="load-app-file">Load file</button>
        </div>
      </div>

      <div class="omok-solo2__status">
        <div class="omok-solo2__status-card">
          <strong>Current move</strong>
          <span>${escapeHtml(formatNodeLabel(state, current))}</span>
        </div>
        <div class="omok-solo2__status-card">
          <strong>Next stone</strong>
          <span>${stoneColor(nextMoveNumber(state)) === 'black' ? 'Black' : 'White'}</span>
        </div>
      </div>

      ${boardHtml(state)}
      <div class="omok-solo2__footer-note">Right-click anywhere on the board to go back one move. Available continuations are shown as black dots, and text mode keeps short labels in the branch list and tree.</div>
    </section>

    <aside class="omok-solo2__side">
      <section class="omok-solo2__section">
        <h2>Branches from here</h2>
        <div class="omok-solo2__branch-list">${childBadges}</div>
        <div class="omok-solo2__hint">Current line length: ${currentPath.length} moves. Child markers also appear directly on the board.</div>
      </section>

      <section class="omok-solo2__section">
        <h2>Text mode</h2>
        <div class="omok-solo2__fields">
          <div class="omok-solo2__field">
            <label for="solo2-pending-text">New branch label</label>
            <input id="solo2-pending-text" class="js-solo2-pending-text" type="text" maxlength="${MAX_LABEL_LENGTH}" value="${escapeHtml(state.pendingText)}" placeholder="ABC" />
          </div>
          <div class="omok-solo2__field">
            <label for="solo2-current-label">Current move label</label>
            <input id="solo2-current-label" class="js-solo2-current-label" type="text" maxlength="${MAX_LABEL_LENGTH}" value="${escapeHtml(state.currentLabelDraft)}" placeholder="Optional" ${current.parentId ? '' : 'disabled'} />
          </div>
          <div class="omok-solo2__row">
            <button class="button button-empty" type="button" data-action="apply-current-label"${current.parentId ? '' : ' disabled'}>Apply label</button>
            <button class="button button-empty" type="button" data-action="clear-current-label"${current.parentId ? '' : ' disabled'}>Clear label</button>
          </div>
        </div>
      </section>

      <section class="omok-solo2__section">
        <h2>Text box</h2>
        <div class="omok-solo2__field">
          <label for="solo2-box-text">Notes for this node</label>
          <textarea id="solo2-box-text" class="js-solo2-box-text" placeholder="Write notes for the current node here.">${escapeHtml(current.boxText || '')}</textarea>
        </div>
      </section>

      <section class="omok-solo2__section">
        <h2>Save and load</h2>
        <div class="omok-solo2__fields">
          <div class="omok-solo2__field">
            <label for="solo2-file-name">Default save filename</label>
            <input id="solo2-file-name" class="js-solo2-filename" type="text" value="${escapeHtml(state.filename)}" placeholder="solo-board.ret" />
          </div>
          <div class="omok-solo2__hint">Save now uses the app-file flow only. Load accepts any extension, and save defaults to <code>.ret</code> unless you explicitly type another extension.</div>
          <input class="js-solo2-binary-file-input" type="file" hidden />
        </div>
      </section>

      <section class="omok-solo2__section">
        <h2>Current line</h2>
        ${sequenceHtml(state)}
      </section>

      <section class="omok-solo2__section">
        <h2>Move tree</h2>
        ${treeHtml(state, state.rootNode)}
      </section>
    </aside>
  `;

  bindEvents(state);
}

function bindEvents(state) {
  root.querySelectorAll('[data-action="goto-node"]').forEach(button => {
    button.addEventListener('click', () => {
      state.currentId = button.getAttribute('data-node-id') || 'root';
      state.currentLabelDraft = getCurrentNode(state).text || '';
      state.hoverKey = null;
      render(state);
    });
  });

  root.querySelector('[data-action="go-root"]')?.addEventListener('click', () => {
    state.currentId = 'root';
    state.currentLabelDraft = '';
    state.hoverKey = null;
    render(state);
  });

  root.querySelector('[data-action="go-parent"]')?.addEventListener('click', () => {
    goToParent(state);
    render(state);
  });

  root.querySelector('[data-action="go-child"]')?.addEventListener('click', () => {
    goToFirstChild(state);
    render(state);
  });

  root.querySelector('[data-action="delete-current"]')?.addEventListener('click', () => {
    deleteCurrentNode(state);
    render(state);
  });

  root.querySelector('[data-action="reset-file"]')?.addEventListener('click', () => {
    resetState(state);
    render(state);
  });

  root.querySelector('[data-action="toggle-mode"]')?.addEventListener('click', () => {
    state.mode = state.mode === 'stone' ? 'text' : 'stone';
    render(state);
  });

  root.querySelector('[data-action="apply-current-label"]')?.addEventListener('click', () => {
    const current = getCurrentNode(state);
    if (!current.parentId) return;
    current.text = normalizeLabel(state.currentLabelDraft);
    render(state);
  });

  root.querySelector('[data-action="clear-current-label"]')?.addEventListener('click', () => {
    const current = getCurrentNode(state);
    if (!current.parentId) return;
    current.text = '';
    state.currentLabelDraft = '';
    render(state);
  });

  root.querySelector('[data-action="save-app-file"]')?.addEventListener('click', async () => {
    try {
      const filename = promptForSaveFilename(state.filename || 'solo-board.ret');
      if (!filename) return;
      state.filename = filename;
      await exportBinaryFile(state, filename);
      render(state);
    } catch (error) {
      window.alert('Failed to export app file.');
      console.error(error);
    }
  });

  root.querySelector('[data-action="load-app-file"]')?.addEventListener('click', () => {
    root.querySelector('.js-solo2-binary-file-input')?.click();
  });

  root.querySelector('.js-solo2-pending-text')?.addEventListener('input', event => {
    state.pendingText = normalizeLabel(event.target.value);
    if (event.target.value !== state.pendingText) event.target.value = state.pendingText;
  });

  root.querySelector('.js-solo2-current-label')?.addEventListener('input', event => {
    state.currentLabelDraft = normalizeLabel(event.target.value);
    if (event.target.value !== state.currentLabelDraft) event.target.value = state.currentLabelDraft;
  });

  root.querySelector('.js-solo2-box-text')?.addEventListener('input', event => {
    getCurrentNode(state).boxText = event.target.value;
  });

  root.querySelector('.js-solo2-filename')?.addEventListener('input', event => {
    state.filename = event.target.value;
  });

  root.querySelector('.js-solo2-binary-file-input')?.addEventListener('change', async event => {
    const file = event.target.files && event.target.files[0];
    if (!file) return;
    try {
      applyImportedState(state, await importBinaryFile(file));
      state.filename = file.name || state.filename;
      render(state);
    } catch (error) {
      window.alert('Failed to load app file.');
      console.error(error);
    } finally {
      event.target.value = '';
    }
  });

  const svg = root.querySelector('.js-solo2-svg');
  const surface = root.querySelector('.js-solo2-surface');
  const taken = occupiedOnPath(state);
  const current = getCurrentNode(state);
  const childKeys = new Set(current.children.map(child => child.moveKey));

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

  surface?.addEventListener('contextmenu', event => {
    event.preventDefault();
    if (goToParent(state)) render(state);
  });

  surface?.addEventListener('pointerdown', event => {
    if (event.button !== 0) return;
    event.preventDefault();
    const key = nearestKeyFromPointer(event, svg);
    if (!key) return;
    if (childKeys.has(key)) {
      const child = current.children.find(node => node.moveKey === key);
      if (child) {
        state.currentId = child.id;
        state.currentLabelDraft = child.text || '';
        state.hoverKey = null;
        render(state);
      }
      return;
    }
    if (taken.has(key)) return;
    const label = state.mode === 'text' ? state.pendingText : '';
    if (createChildAtCurrent(state, key, label)) {
      state.hoverKey = null;
      render(state);
    }
  });
}

function boardHtml(state) {
  const current = getCurrentNode(state);
  const path = pathMoveNodes(state);
  const lines = [];
  for (let i = 0; i < BOARD_SIZE; i += 1) {
    const pos = SVG_INSET + i * STEP;
    lines.push(`<line class="omok-solo2__grid" x1="${SVG_INSET}" y1="${pos}" x2="${SVG_SIZE - SVG_INSET}" y2="${pos}"></line>`);
    lines.push(`<line class="omok-solo2__grid" x1="${pos}" y1="${SVG_INSET}" x2="${pos}" y2="${SVG_SIZE - SVG_INSET}"></line>`);
  }

  const stars = STAR_POINTS.flatMap(row =>
    STAR_POINTS.map(col => {
      const pt = coordFor(col, row);
      return `<circle class="omok-solo2__star" cx="${pt.x}" cy="${pt.y}" r="0.62"></circle>`;
    }),
  ).join('');

  const stones = path.map(node => {
    const moveNumber = nodeMoveNumber(state, node);
    const pos = parseKey(node.moveKey);
    const pt = coordFor(pos.col, pos.row);
    const color = stoneColor(moveNumber);
    const stoneClass = color === 'black' ? 'omok-solo2__stone-black' : 'omok-solo2__stone-white';
    const textClass = color === 'black' ? 'omok-solo2__stone-text-black' : 'omok-solo2__stone-text-white';
    return `<g><circle class="${stoneClass}" cx="${pt.x}" cy="${pt.y}" r="2.86"></circle><text class="${textClass}" x="${pt.x}" y="${pt.y}">${moveNumber}</text></g>`;
  }).join('');

  const childMarkers = current.children.map(child => {
    const pos = parseKey(child.moveKey);
    const pt = coordFor(pos.col, pos.row);
    const title = escapeHtml(formatNodeLabel(state, child));
    return `<g><title>${title}</title><circle class="omok-solo2__child-dot" cx="${pt.x}" cy="${pt.y}" r="1.1"></circle></g>`;
  }).join('');

  const preview = state.hoverKey && !occupiedOnPath(state).has(state.hoverKey) ? (() => {
    const pos = parseKey(state.hoverKey);
    const pt = coordFor(pos.col, pos.row);
    const isExistingChild = current.children.some(child => child.moveKey === state.hoverKey);
    if (isExistingChild) return '';
    const previewClass = stoneColor(nextMoveNumber(state)) === 'black' ? 'omok-solo2__preview-black' : 'omok-solo2__preview-white';
    const previewText = state.mode === 'text' && state.pendingText ? `<text class="omok-solo2__preview-text" x="${pt.x}" y="${pt.y}">${escapeHtml(state.pendingText)}</text>` : '';
    return `<g><circle class="${previewClass}" cx="${pt.x}" cy="${pt.y}" r="2.86"></circle>${previewText}<circle class="omok-solo2__preview-ring" cx="${pt.x}" cy="${pt.y}" r="3.32"></circle></g>`;
  })() : '';

  const topCoords = [...FILES].map(ch => `<span>${ch}</span>`).join('');
  const sideCoords = Array.from({ length: BOARD_SIZE }, (_, i) => `<span>${BOARD_SIZE - i}</span>`).join('');

  return `
    <div class="omok-solo2__board-wrap">
      <div></div>
      <div class="omok-solo2__coords-horizontal">${topCoords}</div>
      <div></div>
      <div class="omok-solo2__coords-vertical">${sideCoords}</div>
      <div class="omok-solo2__board-box">
        <svg class="omok-solo2__board-svg js-solo2-svg" viewBox="0 0 ${SVG_SIZE} ${SVG_SIZE}" preserveAspectRatio="none" aria-label="Omok solo board">
          <rect x="0" y="0" width="${SVG_SIZE}" height="${SVG_SIZE}" rx="2.8" ry="2.8" fill="transparent"></rect>
          ${lines.join('')}
          ${stars}
          ${stones}
          ${childMarkers}
          ${preview}
          <rect class="omok-solo2__surface js-solo2-surface" x="0" y="0" width="${SVG_SIZE}" height="${SVG_SIZE}" rx="2.8" ry="2.8"></rect>
        </svg>
      </div>
      <div class="omok-solo2__coords-vertical">${sideCoords}</div>
      <div></div>
      <div class="omok-solo2__coords-horizontal">${topCoords}</div>
      <div></div>
    </div>
  `;
}

function sequenceHtml(state) {
  const path = pathMoveNodes(state);
  if (!path.length) return '<div class="omok-solo2__empty">No moves yet.</div>';
  return `<ol class="omok-solo2__sequence">${path.map(node => `<li>${escapeHtml(formatNodeLabel(state, node))}${node.boxText ? ` - ${escapeHtml(node.boxText.slice(0, 36))}` : ''}</li>`).join('')}</ol>`;
}

function treeHtml(state, node) {
  const children = node.children.map(child => {
    const isCurrent = child.id === state.currentId;
    const branch = treeHtml(state, child);
    return `<li><button class="omok-solo2__tree-button${isCurrent ? ' omok-solo2__tree-button--current' : ''}" type="button" data-action="goto-node" data-node-id="${escapeHtml(child.id)}">${escapeHtml(formatNodeLabel(state, child))}</button>${branch}</li>`;
  }).join('');
  if (!children) return '';
  return `<ul class="omok-solo2__tree">${children}</ul>`;
}
