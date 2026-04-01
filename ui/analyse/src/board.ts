import { Chessground as makeChessground } from '@lichess-org/chessground';
import type { Api as ChessgroundApi } from '@lichess-org/chessground/api';
import type { Config as ChessgroundConfig } from '@lichess-org/chessground/config';
import { h, type VNode } from 'snabbdom';

import type AnalyseCtrl from './ctrl';

export type AnalyseBoardApi = ChessgroundApi;
export type AnalyseBoardConfig = ChessgroundConfig;

interface OmokAnalyseMove {
  key: string;
  row: number;
  col: number;
}

interface OmokAnalysePosition {
  boardSize: number;
  boardRows: string[];
  turn: string;
  ruleSet: string;
  ply: number;
  lastMove?: OmokAnalyseMove;
}

interface OmokAnalyseData {
  position?: OmokAnalysePosition;
}

export interface AnalyseBoardFactory {
  create(element: HTMLElement, config: AnalyseBoardConfig): AnalyseBoardApi;
}

const chessgroundBoardFactory: AnalyseBoardFactory = {
  create: (element, config) => makeChessground(element, config),
};

// Keep analyse on chessground for now; this is the seam for future non-chess board adapters.
export const boardFactory = (_ctrl: AnalyseCtrl): AnalyseBoardFactory => chessgroundBoardFactory;

export const createBoard = (
  ctrl: AnalyseCtrl,
  element: HTMLElement,
  config: AnalyseBoardConfig,
): AnalyseBoardApi => boardFactory(ctrl).create(element, config);

const omokAnalyseData = (ctrl: AnalyseCtrl): OmokAnalyseData | undefined =>
  (ctrl.data as typeof ctrl.data & { omok?: OmokAnalyseData }).omok;

export const renderBoardInjectionPlaceholder = (ctrl: AnalyseCtrl): VNode => {
  const position = omokAnalyseData(ctrl)?.position;
  const attrs: Record<string, string | number | boolean> = {
    hidden: true,
    'data-board-game': position ? 'omok' : 'chess',
    'data-board-ready': position ? 'placeholder' : 'idle',
  };
  if (position?.boardSize) attrs['data-board-size'] = position.boardSize;
  if (position?.ruleSet) attrs['data-omok-rule-set'] = position.ruleSet;
  if (position) attrs['data-omok-ply'] = position.ply;
  if (position?.lastMove?.key) attrs['data-omok-last-move'] = position.lastMove.key;
  return h(
    'div.analyse__board-injection',
    {
      attrs,
    },
    [],
  );
};
