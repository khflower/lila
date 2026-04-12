import { hasFeature, isMobile } from 'lib/device';

import type { OmokAiConfigData, OmokRoundMove, OmokRoundPosition } from './interfaces';

type RapfiRule = 0 | 1 | 2;

export interface OmokRapfiLimits {
  threads: number;
  moveTimeMs?: number;
  depth?: number;
  nodes?: number;
  hashSizeMb?: number;
  ruleSet?: string;
}

export type OmokRapfiUpdateHandler = (analysis: OmokRapfiAnalysis) => void;

export interface OmokRapfiAnalysisLine {
  moves: OmokRoundMove[];
  score?: string;
  winrate?: number;
  nodes?: number;
  depth?: number;
}

export interface OmokRapfiAnalysis {
  bestMove?: OmokRoundMove;
  score?: string;
  winrate?: number;
  nodes?: number;
  depth?: number;
  millis?: number;
  raw?: string[];
  lines: OmokRapfiAnalysisLine[];
}

export interface OmokRapfiStatus {
  supported: boolean;
  ready: boolean;
  computing: boolean;
  threaded: boolean;
  error?: string;
}

export type OmokTaraguchiAiAction =
  | { type: 'swap' }
  | { type: 'candidates' }
  | { type: 'place'; move: OmokRoundMove };

type OutputEvent =
  | { type: 'ready' }
  | { type: 'move'; move: OmokRoundMove }
  | { type: 'swap' }
  | { type: 'depth'; value: number }
  | { type: 'nodes'; value: number }
  | { type: 'time'; value: number }
  | { type: 'eval'; value: string }
  | { type: 'winrate'; value: number }
  | { type: 'pv'; rank: number }
  | { type: 'bestline'; value: OmokRoundMove[] }
  | { type: 'error'; value: string }
  | { type: 'message'; value: string };

const boardFiles = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
const boardSizeDefault = 15;
const centerKey = 'H8';
const transforms = ['identity', 'rot90', 'rot180', 'rot270', 'mirrorX', 'mirrorY', 'diag', 'antiDiag'] as const;

interface OpeningGuideFourthItem {
  move: string;
  bestEvalIndex?: number;
  bestEvalLabel?: string;
  candidateCount?: number;
}

interface OpeningGuideFifthItem {
  rank?: number;
  move: string;
  evalIndex?: number;
  evalLabel?: string;
  summary?: string;
  page?: number;
  catalog?: number;
  title?: string;
  sourceLabel?: string;
  note?: string;
}

interface OpeningGuideData {
  prefix3: Record<string, OpeningGuideFourthItem[]>;
  prefix4: Record<string, OpeningGuideFifthItem[]>;
}

let openingGuidePromise: Promise<OpeningGuideData> | undefined;

const keyFor = (row: number, col: number): string => `${boardFiles[col] || '?'}${row + 1}`;

const toEngineCoord = (move: OmokRoundMove, boardSize: number): [number, number] => [move.col, boardSize - 1 - move.row];

const fromEngineCoord = (x: number, y: number, boardSize: number): OmokRoundMove => {
  const row = boardSize - 1 - y;
  return { row, col: x, key: keyFor(row, x) };
};

const parseMoveList = (tail: string, boardSize: number): OmokRoundMove[] =>
  (tail.match(/\d+,\d+/g) || []).map(token => {
    const [x, y] = token.split(',').map(Number);
    return fromEngineCoord(x, y, boardSize);
  });

const parseKey = (key: string): { key: string; row: number; col: number } => ({
  key,
  col: boardFiles.indexOf(key[0]?.toUpperCase() || ''),
  row: Number(key.slice(1)) - 1,
});

const roundMoveFromKey = (key: string): OmokRoundMove => {
  const parsed = parseKey(key);
  return { key, row: parsed.row, col: parsed.col };
};

const currentPrefix = (position: OmokRoundPosition, length: number): string =>
  (position.moves || [])
    .slice(0, length)
    .map(move => move.key)
    .join(' ');

const compareExpandedItems = (
  a: OpeningGuideFourthItem | OpeningGuideFifthItem,
  b: OpeningGuideFourthItem | OpeningGuideFifthItem,
): number => {
  if ('rank' in a || 'rank' in b) {
    return ((a as OpeningGuideFifthItem).rank ?? Number.MAX_SAFE_INTEGER) -
      ((b as OpeningGuideFifthItem).rank ?? Number.MAX_SAFE_INTEGER) ||
      a.move.localeCompare(b.move);
  }
  return ((a as OpeningGuideFourthItem).bestEvalIndex ?? Number.MAX_SAFE_INTEGER) -
    ((b as OpeningGuideFourthItem).bestEvalIndex ?? Number.MAX_SAFE_INTEGER) ||
    a.move.localeCompare(b.move);
};

const applyTransform = (dx: number, dy: number, transform: (typeof transforms)[number]): [number, number] => {
  switch (transform) {
    case 'identity':
      return [dx, dy];
    case 'rot90':
      return [-dy, dx];
    case 'rot180':
      return [-dx, -dy];
    case 'rot270':
      return [dy, -dx];
    case 'mirrorX':
      return [dx, -dy];
    case 'mirrorY':
      return [-dx, dy];
    case 'diag':
      return [dy, dx];
    case 'antiDiag':
      return [-dy, -dx];
  }
};

const transformKey = (key: string, transform: (typeof transforms)[number]): string => {
  const center = parseKey(centerKey);
  const pos = parseKey(key);
  const dx = pos.col - center.col;
  const dy = pos.row - center.row;
  const [tx, ty] = applyTransform(dx, dy, transform);
  const col = center.col + tx;
  const row = center.row + ty;
  if (col < 0 || col >= boardSizeDefault || row < 0 || row >= boardSizeDefault) return key;
  return `${boardFiles[col]}${row + 1}`;
};

const transformMoves = (moves: string[], transform: (typeof transforms)[number]): string[] =>
  moves.map(move => transformKey(move, transform));

const transformFourthItem = (
  item: OpeningGuideFourthItem,
  transform: (typeof transforms)[number],
): OpeningGuideFourthItem => ({
  ...item,
  move: transformKey(item.move, transform),
});

const transformFifthItem = (
  item: OpeningGuideFifthItem,
  transform: (typeof transforms)[number],
): OpeningGuideFifthItem => ({
  ...item,
  move: transformKey(item.move, transform),
});

const expandPrefixMap = <T extends OpeningGuideFourthItem | OpeningGuideFifthItem>(
  source: Record<string, T[]>,
  transformItem: (item: T, transform: (typeof transforms)[number]) => T,
): Record<string, T[]> => {
  const expanded: Record<string, Map<string, T>> = {};
  for (const [prefix, items] of Object.entries(source || {})) {
    const baseMoves = String(prefix).split(' ');
    for (const transform of transforms) {
      const transformedPrefix = transformMoves(baseMoves, transform).join(' ');
      const bucket = (expanded[transformedPrefix] ||= new Map<string, T>());
      for (const item of items) {
        const transformed = transformItem(item, transform);
        const prev = bucket.get(transformed.move);
        if (!prev || compareExpandedItems(transformed, prev) < 0) bucket.set(transformed.move, transformed);
      }
    }
  }
  return Object.fromEntries(
    Object.entries(expanded).map(([prefix, bucket]) => [
      prefix,
      [...bucket.values()].sort((a, b) => compareExpandedItems(a, b)),
    ]),
  );
};

const expandOpeningGuide = (data: { prefix3?: Record<string, OpeningGuideFourthItem[]>; prefix4?: Record<string, OpeningGuideFifthItem[]> }): OpeningGuideData => ({
  prefix3: expandPrefixMap(data.prefix3 || {}, transformFourthItem),
  prefix4: expandPrefixMap(data.prefix4 || {}, transformFifthItem),
});

const loadOpeningGuide = async (): Promise<OpeningGuideData> => {
  if (!openingGuidePromise) {
    const url = site.asset.url('omok/opening-guide-data.json', { documentOrigin: true });
    openingGuidePromise = fetch(url, { cache: 'no-store' })
      .then(async res => {
        if (!res.ok) throw new Error(`opening guide data request failed: ${res.status}`);
        return res.json();
      })
      .then(raw => expandOpeningGuide(raw));
  }
  return openingGuidePromise;
};

const randomChoice = <T>(items: T[]): T | undefined =>
  items.length ? items[Math.floor(Math.random() * items.length)] : undefined;

const weightedEqualishEval = (evalIndex?: number): number =>
  ({
    0: 0.01,
    1: 0.03,
    2: 0.08,
    3: 0.32,
    4: 1,
    5: 0.32,
    6: 0.08,
    7: 0.03,
    8: 0.01,
  })[evalIndex ?? 4] || 0.01;

const weightedChoice = <T>(items: T[], weightOf: (item: T) => number): T | undefined => {
  const weighted = items
    .map(item => ({ item, weight: Math.max(0, weightOf(item)) }))
    .filter(entry => entry.weight > 0);
  const total = weighted.reduce((sum, entry) => sum + entry.weight, 0);
  if (!total) return weighted[0]?.item;
  let pick = Math.random() * total;
  for (const entry of weighted) {
    pick -= entry.weight;
    if (pick <= 0) return entry.item;
  }
  return weighted.length ? weighted[weighted.length - 1]!.item : undefined;
};

const isBlackAdvantageOrBetter = (evalIndex?: number): boolean => (evalIndex ?? 9) <= 3;

const currentOpeningSeat = (position: OmokRoundPosition): Color | undefined => {
  const seat = position.opening?.activeSeat ?? position.turn;
  return seat === 'white' || seat === 'black' ? seat : undefined;
};

const shouldSwapOpening = (opening: OmokRoundPosition['opening']): boolean => !!opening?.canSwap && Math.random() < 0.5;

const shouldSwapFinalFifth = (position: OmokRoundPosition, prefix4: OpeningGuideFifthItem[]): boolean => {
  if (!position.opening?.canSwap) return false;
  const actor = currentOpeningSeat(position);
  const lastMoveKey = position.lastMove?.key || position.moves?.[4]?.key;
  const fifth = lastMoveKey ? prefix4.find(item => item.move === lastMoveKey) : undefined;
  const evalIndex = fifth?.evalIndex;
  if (evalIndex === undefined || !actor) return Math.random() < 0.5;
  if (evalIndex < 4) return actor !== 'black';
  if (evalIndex > 4) return actor !== 'white';
  return Math.random() < 0.5;
};

const shouldStartCandidates = (items: OpeningGuideFifthItem[]): boolean =>
  items.length >= 10 && items.slice(0, 10).every(item => isBlackAdvantageOrBetter(item.evalIndex));

export const chooseSelectedCandidate = (
  current: OmokRoundMove[],
  reference: Map<string, OpeningGuideFifthItem>,
): OmokRoundMove | undefined =>
  current
    .map((move, index) => ({ move, index }))
    .sort((a, b) => {
      const aRef = reference.get(a.move.key);
      const bRef = reference.get(b.move.key);
      const aUnknown = aRef ? 0 : 1;
      const bUnknown = bRef ? 0 : 1;
      return (
        bUnknown - aUnknown ||
        (bRef?.evalIndex ?? -1) - (aRef?.evalIndex ?? -1) ||
        (aRef?.rank ?? 999) - (bRef?.rank ?? 999) ||
        a.index - b.index ||
        a.move.key.localeCompare(b.move.key)
      );
    })[0]?.move;

const occupiedMoveKeys = (position: OmokRoundPosition): Set<string> => new Set((position.moves || []).map(move => move.key));

const openingRangeMoves = (position: OmokRoundPosition): OmokRoundMove[] => {
  const radius = position.opening?.rangeRadius;
  if (radius === undefined || radius < 0) return [];
  const occupied = occupiedMoveKeys(position);
  const center = parseKey(centerKey);
  const moves: OmokRoundMove[] = [];
  for (let row = center.row - radius; row <= center.row + radius; row++) {
    for (let col = center.col - radius; col <= center.col + radius; col++) {
      if (row < 0 || row >= boardSizeDefault || col < 0 || col >= boardSizeDefault) continue;
      const key = keyFor(row, col);
      if (occupied.has(key)) continue;
      moves.push({ key, row, col });
    }
  }
  return moves;
};

const fallbackTaraguchiPlacement = (position: OmokRoundPosition): OmokRoundMove | undefined =>
  randomChoice(openingRangeMoves(position));

export const pickTaraguchiAiAction = async (
  position: OmokRoundPosition,
): Promise<OmokTaraguchiAiAction | undefined> => {
  if (position.ruleSet !== 'taraguchi10' || !position.opening) return;

  const opening = position.opening;
  const data = await loadOpeningGuide();
  const prefix3 = data.prefix3[currentPrefix(position, 3)] || [];
  const prefix4 = data.prefix4[currentPrefix(position, 4)] || [];

  if (opening.candidateMode) {
    if (!opening.candidateSelection && opening.candidateCount < opening.candidateTarget) {
      const taken = new Set((opening.candidates || []).map(move => move.key));
      const candidatePool = shouldStartCandidates(prefix4) ? prefix4.slice(0, opening.candidateTarget) : prefix4;
      const next = candidatePool.find(item => !taken.has(item.move));
      return next ? { type: 'place', move: roundMoveFromKey(next.move) } : undefined;
    }
    const currentCandidates = opening.candidates || [];
    const reference = new Map(prefix4.map(item => [item.move, item]));
    const selected = chooseSelectedCandidate(currentCandidates, reference);
    return selected ? { type: 'place', move: selected } : undefined;
  }

  switch (position.ply) {
    case 1:
    case 2: {
      if (shouldSwapOpening(opening)) return { type: 'swap' };
      const pick = fallbackTaraguchiPlacement(position);
      return pick ? { type: 'place', move: pick } : undefined;
    }
    case 3: {
      if (shouldSwapOpening(opening)) return { type: 'swap' };
      const pick = randomChoice(prefix3);
      if (pick) return { type: 'place', move: roundMoveFromKey(pick.move) };
      const fallback = fallbackTaraguchiPlacement(position);
      return fallback ? { type: 'place', move: fallback } : undefined;
    }
    case 4: {
      if (opening.canStartCandidates && shouldStartCandidates(prefix4)) return { type: 'candidates' };
      if (shouldSwapOpening(opening)) return { type: 'swap' };
      const pick = weightedChoice(prefix4, item => weightedEqualishEval(item.evalIndex));
      if (pick) return { type: 'place', move: roundMoveFromKey(pick.move) };
      const fallback = fallbackTaraguchiPlacement(position);
      return fallback ? { type: 'place', move: fallback } : undefined;
    }
    case 5:
      if (shouldSwapFinalFifth(position, prefix4)) return { type: 'swap' };
      return;
    default:
      return;
  }
};

const mapRule = (ruleSet?: string): RapfiRule => {
  switch ((ruleSet || '').toLowerCase()) {
    case 'renju':
    case 'taraguchi10':
      return 2;
    case 'freestyle':
      return 0;
    default:
      return 2;
  }
};

const normalizeLimits = (config?: Partial<OmokAiConfigData> | OmokRapfiLimits): OmokRapfiLimits => ({
  threads: Math.max(1, Math.min(16, config?.threads || (isMobile() ? 1 : 2))),
  moveTimeMs: config?.moveTimeMs && config.moveTimeMs > 0 ? config.moveTimeMs : undefined,
  depth: config?.depth && config.depth > 0 ? config.depth : undefined,
  nodes: config?.nodes && config.nodes > 0 ? config.nodes : undefined,
  hashSizeMb:
    config?.hashSizeMb && config.hashSizeMb > 0 ? Math.max(16, Math.min(1024, Math.round(config.hashSizeMb))) : undefined,
  ruleSet: config?.ruleSet,
});

const cloneAnalysis = (analysis: OmokRapfiAnalysis): OmokRapfiAnalysis => ({
  ...analysis,
  raw: analysis.raw ? [...analysis.raw] : undefined,
  lines: analysis.lines.map(line => ({
    ...line,
    moves: [...line.moves],
  })),
});

const positionKey = (position: OmokRoundPosition): string => {
  const opening = position.opening;
  const openingKey = opening
    ? [
        opening.activeSeat,
        opening.canSwap ? 1 : 0,
        opening.canStartCandidates ? 1 : 0,
        opening.candidateMode ? 1 : 0,
        opening.candidateSelection ? 1 : 0,
        opening.forceSimpleFifth ? 1 : 0,
        opening.candidateCount,
        opening.candidateTarget,
        (opening.candidates || []).map(move => move.key).join(','),
      ].join('|')
    : '-';
  return `${position.ruleSet}|${position.ply}|${(position.moves || []).map(m => m.key).join(',')}|${openingKey}`;
};

export class OmokRapfiEngine {
  private worker?: Worker;
  private ready = false;
  private computing = false;
  private bootPromise?: Promise<void>;
  private analysisToken = 0;
  private boardSize = boardSizeDefault;
  private threaded = false;
  private error?: string;
  private activePv = 1;
  private currentAnalysis?: OmokRapfiAnalysis;
  private updateHandler?: { token: number; onUpdate?: OmokRapfiUpdateHandler };

  status(): OmokRapfiStatus {
    return {
      supported: typeof Worker !== 'undefined',
      ready: this.ready,
      computing: this.computing,
      threaded: this.threaded,
      error: this.error,
    };
  }

  async init(preferredThreads?: number): Promise<void> {
    if (this.bootPromise) return this.bootPromise;
    if (typeof Worker === 'undefined') {
      this.error = 'Web Worker is not available in this browser.';
      throw new Error(this.error);
    }

    this.threaded = hasFeature('sharedMem');
    const script = this.threaded ? 'rapfi-multi.js' : 'rapfi-single.js';
    const scriptUrl = site.asset.url(`omok/rapfi/${script}`, { documentOrigin: true });

    this.bootPromise = new Promise<void>((resolve, reject) => {
      const worker = new Worker(scriptUrl);
      const onMessage = (event: MessageEvent) => {
        const data = event.data;
        if (data?.ready) {
          if (this.ready) return;
          this.worker = worker;
          this.ready = true;
          this.error = undefined;
          const threads = normalizeLimits({ threads: preferredThreads }).threads;
          this.send(`INFO THREAD_NUM ${threads}`);
          resolve();
        } else if (data?.output) {
          this.handleOutput(String(data.output));
        }
      };
      const onError = (event: ErrorEvent) => {
        this.error = event.message || 'Failed to initialize Rapfi.';
        worker.terminate();
        reject(new Error(this.error));
      };
      worker.addEventListener('message', onMessage);
      worker.addEventListener('error', onError);
    });
    return this.bootPromise;
  }

  async bestMove(
    position: OmokRoundPosition,
    limits: OmokRapfiLimits,
    onUpdate?: OmokRapfiUpdateHandler,
  ): Promise<OmokRoundMove | 'swap'> {
    await this.init(limits.threads);
    const analysis = await this.run(position, limits, 1, onUpdate);
    if (analysis.bestMove) return analysis.bestMove;
    throw new Error(this.error || 'Rapfi did not return a best move.');
  }

  async analyse(
    position: OmokRoundPosition,
    limits: OmokRapfiLimits,
    multiPv = 2,
    onUpdate?: OmokRapfiUpdateHandler,
  ): Promise<OmokRapfiAnalysis> {
    await this.init(limits.threads);
    return this.run(position, limits, multiPv, onUpdate);
  }

  stop(): void {
    if (!this.worker || !this.ready) return;
    this.send('YXSTOP');
    this.computing = false;
  }

  destroy(): void {
    this.stop();
    this.worker?.terminate();
    this.worker = undefined;
    this.ready = false;
    this.bootPromise = undefined;
    this.error = undefined;
  }

  private async run(
    position: OmokRoundPosition,
    rawLimits: OmokRapfiLimits,
    multiPv: number,
    onUpdate?: OmokRapfiUpdateHandler,
  ): Promise<OmokRapfiAnalysis> {
    const limits = normalizeLimits(rawLimits);
    this.boardSize = position.boardSize || boardSizeDefault;
    this.analysisToken += 1;
    const token = this.analysisToken;
    this.currentAnalysis = { lines: [], raw: [] };
    this.updateHandler = { token, onUpdate };
    this.activePv = 1;
    this.computing = true;

    this.send(`START ${this.boardSize}`);
    this.send(`INFO RULE ${mapRule(limits.ruleSet || position.ruleSet)}`);
    this.send(`INFO THREAD_NUM ${limits.threads}`);
    if (limits.hashSizeMb) this.send(`INFO HASH_SIZE ${limits.hashSizeMb}`);
    this.send(`INFO TIMEOUT_TURN ${limits.moveTimeMs || 800}`);
    this.send(`INFO TIMEOUT_MATCH ${(limits.moveTimeMs || 800) * 20}`);
    this.send(`INFO TIME_LEFT ${(limits.moveTimeMs || 800) * 20}`);
    if (limits.depth) this.send(`INFO MAX_DEPTH ${limits.depth}`);
    if (limits.nodes) this.send(`INFO MAX_NODE ${limits.nodes}`);
    this.send(`INFO SWAPABLE ${position.opening?.canSwap ? 1 : 0}`);
    this.sendBoard(position);

    return new Promise<OmokRapfiAnalysis>((resolve, reject) => {
      const startedAt = performance.now();
      const timeoutMs = Math.max(4000, (limits.moveTimeMs || 800) * 5);
      const checkDone = (event: OutputEvent) => {
        if (token !== this.analysisToken) return;
        if (event.type === 'move') {
          this.computing = false;
          const base = this.currentAnalysis || { lines: [], raw: [] };
          const analysis: OmokRapfiAnalysis = {
            ...base,
            bestMove: event.move,
            millis: Math.round(performance.now() - startedAt),
          };
          this.currentAnalysis = analysis;
          cleanup();
          resolve(analysis);
        } else if (event.type === 'swap') {
          this.computing = false;
          cleanup();
          resolve({
            ...(this.currentAnalysis || { lines: [], raw: [] }),
            bestMove: undefined,
            raw: [...(this.currentAnalysis?.raw || []), 'SWAP'],
            millis: Math.round(performance.now() - startedAt),
          });
        } else if (event.type === 'error') {
          this.computing = false;
          cleanup();
          reject(new Error(event.value));
        }
      };
      const listener = (event: Event) => checkDone((event as CustomEvent<OutputEvent>).detail);
      const timer = window.setTimeout(() => {
        this.stop();
        cleanup();
        reject(new Error('Rapfi analysis timed out.'));
      }, timeoutMs);
      const cleanup = () => {
        clearTimeout(timer);
        if (this.updateHandler?.token === token) this.updateHandler = undefined;
        window.removeEventListener(this.eventName(token), listener as EventListener);
      };
      window.addEventListener(this.eventName(token), listener as EventListener);
      this.send(`YXNBEST ${Math.max(1, multiPv)}`);
    });
  }

  private eventName(token: number): string {
    return `omok-rapfi-${token}`;
  }

  private sendBoard(position: OmokRoundPosition): void {
    const moves = position.moves || [];
    let command = 'YXBOARD';
    let side = 1;
    for (const move of moves) {
      const [x, y] = toEngineCoord(move, this.boardSize);
      command += ` ${x},${y},${side}`;
      side = side === 1 ? 2 : 1;
    }
    command += ' DONE';
    this.send(command);
  }

  private send(command: string): void {
    this.worker?.postMessage(command);
  }

  private handleOutput(output: string): void {
    const event = this.parseOutput(output);
    if (!event) return;

    const token = this.analysisToken;
    const analysis = (this.currentAnalysis ||= { lines: [], raw: [] });
    if (analysis.raw) analysis.raw.push(output);

    switch (event.type) {
      case 'pv':
        this.activePv = Math.max(1, event.rank);
        while (analysis.lines.length < this.activePv) analysis.lines.push({ moves: [] });
        break;
      case 'depth':
        (analysis.lines[this.activePv - 1] ||= { moves: [] }).depth = event.value;
        analysis.depth = event.value;
        break;
      case 'nodes':
        (analysis.lines[this.activePv - 1] ||= { moves: [] }).nodes = event.value;
        analysis.nodes = event.value;
        break;
      case 'time':
        analysis.millis = event.value;
        break;
      case 'eval':
        (analysis.lines[this.activePv - 1] ||= { moves: [] }).score = event.value;
        analysis.score = event.value;
        break;
      case 'winrate':
        (analysis.lines[this.activePv - 1] ||= { moves: [] }).winrate = event.value;
        analysis.winrate = event.value;
        break;
      case 'bestline':
        (analysis.lines[this.activePv - 1] ||= { moves: [] }).moves = event.value;
        break;
      case 'error':
        this.error = event.value;
        break;
      case 'ready':
      case 'move':
      case 'swap':
      case 'message':
        break;
    }

    if (
      this.updateHandler?.token === token &&
      this.updateHandler.onUpdate &&
      (event.type === 'pv' ||
        event.type === 'depth' ||
        event.type === 'nodes' ||
        event.type === 'time' ||
        event.type === 'eval' ||
        event.type === 'winrate' ||
        event.type === 'bestline')
    ) {
      this.updateHandler.onUpdate(cloneAnalysis(analysis));
    }

    window.dispatchEvent(new CustomEvent(this.eventName(token), { detail: event }));
  }

  private parseOutput(output: string): OutputEvent | undefined {
    const trimmed = output.trim();
    if (!trimmed || trimmed === 'OK') return;
    if (trimmed === 'SWAP') return { type: 'swap' };
    if (trimmed === 'Running...') return { type: 'ready' };

    const headSep = trimmed.indexOf(' ');
    if (headSep < 0) {
      const coord = trimmed.split(',').map(Number);
      if (coord.length === 2 && coord.every(Number.isFinite)) {
        return { type: 'move', move: fromEngineCoord(coord[0], coord[1], this.boardSize) };
      }
      return { type: 'message', value: trimmed };
    }

    const head = trimmed.slice(0, headSep);
    const tail = trimmed.slice(headSep + 1);
    if (head === 'INFO') {
      const nextSep = tail.indexOf(' ');
      const infoKey = nextSep < 0 ? tail : tail.slice(0, nextSep);
      const infoTail = nextSep < 0 ? '' : tail.slice(nextSep + 1);
      switch (infoKey) {
        case 'MULTIPV':
        case 'NUMPV':
          return { type: 'pv', rank: Number(infoTail) || 1 };
        case 'DEPTH':
          return { type: 'depth', value: Number(infoTail) || 0 };
        case 'TOTALNODES':
        case 'NODES':
          return { type: 'nodes', value: Number(infoTail) || 0 };
        case 'TOTALTIME':
          return { type: 'time', value: Number(infoTail) || 0 };
        case 'EVAL':
          return { type: 'eval', value: infoTail };
        case 'WINRATE':
          return { type: 'winrate', value: Number(infoTail) || 0 };
        case 'BESTLINE':
          return { type: 'bestline', value: parseMoveList(infoTail, this.boardSize) };
      }
    }
    if (head === 'ERROR') return { type: 'error', value: tail };
    if (head === 'MESSAGE') return { type: 'message', value: tail };
    return { type: 'message', value: trimmed };
  }
}

export const omokRapfiDefaults = (config?: OmokAiConfigData): OmokRapfiLimits =>
  normalizeLimits({
    threads: config?.threads || (isMobile() ? 1 : 2),
    moveTimeMs: Math.min(config?.moveTimeMs || 5000, 30000),
    depth: config?.depth,
    nodes: config?.nodes,
    hashSizeMb: config?.hashSizeMb || 32,
    ruleSet: config?.ruleSet || 'renju',
  });

export const omokPositionKey: (position: OmokRoundPosition) => string = positionKey;
