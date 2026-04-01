import { hl, type VNode } from 'lib/view';

import type { OmokRoundMove } from '../interfaces';
import type RoundController from '../ctrl';

const capitalize = (value: string): string => value.slice(0, 1).toUpperCase() + value.slice(1);

const formatTurn = (turn: string): string =>
  turn === 'white' || turn === 'black' ? capitalize(turn) : turn || 'Unknown';

const formatRuleSet = (ruleSet?: string): string =>
  ruleSet ? ruleSet.replace(/[-_]+/g, ' ').replace(/\b\w/g, char => char.toUpperCase()) : 'Unknown';

const formatLastMove = (move?: OmokRoundMove): string => {
  if (move?.key) return move.key;
  if (move) return `row ${move.row}, col ${move.col}`;
  return 'None';
};

const renderField = (label: string, value: string): VNode =>
  hl('span.round__app__board__omok-state__field', [
    hl('span.round__app__board__omok-state__label', `${label}:`),
    hl('strong.round__app__board__omok-state__value', value),
  ]);

export const renderOmokState = (ctrl: RoundController): VNode | undefined => {
  const omok = ctrl.data.omok,
    position = omok?.position;
  if (!position) return;

  return hl(
    'div.round__app__board__omok-state',
    {
      attrs: {
        role: 'status',
        'aria-live': 'polite',
      },
    },
    [
      hl('span.round__app__board__omok-state__title', 'Omok'),
      renderField('Turn', formatTurn(String(position.turn || ''))),
      renderField('Ply', String(position.ply)),
      renderField('Last move', formatLastMove(position.lastMove)),
      renderField('Rule set', formatRuleSet(position.ruleSet || omok.ruleset)),
    ],
  );
};
