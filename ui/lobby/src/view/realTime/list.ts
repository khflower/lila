import { h } from 'snabbdom';

import perfIcons from 'lib/game/perfIcons';
import * as licon from 'lib/licon';
import { bind } from 'lib/view';

import type LobbyController from '@/ctrl';
import * as hookRepo from '@/hookRepo';
import type { Hook } from '@/interfaces';

import { tds, perfNames } from '../util';

function omokRuleSetLabel(ruleSet?: Hook['omokRuleSet']) {
  switch (ruleSet) {
    case 'taraguchi10':
      return 'Taraguchi-10';
    case 'renju':
      return 'Renju';
    case 'freestyle':
      return 'Freestyle';
    default:
      return '';
  }
}

function renderPlayer(ctrl: LobbyController, hook: Hook) {
  const player =
    ctrl.me && hook.u
      ? h('span.ulink.ulpt.mobile-powertip', { attrs: { 'data-href': '/@/' + hook.u } }, hook.u)
      : h('span', i18n.site.anonymous);
  return h('span.hook__player', [
    player,
    hook.omokRuleSet ? h('span.hook__player__sub', 'Omok room') : null,
  ]);
}

function renderMode(hook: Hook) {
  const modeLabel = i18n.site[hook.ra ? 'rated' : 'casual'];
  const ruleSet = omokRuleSetLabel(hook.omokRuleSet);
  return h('span.hook__mode', [
    h('span', { attrs: { 'data-icon': perfIcons[hook.perf] } }, modeLabel),
    ruleSet ? h('span.hook__mode__sub', ruleSet) : null,
  ]);
}

function renderHook(ctrl: LobbyController, hook: Hook) {
  return h(
    'tr.hook.' + hook.action,
    {
      key: hook.id,
      class: { disabled: !!hook.disabled },
      attrs: {
        role: 'button',
        title: hook.disabled
          ? ''
          : hook.action === 'join'
            ? i18n.site.joinTheGame + ' | ' + perfNames[hook.perf]
            : i18n.site.cancel,
        'data-id': hook.id,
      },
    },
    tds([
      renderPlayer(ctrl, hook),
      ...(!ctrl.me ? [] : !ctrl.opts.showRatings ? [''] : [hook.rating + (hook.prov ? '?' : '')]),
      hook.clock,
      renderMode(hook),
    ]),
  );
}

const isStandard = (value: boolean) => (hook: Hook) => (hook.variant === 'standard') === value;

const isMine = (hook: Hook) => hook.action === 'cancel';

const isNotMine = (hook: Hook) => !isMine(hook);

export const toggle = (ctrl: LobbyController) =>
  h('button.toggle', {
    key: 'set-mode-chart',
    attrs: { title: i18n.site.graph, 'data-icon': licon.LineGraph },
    hook: bind('click', _ => ctrl.setMode('chart'), ctrl.redraw),
  });

export const render = (ctrl: LobbyController, allHooks: Hook[]) => {
  const mine = allHooks.find(isMine),
    max = mine ? 13 : 14,
    hooks = allHooks.slice(0, max),
    render = (hook: Hook) => renderHook(ctrl, hook),
    standards = hooks.filter(isNotMine).filter(isStandard(true));
  hookRepo.sort(ctrl, standards);
  const variants = hooks
    .filter(isNotMine)
    .filter(isStandard(false))
    .slice(0, Math.max(0, max - standards.length - 1));
  hookRepo.sort(ctrl, variants);
  const renderedHooks = [
    ...standards.map(render),
    variants.length
      ? h('tr.variants', { key: 'variants' }, [
          h('td', { attrs: { colspan: 5 } }, '— ' + i18n.site.variant + ' —'),
        ])
      : null,
    ...variants.map(render),
  ];
  if (mine) renderedHooks.unshift(render(mine));
  return h('table.hooks__list', [
    h(
      'thead',
      h('tr', [
        h('th'),
        ctrl.me
          ? h(
              'th',
              {
                class: { sortable: true, sort: ctrl.sort === 'rating' },
                hook: bind('click', _ => ctrl.setSort('rating'), ctrl.redraw),
              },
              [h('i.is'), i18n.site.rating],
            )
          : null,
        h(
          'th',
          ctrl.me
            ? {
                key: 'time-header-with-rating',
                class: { sortable: true, sort: ctrl.sort === 'time' },
                hook: bind('click', _ => ctrl.setSort('time'), ctrl.redraw),
              }
            : {
                key: 'time-header-without-rating',
              },
          [h('i.is'), i18n.site.time],
        ),
        h('th', [h('i.is'), i18n.site.mode]),
      ]),
    ),
    h(
      'tbody',
      {
        class: { stepping: ctrl.stepping },
        hook: bind(
          'click',
          async e => {
            let el = e.target as HTMLElement;
            do {
              el = el.parentNode as HTMLElement;
              if (el.nodeName === 'TR') return ctrl.clickHook(el.dataset['id']!);
            } while (el.nodeName !== 'TABLE');
          },
          ctrl.redraw,
        ),
      },
      renderedHooks,
    ),
  ]);
};
