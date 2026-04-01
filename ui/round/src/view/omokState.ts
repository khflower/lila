import { finished as gameFinished } from 'lib/game';
import { hl, type VNode } from 'lib/view';

import type { OmokRoundMove } from '../interfaces';
import type RoundController from '../ctrl';

const capitalize = (value: string): string => value.slice(0, 1).toUpperCase() + value.slice(1);
const humanize = (value: string): string => value.replace(/[-_]+/g, ' ').replace(/\b\w/g, char => char.toUpperCase());

const formatTurn = (turn: string): string =>
  turn === 'white' || turn === 'black' ? capitalize(turn) : turn || 'Unknown';

const formatRuleSet = (ruleSet?: string): string =>
  ruleSet ? humanize(ruleSet) : 'Unknown';

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

const formatTerminalState = (ctrl: RoundController): string | undefined => {
  const omok = ctrl.data.omok;
  const winner = omok?.winner || ctrl.data.game.winner;

  if (omok?.status === 'draw') return 'Finished: Draw';
  if (omok?.status === 'win') return winner ? `Finished: ${formatTurn(String(winner))} wins` : 'Finished';
  if (omok?.status && omok.status !== 'ongoing') return humanize(omok.status);
  if (omok?.winner) return `Finished: ${formatTurn(String(omok.winner))} wins`;

  if (!gameFinished(ctrl.data)) return;
  if (ctrl.data.game.status.name === 'draw') return 'Finished: Draw';
  if (winner) return `Finished: ${formatTurn(String(winner))} wins`;
  return 'Finished';
};

export const renderOmokState = (ctrl: RoundController): VNode | undefined => {
  const omok = ctrl.data.omok,
    position = omok?.position;
  if (!position) return;

  const terminalState = formatTerminalState(ctrl);

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
      terminalState ? renderField('State', terminalState) : undefined,
      renderField('Turn', formatTurn(String(position.turn || ''))),
      renderField('Ply', String(position.ply)),
      renderField('Last move', formatLastMove(position.lastMove)),
      renderField('Rule set', formatRuleSet(position.ruleSet || omok.ruleset)),
    ],
  );
};
