import { hl, onInsert, type VNode } from 'lib/view';

import * as blur from '../blur';
import type RoundController from '../ctrl';
import type { SocketPlace } from '../interfaces';
import { getOmokStatusSummary } from './omokState';

const defaultBoardSize = 15;
const boardFiles = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const placeCooldownMs = 300;
const lastPlaceAt = new WeakMap<RoundController, number>();
const hoveredPos = new WeakMap<RoundController, { row: number; col: number; key: Key } | null>();
const svgSize = 100;
const svgInset = 7;
const boardExtent = svgSize - svgInset * 2;

const normalizeBoardRows = (boardRows: string[], boardSize: number): string[] =>
  Array.from({ length: boardSize }, (_, row) => (boardRows[row] || '').padEnd(boardSize, '.').slice(0, boardSize));

const fileLabel = (col: number): string => boardFiles[col] || String(col + 1);
const posKey = (row: number, col: number): string => `${fileLabel(col)}${row + 1}`;

const getHoveredPos = (ctrl: RoundController) => hoveredPos.get(ctrl) || null;

const setHoveredPos = (ctrl: RoundController, next: { row: number; col: number; key: Key } | null): void => {
  const prev = hoveredPos.get(ctrl) || null;
  const changed = !prev || !next ? prev !== next : prev.row !== next.row || prev.col !== next.col || prev.key !== next.key;
  if (!changed) return;
  hoveredPos.set(ctrl, next);
  ctrl.redraw();
};

const sendPlace = (ctrl: RoundController, pos: Key): void => {
  const now = Date.now();
  if (now - (lastPlaceAt.get(ctrl) || 0) < placeCooldownMs) return;

  lastPlaceAt.set(ctrl, now);
  ctrl.setOmokPlacementPending(true);

  const place: SocketPlace = { pos };
  if (blur.get()) place.b = 1;
  ctrl.socket.send('place', place, { ackable: true });
};

const coord = (index: number, boardSize: number): number =>
  boardSize <= 1 ? svgSize / 2 : svgInset + (boardExtent * index) / (boardSize - 1);

const starPointIndexes = (boardSize: number): number[] => {
  if (boardSize >= 15) return [3, Math.floor(boardSize / 2), boardSize - 4];
  if (boardSize >= 11) return [2, Math.floor(boardSize / 2), boardSize - 3];
  return [Math.floor(boardSize / 2)];
};

const clampIndex = (value: number, boardSize: number): number =>
  Math.max(0, Math.min(boardSize - 1, Math.round(value)));

const bindBoardPlacement = (el: HTMLDivElement, ctrl: RoundController, boardSize: number): void => {
  const isEmptyPoint = (row: number, col: number): boolean => {
    const position = ctrl.data.omok?.position;
    const rows = position?.boardRows;
    return rows ? rows[row]?.[col] === '.' || rows[row]?.[col] === undefined : false;
  };

  const isCandidatePoint = (row: number, col: number): boolean =>
    !!ctrl.data.omok?.position.opening?.candidates?.some(candidate => candidate.row === row && candidate.col === col);

  const isWithinOpeningRange = (row: number, col: number): boolean => {
    const opening = ctrl.data.omok?.position.opening;
    if (!opening || typeof opening.rangeRadius !== 'number') return true;
    const center = Math.floor(boardSize / 2);
    return Math.max(Math.abs(row - center), Math.abs(col - center)) <= opening.rangeRadius;
  };

  const canPlacePoint = (row: number, col: number): boolean => {
    const opening = ctrl.data.omok?.position.opening;
    if (!opening) return isEmptyPoint(row, col);
    if (opening.candidateMode && opening.candidateSelection) return isCandidatePoint(row, col);
    if (opening.candidateMode) return isEmptyPoint(row, col);
    return isEmptyPoint(row, col) && isWithinOpeningRange(row, col);
  };

  const resolveHover = (event: MouseEvent) => {
    const svg = el.querySelector('.round__app__board__omok-placeholder__svg') as SVGElement | null;
    const rect = (svg ?? el).getBoundingClientRect();
    const relX = rect.width <= 0 ? 0 : (event.clientX - rect.left) / rect.width;
    const relY = rect.height <= 0 ? 0 : (event.clientY - rect.top) / rect.height;
    const col = clampIndex(relX * (boardSize - 1), boardSize);
    const row = clampIndex(relY * (boardSize - 1), boardSize);
    return { row, col, key: posKey(row, col) as Key };
  };

  el.addEventListener('mouseenter', event => {
    if (!ctrl.canPlaceOmok()) {
      setHoveredPos(ctrl, null);
      return;
    }
    const hovered = resolveHover(event);
    setHoveredPos(ctrl, canPlacePoint(hovered.row, hovered.col) ? hovered : null);
  });

  el.addEventListener('mousemove', event => {
    if (!ctrl.canPlaceOmok()) {
      setHoveredPos(ctrl, null);
      return;
    }
    const hovered = resolveHover(event);
    setHoveredPos(ctrl, canPlacePoint(hovered.row, hovered.col) ? hovered : null);
  });

  el.addEventListener('mouseleave', () => setHoveredPos(ctrl, null));

  el.addEventListener('click', event => {
    if (!ctrl.canPlaceOmok()) return;
    const hovered = getHoveredPos(ctrl) || resolveHover(event);
    if (!canPlacePoint(hovered.row, hovered.col)) return;
    setHoveredPos(ctrl, hovered);
    sendPlace(ctrl, hovered.key);
  });
};

export const renderOmokPlaceholder = (ctrl: RoundController): VNode | undefined => {
  const omok = ctrl.data.omok,
    position = omok?.position;
  if (!position) return;
  const statusSummary = getOmokStatusSummary(ctrl);
  if (!statusSummary) return;

  const boardSize = position.boardSize || omok.boardSize || position.boardRows.length || defaultBoardSize;
  const boardRows = normalizeBoardRows(position.boardRows, boardSize);
  const lastMove = position.lastMove;
  const attrs: Record<string, string | number | boolean> = {
    'aria-hidden': 'true',
    'data-board-game': 'omok',
    'data-board-ready': 'placeholder',
    'data-omok-ply': position.ply,
    'data-omok-turn': position.turn,
  };
  const ruleSet = position.ruleSet || omok.ruleset;
  if (boardSize) attrs['data-board-size'] = boardSize;
  if (ruleSet) attrs['data-omok-rule-set'] = ruleSet;
  if (lastMove?.key) attrs['data-omok-last-move'] = lastMove.key;
  if (position.opening) {
    attrs['data-omok-active-seat'] = position.opening.activeSeat;
    attrs['data-omok-candidate-mode'] = position.opening.candidateMode ? 'true' : 'false';
    attrs['data-omok-candidate-selection'] = position.opening.candidateSelection ? 'true' : 'false';
    if (typeof position.opening.rangeRadius === 'number') attrs['data-omok-range-radius'] = position.opening.rangeRadius;
  }

  const horizontalCoordStyle = {
    gridTemplateColumns: `repeat(${boardSize}, minmax(0, 1fr))`,
  };
  const verticalCoordStyle = {
    gridTemplateRows: `repeat(${boardSize}, minmax(0, 1fr))`,
  };
  const canPlace = ctrl.canPlaceOmok();
  const hovered = canPlace ? getHoveredPos(ctrl) : null;
  const opening = position.opening;
  const isActiveSeat = opening?.activeSeat === ctrl.data.player.color;
  const starIndexes = starPointIndexes(boardSize);
  const stoneRadius = Math.max(1.9, Math.min(3.15, boardExtent / (boardSize - 1) / 2.12));
  const hoverStoneClass =
    position.turn === 'white'
      ? 'is-white'
      : position.turn === 'black'
        ? 'is-black'
        : ctrl.data.player.color === 'white'
          ? 'is-white'
          : 'is-black';

  const boardSvgChildren: VNode[] = [
    hl('rect.round__app__board__omok-placeholder__svg-base', {
      attrs: {
        x: 0,
        y: 0,
        width: svgSize,
        height: svgSize,
        rx: 2.8,
        ry: 2.8,
      },
    }),
    ...Array.from({ length: boardSize }, (_, row) =>
      hl('line.round__app__board__omok-placeholder__svg-line', {
        attrs: {
          x1: svgInset,
          y1: coord(row, boardSize),
          x2: svgSize - svgInset,
          y2: coord(row, boardSize),
        },
      }),
    ),
    ...Array.from({ length: boardSize }, (_, col) =>
      hl('line.round__app__board__omok-placeholder__svg-line', {
        attrs: {
          x1: coord(col, boardSize),
          y1: svgInset,
          x2: coord(col, boardSize),
          y2: svgSize - svgInset,
        },
      }),
    ),
    ...starIndexes.flatMap(row =>
      starIndexes.map(col =>
        hl('circle.round__app__board__omok-placeholder__svg-star', {
          attrs: {
            cx: coord(col, boardSize),
            cy: coord(row, boardSize),
            r: Math.max(0.52, stoneRadius * 0.18),
          },
        }),
      ),
    ),
  ];

  for (let rowIndex = 0; rowIndex < boardSize; rowIndex += 1) {
    const row = boardRows[rowIndex];
    for (let colIndex = 0; colIndex < boardSize; colIndex += 1) {
      const cell = row[colIndex];
      if (cell !== 'b' && cell !== 'w') continue;
      const isLastMove = lastMove?.row === rowIndex && lastMove.col === colIndex;
      const cx = coord(colIndex, boardSize);
      const cy = coord(rowIndex, boardSize);
      boardSvgChildren.push(
        hl(`circle.round__app__board__omok-placeholder__svg-stone.${cell === 'b' ? 'is-black' : 'is-white'}`, {
          attrs: { cx, cy, r: stoneRadius },
        }),
      );
      if (hovered && hovered.row === rowIndex && hovered.col === colIndex) {
        boardSvgChildren.push(
          hl('circle.round__app__board__omok-placeholder__svg-hover-ring', {
            attrs: { cx, cy, r: Math.max(0.9, stoneRadius * 0.72) },
          }),
        );
      }
      if (isLastMove) {
        boardSvgChildren.push(
          hl('circle.round__app__board__omok-placeholder__svg-last-ring', {
            attrs: { cx, cy, r: Math.max(0.65, stoneRadius * 0.38) },
          }),
        );
      }
    }
  }

  for (const [candidateIndex, candidate] of opening?.candidates?.entries() || []) {
    const cx = coord(candidate.col, boardSize);
    const cy = coord(candidate.row, boardSize);
    boardSvgChildren.push(
      hl('circle.round__app__board__omok-placeholder__svg-ghost.is-black.is-candidate', {
        attrs: { cx, cy, r: stoneRadius },
      }),
    );
    boardSvgChildren.push(
      hl('text.round__app__board__omok-placeholder__svg-candidate-label', {
        attrs: {
          x: cx,
          y: cy + stoneRadius * 0.34,
          'text-anchor': 'middle',
          'aria-hidden': 'true',
        },
      }, String(candidateIndex + 1)),
    );
  }

  if (hovered) {
    const hoveredCell = boardRows[hovered.row]?.[hovered.col];
    if (hoveredCell === '.' || hoveredCell === undefined) {
      boardSvgChildren.push(
        hl(`circle.round__app__board__omok-placeholder__svg-ghost.${hoverStoneClass}`, {
          attrs: {
            cx: coord(hovered.col, boardSize),
            cy: coord(hovered.row, boardSize),
            r: stoneRadius,
          },
        }),
      );
      boardSvgChildren.push(
        hl('circle.round__app__board__omok-placeholder__svg-hover-ring', {
          attrs: {
            cx: coord(hovered.col, boardSize),
            cy: coord(hovered.row, boardSize),
            r: Math.max(1.05, stoneRadius * 1.14),
          },
        }),
      );
    }
  }

  return hl('div.round__app__board__omok-placeholder', { attrs }, [
    hl('div.round__app__board__omok-placeholder__shell', [
      hl(
        'div.round__app__board__omok-placeholder__status',
        {
          class: {
            'is-active': statusSummary.tone === 'active',
            'is-terminal': statusSummary.terminal,
            'is-draw': statusSummary.tone === 'draw',
          },
        },
        [
          hl('strong.round__app__board__omok-placeholder__status-main', statusSummary.text),
          statusSummary.detail
            ? hl('span.round__app__board__omok-placeholder__status-detail', statusSummary.detail)
            : undefined,
        ],
      ),
      opening
        ? hl('div.round__app__board__omok-placeholder__opening', [
            hl('span.round__app__board__omok-placeholder__opening-text', opening.instruction),
            opening.canSwap && isActiveSeat
              ? hl(
                  'button.button.button-empty.round__app__board__omok-placeholder__opening-action',
                  {
                    attrs: { type: 'button' },
                    on: { click: () => ctrl.socket.send('omok-swap', undefined, { ackable: true }) },
                  },
                  'Swap',
                )
              : undefined,
            opening.canStartCandidates && isActiveSeat
              ? hl(
                  'button.button.button-empty.round__app__board__omok-placeholder__opening-action',
                  {
                    attrs: { type: 'button' },
                    on: { click: () => ctrl.socket.send('omok-candidates', undefined, { ackable: true }) },
                  },
                  'Propose 10',
                )
              : undefined,
          ])
        : undefined,
      hl(
        'div.round__app__board__omok-placeholder__coords.round__app__board__omok-placeholder__coords--top',
        { style: horizontalCoordStyle },
        Array.from({ length: boardSize }, (_, col) =>
          hl('span.round__app__board__omok-placeholder__coord', fileLabel(col)),
        ),
      ),
      hl(
        'div.round__app__board__omok-placeholder__coords.round__app__board__omok-placeholder__coords--left',
        { style: verticalCoordStyle },
        Array.from({ length: boardSize }, (_, row) =>
          hl('span.round__app__board__omok-placeholder__coord', String(row + 1)),
        ),
      ),
      hl(
        'div.round__app__board__omok-placeholder__board',
        {
          class: { 'is-clickable': canPlace },
          attrs: {
            role: 'button',
            tabindex: 0,
            'aria-label': 'Place an omok stone',
            'aria-keyshortcuts': 'Click',
          },
          hook: onInsert((el: HTMLDivElement) => bindBoardPlacement(el, ctrl, boardSize)),
        },
        [
          hl(
            'svg.round__app__board__omok-placeholder__svg',
            {
              attrs: {
                viewBox: `0 0 ${svgSize} ${svgSize}`,
                preserveAspectRatio: 'none',
                'aria-hidden': 'true',
              },
            },
            boardSvgChildren,
          ),
        ],
      ),
      hl(
        'div.round__app__board__omok-placeholder__coords.round__app__board__omok-placeholder__coords--right',
        { style: verticalCoordStyle },
        Array.from({ length: boardSize }, (_, row) =>
          hl('span.round__app__board__omok-placeholder__coord', String(row + 1)),
        ),
      ),
      hl(
        'div.round__app__board__omok-placeholder__coords.round__app__board__omok-placeholder__coords--bottom',
        { style: horizontalCoordStyle },
        Array.from({ length: boardSize }, (_, col) =>
          hl('span.round__app__board__omok-placeholder__coord', fileLabel(col)),
        ),
      ),
    ]),
  ]);
};






