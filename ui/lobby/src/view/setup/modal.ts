import { timePickerAndSliders } from 'lib/setup/view/timeControl';
import { hl, type VNode, type LooseVNodes, snabDialog, spinnerVdom } from 'lib/view';

import type LobbyController from '@/ctrl';

import { colorButtons } from './components/colorButtons';
import { gameModeButtons } from './components/gameModeButtons';
import { ratingDifferenceSliders } from './components/ratingDifferenceSliders';
import { ratingView } from './components/ratingView';

const siteText = (key: string, fallback: string): string =>
  ((i18n.site as unknown as Record<string, string | undefined>)[key] as string | undefined) || fallback;

export default function setupModal(ctrl: LobbyController): VNode[] | null {
  const { setupCtrl } = ctrl;
  if (!setupCtrl.gameType) return null;
  const buttonText = {
    hook: siteText('omokCreateRoomAction', 'Create omok room'),
    friend: setupCtrl.friendUser
      ? `${siteText('omokInvitePlayerAction', 'Invite a player')}: ${setupCtrl.friendUser}`
      : siteText('omokInvitePlayerAction', 'Invite a player'),
    ai: siteText('omokPlayRapfiAction', 'Play against Rapfi AI'),
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
        hl('h2#lobby-setup-modal-title', siteText('omokSetupTitle', 'Omok game setup')),
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
    timePickerAndSliders(ctrl.setupCtrl.timeControl, 0),
    gameModeButtons(ctrl),
    ratingView(ctrl),
    ratingDifferenceSliders(ctrl),
    colorButtons(ctrl.setupCtrl),
  ],
  friend: (ctrl: LobbyController): LooseVNodes => [
    omokRuleInfo(ctrl),
    timePickerAndSliders(ctrl.setupCtrl.timeControl, 0),
    gameModeButtons(ctrl),
    colorButtons(ctrl.setupCtrl),
  ],
  ai: ({ setupCtrl }: LobbyController): LooseVNodes => [
    omokRuleInfo({ setupCtrl }),
    timePickerAndSliders(setupCtrl.timeControl, setupCtrl.minimumTimeIfReal()),
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
        hl('span.name', i18n.site.omokBrandName),
        hl('span.desc', i18n.site.omokRuleSetDescription),
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
                'Taraguchi-10',
              ),
            ]
          : []),
        hl('option', { attrs: { value: 'renju', selected: setupCtrl.omokRuleSet() === 'renju' } }, 'Renju'),
        hl(
          'option',
          { attrs: { value: 'freestyle', selected: setupCtrl.omokRuleSet() === 'freestyle' } },
          'Freestyle',
        ),
      ],
    ),
  ]);

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
          hl('span.name', siteText('omokBrowserEngineLabel', 'Rapfi browser engine')),
          hl(
            'span.desc',
            siteText(
              'omokBrowserEngineDescription',
              'Engine settings for this device. Rapfi runs in your browser, not on the game server.',
            ),
          ),
        ]),
      ]),
    ]),
    hl('div.omok-ai-config__grid', [
      numberInput(
        'sf_omok_threads',
        siteText('threads', 'Threads'),
        setupCtrl.omokAiThreads(),
        1,
        16,
        1,
        setupCtrl.omokAiThreads,
        siteText('omokThreadsHelp', 'Used when SharedArrayBuffer is available.'),
      ),
      numberInput(
        'sf_omok_move_time',
        siteText('omokThinkTimeLabel', 'Think time (ms)'),
        setupCtrl.omokAiMoveTimeMs(),
        50,
        120000,
        50,
        setupCtrl.omokAiMoveTimeMs,
        siteText('omokThinkTimeHelp', 'Primary limit for browser-side move generation.'),
      ),
      numberInput(
        'sf_omok_depth',
        siteText('omokDepthLimitLabel', 'Depth limit'),
        setupCtrl.omokAiDepth(),
        0,
        64,
        1,
        setupCtrl.omokAiDepth,
        siteText('omokDepthHelp', '0 means no explicit depth cap.'),
      ),
      numberInput(
        'sf_omok_nodes',
        siteText('omokNodeLimitLabel', 'Node limit'),
        setupCtrl.omokAiNodes(),
        0,
        1000000000,
        1000,
        setupCtrl.omokAiNodes,
        siteText('omokNodesHelp', '0 means no explicit node cap.'),
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
      hl('span', siteText('omokAnalysisPanelToggle', 'Show Rapfi analysis in the round view')),
    ]),
  ]);
