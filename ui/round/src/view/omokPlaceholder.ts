import { hl, type VNode } from 'lib/view';

import type RoundController from '../ctrl';

export const renderOmokPlaceholder = (ctrl: RoundController): VNode | undefined => {
  const omok = ctrl.data.omok,
    position = omok?.position;
  if (!position) return;

  const attrs: Record<string, string | number | boolean> = {
    hidden: true,
    'aria-hidden': 'true',
    'data-board-game': 'omok',
    'data-board-ready': 'placeholder',
    'data-omok-ply': position.ply,
    'data-omok-turn': position.turn,
  };
  const boardSize = position.boardSize || omok.boardSize;
  const ruleSet = position.ruleSet || omok.ruleset;
  if (boardSize) attrs['data-board-size'] = boardSize;
  if (ruleSet) attrs['data-omok-rule-set'] = ruleSet;
  if (position.lastMove?.key) attrs['data-omok-last-move'] = position.lastMove.key;

  return hl('div.round__app__board__omok-placeholder', { attrs });
};
