import type RoundController from './ctrl';
import type { RoundOpts } from './interfaces';

export const isOmokRound = (opts: Pick<RoundOpts, 'data'>): boolean => !!opts.data.omok;

export async function bootOmokPlaceholder(
  opts: RoundOpts,
  roundMain: (opts: RoundOpts) => Promise<RoundController>,
): Promise<RoundController> {
  document.body.dataset.roundMode = 'omok';
  const el = (opts.element ?? document.querySelector('.round__app')) as HTMLElement | null;
  if (el) {
    el.dataset.boardGame = 'omok';
    el.dataset.boardReady = 'placeholder';
  }
  return roundMain(opts);
}
