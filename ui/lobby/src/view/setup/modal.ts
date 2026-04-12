import { timePickerAndSliders } from 'lib/setup/view/timeControl';
import { hl, type VNode, type LooseVNodes, snabDialog, spinnerVdom } from 'lib/view';

import type LobbyController from '@/ctrl';

import { colorButtons } from './components/colorButtons';
import { gameModeButtons } from './components/gameModeButtons';
import { ratingDifferenceSliders } from './components/ratingDifferenceSliders';
import { ratingView } from './components/ratingView';

const isKoLocale = (): boolean =>
  document.documentElement.lang?.toLowerCase().startsWith('ko') || location.pathname.startsWith('/ko');

const siteText = (key: string, fallback: string): string =>
  ((i18n.site as unknown as Record<string, string | undefined>)[key] as string | undefined) || fallback;

const omokText = (key: string, enFallback: string, koFallback: string): string => {
  const translated = siteText(key, enFallback);
  return isKoLocale() && translated === enFallback ? koFallback : translated;
};

const clockSystemLabels = (clockSystem: 'fischer' | 'byoyomi') =>
  clockSystem === 'byoyomi'
    ? {
        time: omokText('omokMainTimeLabel', 'Main time (min)', '본시간(분)'),
        increment: omokText('omokByoyomiPeriodLabel', 'Byo-yomi period (sec)', '초읽기 시간(초)'),
        realTimeDescription: omokText(
          'omokByoyomiDescription',
          'Byo-yomi is shown separately, but live room creation still uses a Fischer-only clock engine today.',
          '초읽기는 피셔와 별개로 구분해 두었지만, 지금 실시간 방 생성 엔진은 아직 피셔만 지원합니다.',
        ),
      }
    : {
        time: omokText('omokBaseTimeLabel', 'Base time (min)', '기본 시간(분)'),
        increment: omokText('omokFischerIncrementLabel', 'Fischer increment (sec)', '피셔 추가 시간(초)'),
        realTimeDescription: omokText(
          'omokFischerDescription',
          'Each move adds the selected increment to that player clock.',
          '피셔는 매 수마다 설정한 초가 그 플레이어 시간에 누적 추가됩니다.',
        ),
      };

export default function setupModal(ctrl: LobbyController): VNode[] | null {
  const { setupCtrl } = ctrl;
  if (!setupCtrl.gameType) return null;
  const buttonText = {
    hook: omokText('omokCreateRoomAction', 'Create omok room', '오목 방 만들기'),
    friend: setupCtrl.friendUser
      ? `${omokText('omokInvitePlayerAction', 'Invite a player', '플레이어 초대')}: ${setupCtrl.friendUser}`
      : omokText('omokInvitePlayerAction', 'Invite a player', '플레이어 초대'),
    ai: omokText('omokPlayRapfiAction', 'Play against Rapfi AI', 'Rapfi AI와 대국'),
  }[setupCtrl.gameType];
  const disabled = !setupCtrl.valid() || setupCtrl.loading;
  return [
    snabDialog({
      attrs: { dialog: { 'aria-labelledBy': 'lobby-setup-modal-title', 'aria-modal': 'true', open: 'open' } },
      class: 'game-setup',
      css: [{ hashed: 'lobby.setup' }],
      onClose: () => {
        setupCtrl.closeModal = undefined;
        setupCtrl.gameType = null;
        setupCtrl.root.redraw();
      },
      modal: true,
      vnodes: [
        hl('h2#lobby-setup-modal-title', omokText('omokSetupTitle', 'Omok game setup', '오목 대국 설정')),
        hl('div.setup-content', views[setupCtrl.gameType](ctrl)),
        hl('div.footer', [
          hl(
            `button.button.button-metal.lobby__start__button.lobby__start__button--${setupCtrl.friendUser ? 'friend-user' : setupCtrl.gameType}`,
            {
              attrs: { disabled },
              class: { disabled },
              on: { click: setupCtrl.submit },
            },
            buttonText,
          ),
          setupCtrl.loading && spinnerVdom(),
        ]),
      ],
    }),
  ].filter(v => v !== null);
}

const views = {
  hook: (ctrl: LobbyController): LooseVNodes => [
    omokRuleInfo(ctrl),
    omokClockSystemPicker(ctrl),
    timePickerAndSliders(ctrl.setupCtrl.timeControl, 0, clockSystemLabels(ctrl.setupCtrl.omokClockSystem())),
    gameModeButtons(ctrl),
    ratingView(ctrl),
    ratingDifferenceSliders(ctrl),
    colorButtons(ctrl.setupCtrl),
  ],
  friend: (ctrl: LobbyController): LooseVNodes => [
    omokRuleInfo(ctrl),
    omokClockSystemPicker(ctrl),
    timePickerAndSliders(ctrl.setupCtrl.timeControl, 0, clockSystemLabels(ctrl.setupCtrl.omokClockSystem())),
    gameModeButtons(ctrl),
    colorButtons(ctrl.setupCtrl),
  ],
  ai: ({ setupCtrl }: LobbyController): LooseVNodes => [
    omokRuleInfo({ setupCtrl }),
    omokClockSystemPicker({ setupCtrl }),
    timePickerAndSliders(
      setupCtrl.timeControl,
      setupCtrl.minimumTimeIfReal(),
      clockSystemLabels(setupCtrl.omokClockSystem()),
    ),
    omokAiConfig({ setupCtrl }),
    colorButtons(setupCtrl),
  ],
};

const omokRuleInfo = (
  { setupCtrl }: { setupCtrl: LobbyController['setupCtrl'] },
  allowTaraguchi = true,
): VNode =>
  hl('div.mselect.omok-rule-info.label-select', [
    hl('label.mselect__label', { attrs: { for: 'sf_omok_rule_set' } }, [
      hl('span.icon', '?'),
      hl('div.text', [
        hl('span.name', omokText('omokBrandName', 'Omok.dev', '오목')), 
        hl(
          'span.desc',
          omokText(
            'omokRuleSetDescription',
            'Choose Taraguchi-10 for swap opening, Renju for center-start, or Freestyle for an empty board.',
            'Taraguchi-10은 스왑 오프닝, Renju는 중앙 시작, Freestyle은 빈판 자유룰입니다.',
          ),
        ),
      ]),
    ]),
    hl(
      'select#sf_omok_rule_set',
      {
        on: {
          change: (e: Event) =>
            setupCtrl.omokRuleSet((e.target as HTMLSelectElement).value as 'taraguchi10' | 'renju' | 'freestyle'),
        },
      },
      [
        ...(allowTaraguchi
          ? [
              hl(
                'option',
                { attrs: { value: 'taraguchi10', selected: setupCtrl.omokRuleSet() === 'taraguchi10' } },
                isKoLocale() ? 'Taraguchi-10 (스왑 오프닝)' : 'Taraguchi-10',
              ),
            ]
          : []),
        hl(
          'option',
          { attrs: { value: 'renju', selected: setupCtrl.omokRuleSet() === 'renju' } },
          isKoLocale() ? 'Renju (중앙 시작)' : 'Renju',
        ),
        hl(
          'option',
          { attrs: { value: 'freestyle', selected: setupCtrl.omokRuleSet() === 'freestyle' } },
          isKoLocale() ? 'Freestyle (자유룰)' : 'Freestyle',
        ),
      ],
    ),
  ]);

const omokClockSystemPicker = ({ setupCtrl }: { setupCtrl: LobbyController['setupCtrl'] }): VNode => {
  const selected = setupCtrl.omokClockSystem();
  const byoYomiPending = selected === 'byoyomi';

  return hl('div.mselect.omok-rule-info.label-select', [
    hl('label.mselect__label', { attrs: { for: 'sf_omok_clock_system' } }, [
      hl('span.icon', '⏱'),
      hl('div.text', [
        hl('span.name', omokText('omokClockSystemLabel', 'Clock system', '시간 방식')),
        hl(
          'span.desc',
          byoYomiPending
            ? omokText(
                'omokClockSystemByoyomiDesc',
                'Byo-yomi is separated in the UI, but live room creation is still limited to Fischer clocks.',
                '초읽기는 피셔와 따로 구분해 두었지만, 지금 실시간 방 생성은 아직 피셔만 바로 지원합니다.',
              )
            : omokText(
                'omokClockSystemFischerDesc',
                'Current live rooms use Fischer time, base time plus per-move increment.',
                '현재 실시간 방은 피셔 방식입니다. 기본 시간에 매 수 추가 시간이 붙습니다.',
              ),
        ),
      ]),
    ]),
    hl(
      'select#sf_omok_clock_system',
      {
        on: {
          change: (e: Event) => setupCtrl.omokClockSystem((e.target as HTMLSelectElement).value as 'fischer' | 'byoyomi'),
        },
      },
      [
        hl(
          'option',
          { attrs: { value: 'fischer', selected: selected === 'fischer' } },
          isKoLocale() ? '피셔 (기본 시간 + 추가 시간)' : 'Fischer (base time + increment)',
        ),
        hl(
          'option',
          { attrs: { value: 'byoyomi', selected: selected === 'byoyomi' } },
          isKoLocale() ? '초읽기 (표시만, 생성 보류)' : 'Byo-yomi (listed, creation pending)',
        ),
      ],
    ),
  ]);
};

const numberInput = (
  id: string,
  label: string,
  value: number,
  min: number,
  max: number,
  step: number,
  onChange: (value: number) => void,
  help: string,
): VNode =>
  hl('label.omok-ai-config__field', { attrs: { for: id } }, [
    hl('span.omok-ai-config__field-label', label),
    hl('input.omok-ai-config__field-input', {
      attrs: {
        id,
        type: 'number',
        min,
        max,
        step,
        value: String(value),
      },
      on: {
        change: (e: Event) => onChange(Number((e.target as HTMLInputElement).value) || 0),
      },
    }),
    help ? hl('span.omok-ai-config__field-help', help) : undefined,
  ]);

const omokAiConfig = ({ setupCtrl }: { setupCtrl: LobbyController['setupCtrl'] }): VNode =>
  hl('div.omok-ai-config', [
    hl('div.mselect.omok-rule-info.label-select', [
      hl('label.mselect__label', [
        hl('span.icon', 'R'),
        hl('div.text', [
          hl('span.name', omokText('omokBrowserEngineLabel', 'Rapfi browser engine', 'Rapfi 브라우저 엔진')),
          hl(
            'span.desc',
            omokText(
              'omokBrowserEngineDescription',
              'Engine settings for this device. Rapfi runs in your browser, not on the game server.',
              '이 기기용 엔진 설정입니다. Rapfi는 서버가 아니라 지금 브라우저에서 실행됩니다.',
            ),
          ),
        ]),
      ]),
    ]),
    hl('div.omok-ai-config__grid', [
      numberInput(
        'sf_omok_threads',
        omokText('threads', 'Threads', '스레드 수'),
        setupCtrl.omokAiThreads(),
        1,
        16,
        1,
        setupCtrl.omokAiThreads,
        omokText('omokThreadsHelp', 'Used when SharedArrayBuffer is available.', 'SharedArrayBuffer가 가능할 때 사용됩니다.'),
      ),
      numberInput(
        'sf_omok_move_time',
        omokText('omokThinkTimeLabel', 'Think time (ms)', '생각 시간 (ms)'),
        setupCtrl.omokAiMoveTimeMs(),
        50,
        120000,
        50,
        setupCtrl.omokAiMoveTimeMs,
        omokText('omokThinkTimeHelp', 'Primary limit for browser-side move generation.', '브라우저 수 생성의 기본 제한입니다.'),
      ),
      numberInput(
        'sf_omok_depth',
        omokText('omokDepthLimitLabel', 'Depth limit', '탐색 깊이 제한'),
        setupCtrl.omokAiDepth(),
        0,
        64,
        1,
        setupCtrl.omokAiDepth,
        omokText('omokDepthHelp', '0 means no explicit depth cap.', '0이면 깊이 제한을 두지 않습니다.'),
      ),
      numberInput(
        'sf_omok_nodes',
        omokText('omokNodeLimitLabel', 'Node limit', '노드 수 제한'),
        setupCtrl.omokAiNodes(),
        0,
        1000000000,
        1000,
        setupCtrl.omokAiNodes,
        omokText('omokNodesHelp', '0 means no explicit node cap.', '0이면 노드 수 제한을 두지 않습니다.'),
      ),
    ]),
    hl('label.omok-ai-config__toggle', [
      hl('input', {
        attrs: {
          type: 'checkbox',
          checked: setupCtrl.omokAiAnalysisEnabled(),
        },
        on: {
          change: (e: Event) => setupCtrl.omokAiAnalysisEnabled((e.target as HTMLInputElement).checked),
        },
      }),
      hl('span', omokText('omokAnalysisPanelToggle', 'Show Rapfi analysis in the round view', '대국 화면에서 Rapfi 분석 보기')),
    ]),
  ]);
