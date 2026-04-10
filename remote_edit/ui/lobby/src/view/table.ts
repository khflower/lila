import { numberFormat } from 'lib/i18n';
import { bind, onInsert, hl, thunk } from 'lib/view';

import type LobbyController from '@/ctrl';
import type { GameType } from '@/interfaces';

import renderSetupModal from './setup/modal';

type ButtonInfo = { gameType: GameType | 'opening-guide' | 'solo' | 'dev' | 'bots'; label: string; disabled?: boolean; title?: string };

const siteText = (key: string, fallback: string): string =>
  ((i18n.site as unknown as Record<string, string | undefined>)[key] as string | undefined) || fallback;

export default function table(ctrl: LobbyController) {
  const { data, opts } = ctrl;
  const hasOngoingRealTimeGame = ctrl.hasOngoingRealTimeGame();
  const hookDisabled =
    opts.playban || opts.hasUnreadLichessMessage || ctrl.me?.isBot || hasOngoingRealTimeGame;
  const { members, rounds } = data.counters;
  const lobbyButtons: ButtonInfo[] = [
    {
      gameType: 'hook',
      label: siteText('omokCreateRoomAction', 'Create omok room'),
      disabled: hookDisabled,
      title: i18n.site.omokRoomDescription,
    },
    {
      gameType: 'opening-guide',
      label: siteText('omokOpeningGuide', 'Opening guide'),
      title: siteText(
        'omokOpeningGuideDescription',
        'Explore workbook-backed Taraguchi opening moves without using hints during live games.',
      ),
    },
    {
      gameType: 'solo',
      label: siteText('omokSoloBoardAction', 'Solo board'),
      title: siteText('omokSoloBoardDescription', 'Place stones freely on an empty board with Undo and Reset.'),
    },
    {
      gameType: 'friend',
      label: siteText('omokInvitePlayerAction', 'Invite a player'),
      disabled: hasOngoingRealTimeGame,
      title: i18n.site.omokFriendDescription,
    },
    {
      gameType: 'ai',
      label: siteText('omokPlayRapfiAction', 'Play against Rapfi AI'),
      disabled: hasOngoingRealTimeGame,
      title: i18n.site.omokAiMatchDescription,
    },
  ];
  if (opts.bots)
    lobbyButtons.push({
      gameType: 'bots',
      label: 'play bot',
    });

  return hl('div.lobby__table', [
    hl('div.lobby__start', [site.blindMode && hl('h2', siteText('omokQuickMatch', 'Quick match')), lobbyButtons.map(makeLobbyButton)]),
    renderSetupModal(ctrl),
    site.blindMode
      ? undefined
      : thunk(
          'div.lobby__counters',
          () =>
            hl('div.lobby__counters', [
              hl(
                'a',
                { attrs: { href: '/player' } },
                i18n.site.nbPlayers.asArray(
                  members,
                  hl(
                    'strong',
                    {
                      attrs: { 'data-count': members },
                      hook: onInsert<HTMLAnchorElement>(elm => {
                        ctrl.spreadPlayersNumber = ctrl.initNumberSpreader(elm, 10, members);
                      }),
                    },
                    numberFormat(members),
                  ),
                ),
              ),
              hl(
                'a',
                { attrs: { href: '/games' } },
                i18n.site.nbGamesInPlay.asArray(
                  rounds,
                  hl(
                    'strong',
                    {
                      attrs: { 'data-count': rounds },
                      hook: onInsert<HTMLAnchorElement>(elm => {
                        ctrl.spreadGamesNumber = ctrl.initNumberSpreader(elm, 8, rounds);
                      }),
                    },
                    numberFormat(rounds),
                  ),
                ),
              ),
            ]),
          [],
        ),
  ]);

  function makeLobbyButton({ gameType, label, disabled, title }: ButtonInfo) {
    return hl(
      `button.button.button-metal.lobby__start__button.lobby__start__button--${gameType}`,
      {
        class: { active: ctrl.setupCtrl.gameType === gameType, disabled: !!disabled },
        attrs: { type: 'button', title: title ?? '', 'aria-disabled': disabled ? 'true' : 'false' },
        hook: disabled
          ? {}
          : bind(
              'click',
              () => {
                if (gameType === 'opening-guide') location.href = '/dev/omok/opening-guide';
                else if (gameType === 'solo') location.href = '/omok/solo';
                else if (gameType === 'bots') location.href = '/bots';
                else if (gameType === 'dev') location.href = '/bots/dev';
                else ctrl.setupCtrl.openModal(gameType);
              },
              ctrl.redraw,
            ),
      },
      label,
    );
  }
}
