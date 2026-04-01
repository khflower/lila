import { hl, onInsert, type VNode } from 'lib/view';

import * as blur from '../blur';
import type RoundController from '../ctrl';
import type { SocketPlace } from '../interfaces';
import { getOmokStatusSummary } from './omokState';

const defaultBoardSize = 15;
const boardFiles = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const placeCooldownMs = 300;
const lastPlaceAt = new WeakMap<RoundController, number>();

const normalizeBoardRows = (boardRows: string[], boardSize: number): string[] =>
  Array.from({ length: boardSize }, (_, row) => (boardRows[row] || '').padEnd(boardSize, '.').slice(0, boardSize));

const fileLabel = (col: number): string => boardFiles[col] || String(col + 1);
const posKey = (row: number, col: number): string => `${fileLabel(col)}${row + 1}`;
const isActivationKey = (event: KeyboardEvent): boolean =>
  event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar';
const sendPlace = (ctrl: RoundController, pos: Key): void => {
  const now = Date.now();
  if (now - (lastPlaceAt.get(ctrl) || 0) < placeCooldownMs) return;

  lastPlaceAt.set(ctrl, now);

  const place: SocketPlace = { pos };
  if (blur.get()) place.b = 1;
  ctrl.socket.send('place', place, { ackable: true });
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

  const horizontalCoordStyle = {
    gridTemplateColumns: `repeat(${boardSize}, minmax(0, 1fr))`,
  };
  const verticalCoordStyle = {
    gridTemplateRows: `repeat(${boardSize}, minmax(0, 1fr))`,
  };
  const boardStyle = {
    gridTemplateColumns: `repeat(${boardSize}, minmax(0, 1fr))`,
    gridTemplateRows: `repeat(${boardSize}, minmax(0, 1fr))`,
  };
  const canPlace = ctrl.canPlaceOmok();

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
        { style: boardStyle },
        boardRows.flatMap((row, rowIndex) =>
          Array.from(row).map((cell, colIndex) => {
            const empty = cell !== 'b' && cell !== 'w';
            const clickable = empty && canPlace;
            const pos = posKey(rowIndex, colIndex) as Key;

            return hl(
              'div.round__app__board__omok-placeholder__cell',
              {
                class: {
                  'is-black': cell === 'b',
                  'is-white': cell === 'w',
                  'is-last-move': lastMove?.row === rowIndex && lastMove.col === colIndex,
                },
                attrs: clickable
                  ? {
                      role: 'button',
                      tabindex: 0,
                      'data-omok-pos': pos,
                      'aria-label': `Place at ${pos}`,
                      'aria-keyshortcuts': 'Enter Space',
                    }
                  : { 'data-omok-pos': pos },
                hook: clickable
                  ? onInsert((el: HTMLDivElement) => {
                      el.addEventListener('click', () => sendPlace(ctrl, pos));
                      el.addEventListener('keydown', (event: KeyboardEvent) => {
                        if (event.repeat || !isActivationKey(event)) return;
                        event.preventDefault();
                        sendPlace(ctrl, pos);
                      });
                    })
                  : undefined,
              },
              cell === 'b' || cell === 'w'
                ? [hl('span.round__app__board__omok-placeholder__stone')]
                : [],
            );
          }),
        ),
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
