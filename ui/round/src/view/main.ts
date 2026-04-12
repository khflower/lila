import { render as renderKeyboardMove } from 'keyboardMove';
import { renderVoiceBar } from 'voice';

import { displayColumns, isTouchDevice } from 'lib/device';
import { playable } from 'lib/game';
import { renderMaterialDiffs } from 'lib/game/view/material';
import { storage } from 'lib/storage';
import { stepwiseScroll, type VNode, hl, bind } from 'lib/view';
import { renderBlindfoldToggle } from 'lib/view/blindfold';

import crazyView from '../crazy/crazyView';
import type RoundController from '../ctrl';
import { render as renderGround } from '../ground';
import { renderOmokPlaceholder } from './omokPlaceholder';
import { renderOmokState } from './omokState';
import { next, prev, view } from '../keyboard';
import { renderTable } from './table';

export function main(ctrl: RoundController): VNode {
  const d = ctrl.data,
    topColor = d[ctrl.flip ? 'player' : 'opponent'].color,
    bottomColor = d[ctrl.flip ? 'opponent' : 'player'].color,
    materialDiffs = renderMaterialDiffs(
      ctrl.data.pref.showCaptured,
      ctrl.flip ? ctrl.data.opponent.color : ctrl.data.player.color,
      ctrl.stepAt(ctrl.ply).fen,
      !!(ctrl.data.player.checks || ctrl.data.opponent.checks), // showChecks
      ctrl.data.steps,
      ctrl.ply,
    );
  const hideBoard = ctrl.data.player.blindfold && playable(ctrl.data);
  const isOmok = !!ctrl.data.omok;
  return ctrl.nvui
    ? ctrl.nvui.render()
    : hl(
        'div.round__app.variant-' + d.game.variant.key,
        {
          class: {
            'swap-clock': isTouchDevice() && displayColumns() === 1 && storage.boolean('swapClock').get(),
          },
          attrs: isOmok
            ? {
                'data-board-game': 'omok',
                'data-board-ready': 'placeholder',
              }
            : undefined,
        },
        [
          renderBlindfoldToggle(ctrl.blindfold),
          hl(
            'div.round__app__board.main-board' + (hideBoard ? '.blindfold' : ''),
            {
              attrs: isOmok
                ? {
                    'data-board-game': 'omok',
                    'data-board-ready': 'placeholder',
                  }
                : undefined,
              hook:
                'ontouchstart' in window || !storage.boolean('scrollMoves').getOrDefault(true)
                  ? undefined
                  : bind(
                      'wheel',
                      stepwiseScroll(
                        e => {
                          if (e.deltaY > 0) next(ctrl);
                          else if (e.deltaY < 0) prev(ctrl);
                          ctrl.redraw();
                        },
                        () => ctrl.isPlaying(),
                      ),
                      undefined,
                      false,
                    ),
            },
            [
              isOmok
                ? hl('div.round__app__board__cg-wrap-hidden', { style: { display: 'none' } }, [renderGround(ctrl)])
                : renderGround(ctrl),
              ctrl.promotion.view(ctrl.data.game.variant.key === 'antichess'),
              renderOmokPlaceholder(ctrl),
            ],
          ),
          isOmok ? renderOmokState(ctrl) : undefined,
          ctrl.voiceMove && renderVoiceBar(ctrl.voiceMove.ctrl, ctrl.redraw),
          ctrl.keyboardHelp && view(ctrl),
          crazyView(ctrl, topColor, 'top') || materialDiffs[0],
          renderTable(ctrl),
          crazyView(ctrl, bottomColor, 'bottom') || materialDiffs[1],
          ctrl.keyboardMove && renderKeyboardMove(ctrl.keyboardMove),
        ],
      );
}

export function endGameView(): void {
  const $body = $('body');
  if ($body.hasClass('zen-auto') && $body.hasClass('zen')) {
    $body.toggleClass('zen');
    window.dispatchEvent(new Event('resize'));
  }
}
