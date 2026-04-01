import { finished as gameFinished } from 'lib/game';
import { hl, type VNode } from 'lib/view';

import type { OmokRoundMove } from '../interfaces';
import type RoundController from '../ctrl';

export type OmokStatusTone = 'active' | 'neutral' | 'terminal' | 'draw';

export interface OmokStatusSummary {
  text: string;
  detail?: string;
  tone: OmokStatusTone;
  terminal: boolean;
}

const capitalize = (value: string): string => value.slice(0, 1).toUpperCase() + value.slice(1);
const humanize = (value: string): string => value.replace(/[-_]+/g, ' ').replace(/\b\w/g, char => char.toUpperCase());
const isTurnColor = (turn: string): turn is Color => turn === 'white' || turn === 'black';

export const formatTurn = (turn: string): string =>
  turn === 'white' || turn === 'black' ? capitalize(turn) : turn || 'Unknown';

export const formatRuleSet = (ruleSet?: string): string =>
  ruleSet ? humanize(ruleSet) : 'Unknown';

export const formatLastMove = (move?: OmokRoundMove): string => {
  if (move?.key) return move.key;
  if (move) return `row ${move.row}, col ${move.col}`;
  return 'None';
};

const renderField = (label: string, value: string, emphatic = false): VNode =>
  hl(
    'span.round__app__board__omok-state__field',
    {
      class: {
        'is-emphasis': emphatic,
      },
    },
    [
      hl('span.round__app__board__omok-state__label', `${label}:`),
      hl('strong.round__app__board__omok-state__value', value),
    ],
  );

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

export const getOmokStatusSummary = (ctrl: RoundController): OmokStatusSummary | undefined => {
  const omok = ctrl.data.omok,
    position = omok?.position;
  if (!position) return;

  const lastMove = position.lastMove;
  const moveDetail = lastMove ? `Last move ${formatLastMove(lastMove)}` : position.ply ? `Ply ${position.ply}` : undefined;
  const terminalState = formatTerminalState(ctrl);

  if (terminalState) {
    return {
      text: terminalState.replace(/^Finished:\s*/, ''),
      detail: moveDetail,
      tone: terminalState === 'Finished: Draw' ? 'draw' : 'terminal',
      terminal: true,
    };
  }

  const turn = String(position.turn || '');
  const turnLabel = formatTurn(turn);
  const turnDetail = isTurnColor(turn) ? `${turnLabel} to play` : undefined;

  if (!ctrl.data.player.spectator && isTurnColor(turn) && turn === ctrl.data.player.color) {
    return {
      text: 'Your turn',
      detail: [turnDetail, moveDetail].filter(Boolean).join(' · ') || undefined,
      tone: 'active',
      terminal: false,
    };
  }

  return {
    text: ctrl.data.player.spectator ? turnDetail || 'Omok live' : `Waiting for ${turnLabel}`,
    detail: moveDetail,
    tone: 'neutral',
    terminal: false,
  };
};

export const renderOmokState = (ctrl: RoundController): VNode | undefined => {
  const omok = ctrl.data.omok,
    position = omok?.position;
  if (!position) return;

  const statusSummary = getOmokStatusSummary(ctrl);
  if (!statusSummary) return;
  const lastMove = formatLastMove(position.lastMove);

  return hl(
    'div.round__app__board__omok-state',
    {
      class: {
        'is-active': statusSummary.tone === 'active',
        'is-terminal': statusSummary.terminal,
        'is-draw': statusSummary.tone === 'draw',
      },
      attrs: {
        role: 'status',
        'aria-live': 'polite',
      },
    },
    [
      hl('span.round__app__board__omok-state__title', 'Omok'),
      hl(
        'span.round__app__board__omok-state__summary',
        {
          class: {
            'is-active': statusSummary.tone === 'active',
            'is-terminal': statusSummary.terminal,
            'is-draw': statusSummary.tone === 'draw',
          },
        },
        [
          hl('strong.round__app__board__omok-state__summary-main', statusSummary.text),
          statusSummary.detail ? hl('span.round__app__board__omok-state__summary-detail', statusSummary.detail) : undefined,
        ],
      ),
      renderField(statusSummary.terminal ? 'Result' : 'State', statusSummary.text, true),
      statusSummary.terminal ? undefined : renderField('Turn', formatTurn(String(position.turn || ''))),
      renderField('Ply', String(position.ply)),
      renderField('Last move', lastMove, lastMove !== 'None'),
      renderField('Rule set', formatRuleSet(position.ruleSet || omok.ruleset)),
    ],
  );
};
