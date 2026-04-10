import { finished as gameFinished } from 'lib/game';
import { bind, hl, type VNode } from 'lib/view';

import type { OmokRoundMove } from '../interfaces';
import type RoundController from '../ctrl';

export type OmokStatusTone = 'active' | 'neutral' | 'terminal' | 'draw';

export interface OmokStatusSummary {
  text: string;
  detail?: string;
  tone: OmokStatusTone;
  terminal: boolean;
}

const boardFiles = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const taraguchiMoveRegex = /^(\d+)\.\s+(Black|White)\s+placed\s+/i;
const siteText = (key: string, fallback: string): string =>
  ((i18n.site as unknown as Record<string, string | undefined>)[key] as string | undefined) || fallback;

const capitalize = (value: string): string => value.slice(0, 1).toUpperCase() + value.slice(1);
const humanize = (value: string): string => value.replace(/[-_]+/g, ' ').replace(/\b\w/g, char => char.toUpperCase());
const isTurnColor = (turn: string): turn is Color => turn === 'white' || turn === 'black';
const fileLabel = (col: number): string => boardFiles[col] || String(col + 1);

export const formatTurn = (turn: string): string =>
  turn === 'white' || turn === 'black' ? capitalize(turn) : turn || 'Unknown';

export const formatRuleSet = (ruleSet?: string): string =>
  ruleSet ? humanize(ruleSet) : 'Unknown';

export const formatLastMove = (move?: OmokRoundMove): string => {
  if (move?.key) return move.key;
  if (move) return `${fileLabel(move.col)}${move.row + 1}`;
  return 'None';
};

const renderField = (label: string, value: string, emphatic = false): VNode =>
  hl(
    'div.round__app__board__omok-state__field',
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

const getTaraguchiMoveOwners = (
  history: string[],
): {
  black: number[];
  white: number[];
} => {
  const black: number[] = [];
  const white: number[] = [];

  history.forEach(entry => {
    const match = entry.match(taraguchiMoveRegex);
    if (!match) return;
    const moveNo = Number.parseInt(match[1] || '', 10);
    if (!Number.isFinite(moveNo) || moveNo < 1 || moveNo > 6) return;
    if ((match[2] || '').toLowerCase() === 'black') black.push(moveNo);
    else white.push(moveNo);
  });

  return { black, white };
};

const renderTaraguchiSummary = (history: string[]): VNode => {
  const owners = getTaraguchiMoveOwners(history);
  const renderRow = (label: string, moves: number[]): VNode =>
    hl(
      'div.round__app__board__omok-state__opening-row',
      `${label} : ${moves.length ? moves.join(' ') : '-'}`,
    );

  return hl('div.round__app__board__omok-state__opening-summary', [
    hl('strong.round__app__board__omok-state__history-title', siteText('omokOpeningOrder', 'Opening order')),
    renderRow('Black', owners.black),
    renderRow('White', owners.white),
  ]);
};

const renderTaraguchiHistory = (ctrl: RoundController): VNode | undefined => {
  const history = ctrl.data.omok?.position.opening?.history || [];
  if (!history.length) return;

  return hl('div.round__app__board__omok-state__history-wrap', [
    renderTaraguchiSummary(history),
    hl('div.round__app__board__omok-state__history', [
      hl('strong.round__app__board__omok-state__history-title', siteText('omokOpeningLog', 'Opening log')),
      hl(
        'ol.round__app__board__omok-state__history-list',
        history.map(item => hl('li.round__app__board__omok-state__history-item', item)),
      ),
    ]),
  ]);
};

const renderRapfi = (ctrl: RoundController): VNode | undefined => {
  const omok = ctrl.data.omok;
  if (!omok) return;
  const config = omok.ai;
  const status = ctrl.omokRapfiStatus();
  const aiSide =
    config?.aiColor === 'white' || config?.aiColor === 'black'
      ? formatTurn(String(config.aiColor))
      : ctrl.data.opponent.ai
        ? formatTurn(ctrl.data.opponent.color)
        : undefined;
  const summary = status?.error
    ? status.error
    : ctrl.omokAnalysisLoading
      ? siteText('omokAnalysisThinking', 'Thinking...')
      : ctrl.omokAnalysis?.bestMove
        ? `${ctrl.omokAnalysis.bestMove.key}${ctrl.omokAnalysis.score ? ` / ${ctrl.omokAnalysis.score}` : ''}`
        : status?.ready
          ? siteText('omokAnalysisReady', 'Ready')
          : siteText('omokAnalysisIdle', 'Idle');

  return hl('div.round__app__board__omok-state__rapfi', [
    renderField('Engine', summary, !!ctrl.omokAnalysis?.bestMove),
    config
      ? renderField(
          'Rapfi AI',
          [
            aiSide ? `${aiSide} AI` : 'AI game',
            config.moveTimeMs ? `${config.moveTimeMs}ms` : undefined,
            config.depth ? `d${config.depth}` : undefined,
            config.nodes ? `n${config.nodes}` : undefined,
            `t${config.threads}`,
          ]
            .filter(Boolean)
            .join(' / '),
        )
      : undefined,
    ctrl.omokAnalysis && ctrl.omokAnalysis.lines.length
      ? hl(
          'div.round__app__board__omok-state__analysis-lines',
          ctrl.omokAnalysis.lines
            .filter(line => line.moves.length)
            .slice(0, 3)
            .map((line, index) =>
              hl('div.round__app__board__omok-state__analysis-line', [
                hl('strong', `#${index + 1}`),
                hl('span', line.moves.map(move => move.key).join(' ')),
                line.score ? hl('em', line.score) : undefined,
              ]),
            ),
        )
      : undefined,
    hl('div.round__app__board__omok-state__actions', [
      hl(
        'button.button-empty',
        {
          attrs: { type: 'button' },
          hook: bind('click', () => ctrl.toggleOmokAnalysis()),
        },
        ctrl.omokAnalysisEnabled
          ? siteText('omokAnalysisHide', 'Hide analysis')
          : siteText('omokAnalysisShow', 'Analyse with Rapfi'),
      ),
    ]),
  ]);
};

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

  const turn = String(position.opening?.activeSeat || position.turn || '');
  const turnLabel = formatTurn(turn);
  const turnDetail = isTurnColor(turn) ? `${turnLabel} to play` : undefined;

  if (!ctrl.data.player.spectator && ctrl.omokPlacementPending) {
    return {
      text: 'Placing stone...',
      detail: 'Waiting for board update',
      tone: 'neutral',
      terminal: false,
    };
  }

  if (!ctrl.data.player.spectator && isTurnColor(turn) && turn === ctrl.data.player.color) {
    return {
      text: 'Your turn',
      detail: [turnDetail, moveDetail].filter(Boolean).join(' / ') || undefined,
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
  const isTaraguchi = position.ruleSet === 'taraguchi10';
  const applyTaraguchiPanelStyle = (elm: Element | undefined): void => {
    if (!(elm instanceof HTMLElement)) return;
    [
      'position',
      'left',
      'right',
      'top',
      'bottom',
      'width',
      'max-height',
      'overflow',
      'padding',
      'border-radius',
      'background',
      'box-shadow',
      'z-index',
    ].forEach(prop => elm.style.removeProperty(prop));
  };
  const stateNodeData: any = {
    class: {
      'is-active': statusSummary.tone === 'active',
      'is-terminal': statusSummary.terminal,
      'is-draw': statusSummary.tone === 'draw',
      'is-taraguchi': isTaraguchi,
    },
    attrs: {
      role: 'status',
      'aria-live': 'polite',
    },
    hook: {
      insert: (vnode: any) => applyTaraguchiPanelStyle(vnode.elm),
      update: (_old: any, vnode: any) => applyTaraguchiPanelStyle(vnode.elm),
    },
  };

  return hl(
    'div.round__app__board__omok-state',
    stateNodeData,
    [
      hl('span.round__app__board__omok-state__title', siteText('omokBrandName', 'Omok.dev')),
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
      renderField(
        statusSummary.terminal ? siteText('omokResult', 'Result') : siteText('omokState', 'State'),
        statusSummary.text,
        true,
      ),
      statusSummary.terminal ? undefined : renderField('Turn', formatTurn(String(position.opening?.activeSeat || position.turn || ''))),
      renderField('Ply', String(position.ply)),
      renderField(siteText('omokLastMove', 'Last move'), lastMove, lastMove !== 'None'),
      renderField(siteText('omokRuleSetLabel', 'Rule set'), formatRuleSet(position.ruleSet || omok.ruleset)),
      isTaraguchi ? renderTaraguchiHistory(ctrl) : undefined,
      renderRapfi(ctrl),
    ],
  );
};
