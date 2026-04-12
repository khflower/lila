import { numberFormat } from 'lib/i18n';
import { bind, onInsert, hl, thunk } from 'lib/view';

import type LobbyController from '@/ctrl';
import type { GameType, OngoingOmokGame } from '@/interfaces';

import renderSetupModal from './setup/modal';

type ButtonInfo = {
  gameType: GameType | 'opening-guide' | 'solo' | 'dev' | 'bots';
  label: string;
  disabled?: boolean;
  title?: string;
};

const isKoLocale = (): boolean =>
  document.documentElement.lang?.toLowerCase().startsWith('ko') || location.pathname.startsWith('/ko');

const siteText = (key: string, fallback: string): string =>
  ((i18n.site as unknown as Record<string, string | undefined>)[key] as string | undefined) || fallback;

const omokText = (key: string, enFallback: string, koFallback: string): string => {
  const translated = siteText(key, enFallback);
  return isKoLocale() && translated === enFallback ? koFallback : translated;
};

const formatUpdatedAt = (updatedAt: string): string => {
  const date = new Date(updatedAt);
  if (Number.isNaN(date.getTime())) return isKoLocale() ? '최근 활동' : 'recent activity';
  const timeText = date.toLocaleTimeString(isKoLocale() ? 'ko-KR' : undefined, { hour: '2-digit', minute: '2-digit' });
  return isKoLocale() ? `${timeText} 활동` : `active ${timeText}`;
};

export default function table(ctrl: LobbyController) {
  const { data, opts } = ctrl;
  const hasOngoingRealTimeGame = ctrl.hasOngoingRealTimeGame();
  const hookDisabled =
    opts.playban || opts.hasUnreadLichessMessage || ctrl.me?.isBot || hasOngoingRealTimeGame;
  const { members, rounds } = data.counters;
  const lobbyButtons: ButtonInfo[] = [
    {
      gameType: 'hook',
      label: omokText('omokCreateRoomAction', 'Create omok room', '오목 방 만들기'),
      disabled: hookDisabled,
      title: omokText(
        'omokRoomDescription',
        'Create a custom omok room that any online player can join.',
        '온라인 플레이어가 바로 참가할 수 있는 오목 방을 만듭니다.',
      ),
    },
    {
      gameType: 'opening-guide',
      label: omokText('omokOpeningGuide', 'Opening guide', '오프닝 가이드'),
      title: omokText(
        'omokOpeningGuideDescription',
        'Explore workbook-backed Taraguchi opening moves without using hints during live games.',
        '실전 힌트 없이 워크북 기반 Taraguchi 오프닝 수순을 살펴봅니다.',
      ),
    },
    {
      gameType: 'solo',
      label: omokText('omokSoloBoardAction', 'Solo board', '솔로 보드'),
      title: omokText(
        'omokSoloBoardDescription',
        'Place stones freely on an empty board with Undo and Reset.',
        '빈판에 돌을 자유롭게 놓고 실행 취소와 초기화를 할 수 있습니다.',
      ),
    },
    {
      gameType: 'friend',
      label: omokText('omokInvitePlayerAction', 'Invite a player', '플레이어 초대'),
      disabled: hasOngoingRealTimeGame,
      title: omokText(
        'omokFriendDescription',
        'Create a private omok invitation link and share it with a specific player.',
        '비공개 초대 링크를 만들어 원하는 플레이어에게 보냅니다.',
      ),
    },
    {
      gameType: 'ai',
      label: omokText('omokPlayRapfiAction', 'Play against Rapfi AI', 'Rapfi AI와 대국'),
      disabled: hasOngoingRealTimeGame,
      title: omokText(
        'omokAiMatchDescription',
        'Start an omok game against Rapfi AI running in your browser.',
        '브라우저에서 실행되는 Rapfi AI와 오목 대국을 시작합니다.',
      ),
    },
  ];
  if (opts.bots)
    lobbyButtons.push({
      gameType: 'bots',
      label: isKoLocale() ? '봇 대국' : 'play bot',
    });

  return hl('div.lobby__table', [
    hl('div.lobby__start', [
      site.blindMode && hl('h2', omokText('omokQuickMatch', 'Quick match', '빠른 대국')),
      lobbyButtons.map(makeLobbyButton),
    ]),
    renderSetupModal(ctrl),
    renderOngoingOmokGames(ctrl.opts.ongoingOmokGames),
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

  function renderOngoingOmokGames(games?: OngoingOmokGame[]) {
    if (!games?.length) return undefined;
    return hl('div.lobby__box.lobby__box--ongoing-omok', [
      hl('h2', isKoLocale() ? '진행 중인 오목 경기' : 'Ongoing omok games'),
      hl(
        'div.lobby__box__content',
        hl(
          'ul',
          games.map(game =>
            hl('li', [
              hl('a', { attrs: { href: game.url } }, `${game.white} vs ${game.black}`),
              hl('span.mini', ` · ${game.clock} · ${formatUpdatedAt(game.updatedAt)}`),
            ]),
          ),
        ),
      ),
    ]);
  }

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
