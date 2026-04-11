/// <reference types="../types/ab" />

import type { DrawShape } from '@lichess-org/chessground/draw';
import { opposite, uciToMove } from '@lichess-org/chessground/util';
import * as ab from 'ab';
import { ctrl as makeKeyboardMove, type KeyboardMove } from 'keyboardMove';
import { makeVoiceMove, type VoiceMove } from 'voice';

import { defined, type Toggle, type Prop, toggle, requestIdleCallback, memoize } from 'lib';
import * as game from 'lib/game';
import { plyToTurn } from 'lib/game/chess';
import { ClockCtrl, type ClockOpts } from 'lib/game/clock/clockCtrl';
import type { MoveRootCtrl } from 'lib/game/moveRootCtrl';
import { PromotionCtrl, promote } from 'lib/game/promotion';
import { readFen, almostSanOf, speakable } from 'lib/game/sanWriter';
import { playing } from 'lib/game/status';
import viewStatus from 'lib/game/view/status';
import * as licon from 'lib/licon';
import notify from 'lib/notification';
import * as poolRangeStorage from 'lib/poolRangeStorage';
import { Replay } from 'lib/prefs';
import { pubsub } from 'lib/pubsub';
import { wsIsOpen } from 'lib/socket';
import { type SocketSendOpts } from 'lib/socket';
import { storage, once, storedBooleanProp, type LichessBooleanStorage } from 'lib/storage';
import type { NodeCrazy } from 'lib/tree/types';
import { toggleZenMode } from 'lib/view/zen';
import * as wakeLock from 'lib/wakeLock';

import * as atomic from './atomic';
import * as blur from './blur';
import * as cevalSub from './cevalSub';
import { CorresClockController } from './corresClock/corresClockCtrl';
import { valid as crazyValid, init as crazyInit, onEnd as crazyEndHook } from './crazy/crazyCtrl';
import { boardOrientation, reload as groundReload } from './ground';
import type {
  Step,
  RoundOpts,
  RoundData,
  SocketMove,
  SocketDrop,
  MoveMetadata,
  NvuiPlugin,
  RoundTour,
  ApiMove,
  ApiOmokMove,
  ApiEnd,
  EventsWithPayload,
} from './interfaces';
import { init as keyboardInit } from './keyboard';
import MoveOn from './moveOn';
import { isOmokTerminal } from './omok';
import {
  OmokRapfiEngine,
  omokPositionKey,
  omokRapfiDefaults,
  pickTaraguchiAiAction,
  type OmokRapfiAnalysis,
} from './omokRapfi';
import Server from './server';
import { make as makeSocket, type RoundSocket } from './socket';
import * as title from './title';
import TransientMove from './transientMove';
import * as util from './util';
import { endGameView } from './view/main';
import { getOmokStatusSummary } from './view/omokState';
import { userTxt } from './view/user';
import * as xhr from './xhr';

type GoneBerserk = Partial<ByColor<boolean>>;

const taraguchiRangeRadius = (ply: number): number | undefined => {
  if (ply >= 1 && ply <= 4) return ply;
  return undefined;
};

const omokAiDisplayName = 'Rapfi AI';

const taraguchiInstruction = (ply: number): string => {
  switch (ply) {
    case 1:
      return 'White may swap, or place the second move within 3x3 from H8.';
    case 2:
      return 'Black may swap, or place the third move within 5x5 from H8.';
    case 3:
      return 'White may swap, or place the fourth move within 7x7 from H8.';
    case 4:
      return 'Black may swap, place one fifth move within 9x9, or propose 10 fifth-move candidates.';
    case 5:
      return 'White may make the final swap, or continue Renju play.';
    default:
      return 'Continue under Renju rules.';
  }
};

const ensureTaraguchiOpening = (data: RoundData): void => {
  const position = data.omok?.position;
  if (!position || (position.ruleSet ?? data.omok?.ruleset) !== 'taraguchi10' || position.opening) return;
  const turn = position.turn === 'white' || position.turn === 'black' ? position.turn : data.game.player;
  position.opening = {
    activeSeat: turn,
    canSwap: position.ply >= 1 && position.ply <= 5,
    canStartCandidates: position.ply === 4,
    candidateMode: false,
    candidateSelection: false,
    forceSimpleFifth: false,
    candidateCount: 0,
    candidateTarget: 10,
    candidates: [],
    history: position.ply >= 1 ? ['1. Black placed H8 (fixed center)'] : [],
    rangeRadius: taraguchiRangeRadius(position.ply),
    instruction: taraguchiInstruction(position.ply),
  };
};

export default class RoundController implements MoveRootCtrl {
  data: RoundData;
  socket: RoundSocket;
  chessground: CgApi;
  clock?: ClockCtrl;
  corresClock?: CorresClockController;
  keyboardMove?: KeyboardMove;
  voiceMove?: VoiceMove;
  moveOn: MoveOn;
  promotion: PromotionCtrl;
  ply: number;
  firstSeconds = true;
  flip = false;
  menu: Toggle;
  confirmMoveToggle: Toggle;
  loading = false;
  loadingTimeout: number;
  omokPlacementPending = false;
  omokPlacementPendingTimeout: number;
  redirecting = false;
  transientMove?: TransientMove;
  toSubmit?: SocketMove | SocketDrop;
  goneBerserk: GoneBerserk = {};
  resignConfirm?: Timeout = undefined;
  drawConfirm?: Timeout = undefined;
  preventDrawOffer?: Timeout = undefined;
  // will be replaced by view layer
  autoScroll: () => void = () => {};
  justDropped?: Role;
  justCaptured?: Piece;
  shouldSendMoveTime = false;
  preDrop?: Role;
  sign: string = Math.random().toString(36);
  keyboardHelp: boolean = location.hash === '#keyboard';
  blindfoldStorage: LichessBooleanStorage;
  server: Server;
  nvui?: NvuiPlugin;
  vibration: Prop<boolean> = storedBooleanProp('vibration', false);
  omokRapfi?: OmokRapfiEngine;
  omokAnalysis?: OmokRapfiAnalysis;
  omokAnalysisLoading = false;
  omokAnalysisEnabled = false;
  omokAnalysisError?: string;
  omokAiPendingKey?: string;
  omokAnalysisKey?: string;
  omokUiCopyObserver?: MutationObserver;
  omokUiCopyRefreshPending = false;

  constructor(
    readonly opts: RoundOpts,
    readonly redraw: Redraw,
  ) {
    util.upgradeServerData(opts.data);
    ensureTaraguchiOpening(opts.data);
    if (opts.data.omok) opts.data.expiration = undefined;

    const d = (this.data = opts.data);
    this.omokAnalysisEnabled = !!d.omok?.ai?.analysisEnabled;

    this.ply = util.lastPly(d);
    this.goneBerserk[d.player.color] = d.player.berserk;
    this.goneBerserk[d.opponent.color] = d.opponent.berserk;
    setTimeout(() => {
      this.firstSeconds = false;
      this.redraw();
    }, 3000);
    this.socket = d.local ?? makeSocket(opts.socketSend!, this);
    this.blindfoldStorage = storage.boolean(`blindfold.${this.data.player.user?.id ?? 'anon'}`);

    this.updateClockCtrl();
    this.promotion = new PromotionCtrl(
      f => f(this.chessground),
      () => {
        this.chessground.cancelPremove();
        xhr.reload(this.data).then(this.reload, site.reload);
      },
      this.redraw,
      d.pref.autoQueen,
    );

    this.setQuietMode();
    this.confirmMoveToggle = toggle(d.pref.submitMove);
    this.moveOn = new MoveOn(this, 'move-on');
    if (!d.local) this.transientMove = new TransientMove(this.socket);
    this.server = new Server(() => this.data);

    this.menu = toggle(false, redraw);
    const nvuiPromise = site.blindMode && site.asset.loadEsm<NvuiPlugin>('round.nvui', { init: this });
    setTimeout(async () => {
      if (nvuiPromise) this.nvui = await nvuiPromise;
      this.delayedInit();
    }, 200);

    setTimeout(this.showExpiration, 350);
    if (d.omok) setTimeout(() => this.scheduleOmokUiCopyRefresh(), 300);

    if (!document.referrer?.includes('/serviceWorker.')) setTimeout(this.showYourMoveNotification, 500);

    // at the end:
    pubsub.on('jump', ply => {
      this.jump(parseInt(ply));
      this.redraw();
    });

    pubsub.on('socket.open', this.onSocketOpen);
    pubsub.on('zen', toggleZenMode);

    if (!this.opts.noab && this.isPlaying()) ab.init(this);
  }

  private readonly showExpiration = () => {
    if (!this.data.expiration) return;
    this.redraw();
    setTimeout(this.showExpiration, 250);
  };

  private readonly scheduleOmokUiCopyRefresh = () => {
    if (!this.data.omok || this.omokUiCopyRefreshPending) return;
    this.omokUiCopyRefreshPending = true;
    requestAnimationFrame(() => {
      this.omokUiCopyRefreshPending = false;
      this.refreshOmokUiCopy();
    });
  };

  private readonly ensureOmokUiCopyObserver = (): void => {
    if (!this.data.omok) {
      document.body.classList.remove('omok-dev-round');
      this.omokUiCopyObserver?.disconnect();
      this.omokUiCopyObserver = undefined;
      return;
    }

    document.body.classList.add('omok-dev-round');
    if (this.omokUiCopyObserver) return;
    const root = document.querySelector('.round') || document.body;
    this.omokUiCopyObserver = new MutationObserver(() => this.scheduleOmokUiCopyRefresh());
    this.omokUiCopyObserver.observe(root, {
      childList: true,
      subtree: true,
      characterData: true,
    });
  };

  private readonly onUserMove = (orig: Key, dest: Key, meta: MoveMetadata) => {
    if (!this.keyboardMove?.usedSan) ab.move(this, meta, pubsub.emit);
    if (!this.startPromotion(orig, dest, meta)) this.sendMove(orig, dest, undefined, meta);
  };

  private readonly onUserNewPiece = (role: Role, key: Key, meta: MoveMetadata) => {
    if (!this.replaying() && crazyValid(this.data, role, key)) {
      this.sendNewPiece(role, key, !!meta.predrop);
    } else this.jump(this.ply);
  };

  private readonly onMove = (orig: Key, dest: Key, captured?: Piece) => {
    if (captured || this.enpassant(orig, dest)) {
      if (this.data.game.variant.key === 'atomic') {
        site.sound.play('explosion');
        atomic.capture(this, dest);
      } else site.sound.move({ name: 'capture', filter: 'game' });
    } else site.sound.move({ name: 'move', filter: 'game' });
  };

  private readonly startPromotion = (orig: Key, dest: Key, meta: MoveMetadata) =>
    this.promotion.start(
      orig,
      dest,
      {
        submit: (orig, dest, role) => this.sendMove(orig, dest, role, meta),
        show: this.voiceMove?.promotionHook(),
      },
      meta,
      this.keyboardMove?.justSelected(),
    );

  private readonly onPremove = (orig: Key, dest: Key, meta: MoveMetadata) =>
    this.startPromotion(orig, dest, meta);

  private readonly onCancelPremove = () => this.promotion.cancelPrePromotion();

  private readonly onNewPiece = (piece: Piece, key: Key): void => {
    if (piece.role === 'pawn' && (key[1] === '1' || key[1] === '8')) return;
    site.sound.move();
  };

  private readonly onPredrop = (role: Role | undefined) => {
    this.preDrop = role;
    this.redraw();
  };

  private readonly isSimulHost = () => this.data.simul && this.data.simul.hostId === this.opts.userId;

  private readonly enpassant = (orig: Key, dest: Key): boolean => {
    if (orig[0] === dest[0] || this.chessground.state.pieces.get(dest)?.role !== 'pawn') return false;
    const pos = (dest[0] + orig[1]) as Key;
    this.chessground.setPieces(new Map([[pos, undefined]]));
    return true;
  };

  lastPly = (): number => util.lastPly(this.data);

  makeCgHooks = (): any => ({
    onUserMove: this.onUserMove,
    onUserNewPiece: this.onUserNewPiece,
    onMove: this.data.local ? undefined : this.onMove,
    onNewPiece: this.onNewPiece,
    onPremove: this.onPremove,
    onCancelPremove: this.onCancelPremove,
    onPredrop: this.onPredrop,
  });

  replaying = (): boolean => this.ply !== this.lastPly() && !this.data.local;

  userJump = (ply: Ply): void => {
    this.toSubmit = undefined;
    this.chessground.selectSquare(null);
    if (ply !== this.ply && this.jump(ply)) site.sound.saySan(this.stepAt(this.ply).san, true);
    else this.redraw();
  };

  userJumpPlyDelta = (plyDelta: Ply): void => this.userJump(this.ply + plyDelta);

  isPlaying = (): boolean => game.isPlayerPlaying(this.data);

  jump = (ply: Ply): boolean => {
    ply = Math.max(util.firstPly(this.data), Math.min(this.lastPly(), ply));
    const isForwardStep = ply === this.ply + 1;
    this.ply = ply;
    this.justDropped = undefined;
    this.preDrop = undefined;
    const s = this.stepAt(ply),
      config: CgConfig = {
        fen: s.fen,
        lastMove: uciToMove(s.uci),
        check: !!s.check,
        turnColor: this.ply % 2 === 0 ? 'white' : 'black',
      };
    if (this.replaying()) this.chessground.stop();
    else
      config.movable = {
        color: this.isPlaying() ? this.data.player.color : undefined,
        dests: util.parsePossibleMoves(this.data.possibleMoves),
      };
    this.chessground.cancelPremove();
    this.chessground.set(config);
    if (s.san && isForwardStep) site.sound.move(s);
    this.autoScroll();
    pubsub.emit('ply', ply);
    this.pluginUpdate(s.fen);
    return true;
  };

  omokTurnColor = (): Color | undefined => {
    const turn = this.data.omok?.position.opening?.activeSeat ?? this.data.omok?.position.turn;
    return turn === 'white' || turn === 'black' ? turn : undefined;
  };

  currentTurnColor = (): Color | undefined => this.omokTurnColor() ?? this.data.game.player;

  isPlayerTurn = (): boolean => !this.data.player.spectator && this.currentTurnColor() === this.data.player.color;

  isOmokActive = (): boolean => this.isPlaying() || !!this.data.omok?.ai;

  canPlaceOmok = (): boolean =>
    this.isOmokActive() &&
    !this.data.player.spectator &&
    !isOmokTerminal(this.data) &&
    this.omokTurnColor() === this.data.player.color &&
    !this.omokPlacementPending &&
    !this.replaying() &&
    !this.loading;

  omokAnalysisAvailable = (): boolean => !!this.data.omok;

  omokRapfiStatus = (): ReturnType<OmokRapfiEngine['status']> | undefined => this.omokRapfi?.status();

  omokAiColor = (): Color | undefined => {
    const aiColor = this.data.omok?.ai?.aiColor;
    if (aiColor === 'white' || aiColor === 'black') return aiColor;
    return this.data.opponent.ai ? this.data.opponent.color : undefined;
  };

  isOmokAiTurn = (): boolean =>
    !!this.omokAiColor() &&
    this.isOmokActive() &&
    !this.data.player.spectator &&
    !isOmokTerminal(this.data) &&
    this.omokTurnColor() === this.omokAiColor();

  canMove = (): boolean => !this.replaying() && this.data.player.color === this.chessground.state.turnColor;

  replayEnabledByPref = (): boolean => {
    const d = this.data;
    return (
      d.pref.replay === Replay.Always ||
      (d.pref.replay === Replay.OnlySlowGames &&
        (d.game.speed === 'classical' || d.game.speed === 'correspondence'))
    );
  };

  isLate = (): boolean => this.replaying() && playing(this.data);

  playerAt = (position: game.TopOrBottom): game.Player =>
    this.flip !== (position === 'top') ? this.data.opponent : this.data.player;

  flipNow = (): void => {
    this.flip = !this.nvui && !this.flip;
    this.chessground.set({
      orientation: boardOrientation(this.data, this.flip),
    });
    pubsub.emit('flip', this.flip);
    this.redraw();
  };

  private refreshOmokUiCopy = (): void => {
    if (!this.data.omok || typeof document === 'undefined') return;
    this.ensureOmokUiCopyObserver();

    const summary = getOmokStatusSummary(this);
    const titleLead = summary?.text || 'Play omok';
    const normalizeText = (value: string): string => value.replace(/\s+/g, ' ').trim();
    const titleOpponent = this.data.opponent.ai
      ? omokAiDisplayName
      : (() => {
          const candidate = normalizeText(userTxt(this.data.opponent));
          return candidate && !/^anonymous$/i.test(candidate) ? candidate : 'Opponent';
        })();
    document.title = `${titleLead} - ${titleOpponent} - Omok.dev`;

    const renameAiNode = (selector: string): void => {
      document.querySelectorAll<HTMLElement>(selector).forEach(el => {
        const normalized = normalizeText(el.textContent || '');
        if (/^Stockfish level \d+$/i.test(normalized) || /^Rapfi AI(?:\s+level)?\s*\d+$/i.test(normalized))
          el.textContent = omokAiDisplayName;
      });
    };

    renameAiNode('.ruser name');
    renameAiNode('.ruser');
    renameAiNode('.game__meta__players .user-link');

    document.querySelectorAll<HTMLElement>('.message, rm6, .round__side .message, .round__app .message').forEach(el => {
      const text = normalizeText(el.textContent || '');
      if (!text) return;
      if (/you play the black pieces/i.test(text)) {
        el.textContent = /it's your turn!/i.test(text) ? 'You play Black. Your turn!' : 'You play Black.';
        return;
      }
      if (/you play the white pieces/i.test(text)) {
        el.textContent = /it's your turn!/i.test(text) ? 'You play White. Your turn!' : 'You play White.';
        return;
      }
      if (/it's your turn!/i.test(text)) el.textContent = text.replace(/it's your turn!/gi, 'Your turn!');
    });
  };

  setTitle = (): void => {
    title.set(this);
    if (this.data.omok) this.scheduleOmokUiCopyRefresh();
  };

  actualSendMove = <moveOrDrop extends 'move' | 'drop'>(
    tpe: moveOrDrop,
    data: EventsWithPayload[moveOrDrop],
    meta: MoveMetadata = { premove: false },
  ): void => {
    const socketOpts: SocketSendOpts = {
      sign: this.sign,
      ackable: true,
    };
    if (this.clock) {
      socketOpts.withLag = !this.shouldSendMoveTime || !this.clock.isRunning();
      if (meta.premove && this.shouldSendMoveTime) {
        this.clock.hardStopClock();
        socketOpts.millis = 0;
      } else {
        const moveMillis = this.clock.stopClock();
        if (moveMillis !== undefined && this.shouldSendMoveTime) {
          socketOpts.millis = moveMillis;
        }
      }
    }
    this.socket.send(tpe, data, socketOpts);

    this.justDropped = meta.justDropped;
    this.justCaptured = meta.justCaptured;
    this.preDrop = undefined;
    this.transientMove?.register();
    this.redraw();
  };

  pluginMove = (orig: Key, dest: Key, role?: Role, preConfirmed?: boolean): void => {
    if (!role) {
      this.chessground.move(orig, dest);
      this.chessground.state.movable.dests = undefined;
      this.chessground.state.turnColor = opposite(this.chessground.state.turnColor);

      if (this.startPromotion(orig, dest, { premove: false })) return;
    }
    this.sendMove(orig, dest, role, { premove: false, preConfirmed });
  };

  pluginUpdate = (fen: string): void => {
    this.voiceMove?.update({ fen, canMove: this.canMove() });
    this.keyboardMove?.update({ fen, canMove: this.canMove() });
  };

  sendMove = (orig: Key, dest: Key, prom: Role | undefined, meta: MoveMetadata): void => {
    const move: SocketMove = { u: orig + dest };
    if (prom) move.u += prom === 'knight' ? 'n' : prom[0];
    if (blur.get()) move.b = 1;
    this.resign(false);

    if (!meta.preConfirmed && this.confirmMoveToggle() && !meta.premove) {
      if (site.sound.speech()) {
        const spoken = `${speakable(almostSanOf(readFen(this.stepAt(this.ply).fen), move.u))}. confirm?`;
        site.sound.say(spoken, false, true);
      }
      this.toSubmit = move;
      this.redraw();
      return;
    }
    this.actualSendMove('move', move, { justCaptured: meta.captured, premove: meta.premove });
  };

  sendNewPiece = (role: Role, key: Key, isPredrop: boolean): void => {
    const drop: SocketDrop = { role, pos: key };
    if (blur.get()) drop.b = 1;
    this.resign(false);
    if (this.confirmMoveToggle() && !isPredrop) {
      this.toSubmit = drop;
      this.redraw();
    } else {
      this.actualSendMove('drop', drop, {
        justDropped: role,
        premove: isPredrop,
      });
    }
  };

  showYourMoveNotification = (): void => {
    if (this.data.local) return;
    const d = this.data;
    const opponent = $('body').hasClass('zen') ? 'Your opponent' : userTxt(d.opponent);
    const joined = `${opponent}\njoined the game.`;
    if (game.isPlayerTurn(d))
      notify(() => {
        let txt = i18n.site.yourTurn;
        if (this.ply < 1) txt = `${joined}\n${txt}`;
        else {
          let move = util.lastStep(this.data).san;
          const turn = plyToTurn(this.ply);
          move = `${turn}${this.ply % 2 === 1 ? '.' : '...'} ${move}`;
          txt = `${opponent}\nplayed ${move}.\n${txt}`;
        }
        return txt;
      });
    else if (this.isPlaying() && this.ply < 1) notify(joined);
  };

  playerByColor = (c: Color): game.Player => this.data[c === this.data.player.color ? 'player' : 'opponent'];

  apiMove = (o: ApiMove): true => {
    const d = this.data,
      playing = this.isPlaying();
    d.game.turns = o.ply;
    d.game.player = o.ply % 2 === 0 ? 'white' : 'black';
    const playedColor = o.ply % 2 === 0 ? 'black' : 'white',
      activeColor = d.player.color === d.game.player;
    if (o.status) d.game.status = o.status;
    if (o.winner) d.game.winner = o.winner;
    this.playerByColor('white').offeringDraw = o.wDraw;
    this.playerByColor('black').offeringDraw = o.bDraw;
    d.possibleMoves = activeColor ? o.dests : undefined;
    d.possibleDrops = activeColor ? o.drops : undefined;
    d.crazyhouse = o.crazyhouse;
    this.setTitle();
    if (!this.replaying()) {
      this.ply++;
      if (o.role)
        this.chessground.newPiece(
          {
            role: o.role,
            color: playedColor,
          },
          o.uci.slice(2, 4) as Key,
        );
      else {
        // This block needs to be idempotent, even for castling moves in
        // Chess960.
        const keys = uciToMove(o.uci)!,
          pieces = this.chessground.state.pieces;
        if (
          !o.castle ||
          (pieces.get(o.castle.king[0])?.role === 'king' && pieces.get(o.castle.rook[0])?.role === 'rook')
        ) {
          this.chessground.move(keys[0], keys[1]);
        }
      }
      if (o.promotion) promote(this.chessground, o.promotion.key, o.promotion.pieceClass);
      this.chessground.set({
        turnColor: d.game.player,
        movable: {
          dests: playing ? util.parsePossibleMoves(d.possibleMoves) : new Map(),
        },
        check: !!o.check,
      });
      if (this.googlyEyes) this.chessground.setAutoShapes(this.googlyEyes());
      if (o.status?.name === 'mate') {
        site.sound.play('checkmate', o.volume);
      } else if (o.check) {
        site.sound.play('check', o.volume);
      }
      blur.onMove();
      pubsub.emit('ply', this.ply);
    }
    d.game.threefold = !!o.threefold;
    d.game.fiftyMoves = !!o.fiftyMoves;
    const step = {
      ply: this.lastPly() + 1,
      fen: o.fen,
      san: o.san,
      uci: o.uci,
      check: o.check,
      crazy: o.crazyhouse,
    };
    d.steps.push(step);
    this.justDropped = undefined;
    this.justCaptured = undefined;
    game.setOnGame(d, playedColor, true);
    this.data.forecastCount = undefined;
    if (o.clock) {
      this.shouldSendMoveTime = true;
      const oc = o.clock,
        delay = playing && activeColor ? 0 : oc.lag || 1;
      if (this.clock)
        this.clock.setClock({
          white: oc.white,
          black: oc.black,
          ticking: this.tickingClockColor(),
          delay,
        });
      else if (this.corresClock) this.corresClock.update(oc.white, oc.black);
    }
    if (this.data.expiration) {
      if (this.data.steps.length > 2) this.data.expiration = undefined;
      else this.data.expiration.movedAt = Date.now();
    }
    this.redraw();
    if (playing && playedColor === d.player.color) {
      this.transientMove?.clear();
      this.moveOn.next();
      cevalSub.publish(d, o);
    }
    if (!this.replaying() && playedColor !== d.player.color) {
      if (this.vibration() && 'vibrate' in navigator) navigator.vibrate(100);
      // prevent race conditions with explosions and premoves
      // https://github.com/lichess-org/lila/issues/343
      const premoveDelay = d.game.variant.key === 'atomic' ? 100 : 1;
      setTimeout(() => {
        if (this.nvui) this.nvui.playPremove();
        else if (!this.chessground.playPremove() && !this.playPredrop()) {
          this.promotion.cancel();
          this.showYourMoveNotification();
        }
      }, premoveDelay);
    }
    this.autoScroll();
    this.onChange();
    this.pluginUpdate(step.fen);
    if (!this.data.local) site.sound.move({ ...o, filter: 'music' });
    site.sound.saySan(step.san);
    this.server.alive();
    return true; // prevents default socket pubsub
  };

  apiOmokMove = (o: ApiOmokMove): void => {
    const d = this.data,
      omok = d.omok;
    if (!omok) return;

    const position = o.position;
    ensureTaraguchiOpening({ ...d, omok: { ...omok, position } });
    const activeSeat = position.opening?.activeSeat;
    const turnColor =
      activeSeat === 'white' || activeSeat === 'black'
        ? activeSeat
        : position.turn === 'white' || position.turn === 'black'
          ? position.turn
          : d.game.player;

    omok.position = position;
    if (defined(o.status)) omok.status = o.status;
    if (defined(o.winner)) omok.winner = o.winner;

    d.game.turns = position.ply;
    d.game.player = turnColor;
    this.setOmokPlacementPending(false);
    this.setTitle();

    this.redraw();
    this.onChange();
    this.server.alive();
    this.refreshOmokRapfi();
  };

  crazyValid = (role: Role, key: Key): boolean => crazyValid(this.data, role, key);

  getCrazyhousePockets = (): NodeCrazy['pockets'] | undefined => this.data.crazyhouse?.pockets;

  private readonly playPredrop = () => {
    return this.chessground.playPredrop(drop => {
      return crazyValid(this.data, drop.role, drop.key);
    });
  };

  private clearJust() {
    this.justDropped = undefined;
    this.justCaptured = undefined;
    this.preDrop = undefined;
  }

  reload = (d: RoundData): void => {
    const posChanged = d.steps.length !== this.data.steps.length;
    if (posChanged) this.ply = util.lastPly(d);
    util.upgradeServerData(d);
    this.data = d;
    this.omokAnalysisEnabled ||= !!d.omok?.ai?.analysisEnabled;
    this.clearJust();
    this.shouldSendMoveTime = false;
    this.updateClockCtrl();
    if (this.clock)
      this.clock.setClock({
        white: d.clock!.white,
        black: d.clock!.black,
        ticking: this.tickingClockColor(),
      });
    if (this.corresClock) this.corresClock.update(d.correspondence!.white, d.correspondence!.black);
    if (!this.replaying()) groundReload(this);
    if (posChanged) this.chessground.cancelPremove();
    this.setTitle();
    this.moveOn.next();
    this.setQuietMode();
    this.redraw();
    this.autoScroll();
    this.onChange();
    this.setOmokPlacementPending(false);
    this.setLoading(false);
    this.pluginUpdate(util.lastStep(this.data).fen);
    this.refreshOmokRapfi();
  };

  endWithData = (o: ApiEnd): void => {
    const d = this.data;
    d.game.winner = o.winner;
    d.game.status = o.status;
    d.game.boosted = o.boosted;
    d.player.blindfold = false;
    this.userJump(this.lastPly());
    d.game.fen = util.lastStep(this.data).fen;
    // If losing/drawing on time but locally it is the opponent's turn, move did not reach server before the end
    if (
      o.status.name === 'outoftime' &&
      d.player.color !== o.winner &&
      this.chessground.state.turnColor === d.opponent.color
    ) {
      this.reload(d);
    }
    this.promotion.cancel();
    this.chessground.stop();
    if (o.ratingDiff) {
      d.player.ratingDiff = o.ratingDiff[d.player.color];
      d.opponent.ratingDiff = o.ratingDiff[d.opponent.color];
    }
    if (!d.player.spectator && d.game.turns > 1) {
      poolRangeStorage.shiftRangeAfter(d);
      const key = o.winner ? (d.player.color === o.winner ? 'victory' : 'defeat') : 'draw';
      // Delay 'victory' & 'defeat' sounds to avoid overlapping with 'checkmate' sound
      if (o.status.name === 'mate') site.sound.playAndDelayMateResultIfNecessary(key);
      else site.sound.play(key);
    }
    this.onTimeTrouble(false);
    endGameView();
    if (d.crazyhouse) crazyEndHook();
    this.clearJust();
    this.setTitle();
    this.moveOn.next();
    this.setQuietMode();
    this.setOmokPlacementPending(false);
    this.setLoading(false);
    if (this.clock && o.clock)
      this.clock.setClock({
        white: o.clock.wc * 0.01,
        black: o.clock.bc * 0.01,
        ticking: undefined,
      });
    this.redraw();
    this.autoScroll();
    this.onChange();
    if (d.tv) setTimeout(site.reload, 10000);
    wakeLock.release();
    if (this.data.game.status.name === 'started') site.sound.saySan(this.stepAt(this.ply).san, false);
    else site.sound.say(viewStatus(this.data), false, false, true);
    this.server.alive();
    if (
      !d.player.spectator &&
      o.status.name === 'outoftime' &&
      this.chessground.state.turnColor === d.opponent.color
    ) {
      notify(viewStatus(this.data));
    }
    this.refreshOmokRapfi();
  };

  challengeRematch = async (): Promise<void> => {
    if (this.data.game.id !== 'synthetic') await xhr.challengeRematch(this.data.game.id);
    pubsub.emit('challenge-app.open');
    if (once('rematch-challenge')) {
      setTimeout(async () => {
        const [tour] = await Promise.all([
          site.asset.loadEsm<RoundTour>('round.tour'),
          site.asset.loadCssPath('bits.shepherd'),
        ]);
        tour.corresRematchOffline();
      }, 1000);
    }
  };

  private updateClockCtrl() {
    const d = this.data;
    if (d.clock) {
      this.corresClock = undefined;
      this.clock ??= new ClockCtrl(d.clock, d.pref, this.tickingClockColor(), this.makeClockOpts());
      this.clock.alarmAction = {
        seconds: 60,
        fire: () => this.onTimeTrouble(true),
      };
    } else {
      this.clock = undefined;
      if (d.correspondence)
        this.corresClock ??= new CorresClockController(this, d.correspondence, this.socket.outoftime);
    }
  }

  private readonly makeClockOpts: () => ClockOpts = () => ({
    onFlag: this.socket.outoftime,
    bothPlayersHavePlayed: () => game.bothPlayersHavePlayed(this.data),
    hasGoneBerserk: this.hasGoneBerserk,
    alarmColor:
      this.data.simul || this.data.player.spectator || !this.data.pref.clockSound
        ? undefined
        : this.data.player.color,
  });

  private readonly tickingClockColor = (): Color | undefined =>
    game.playable(this.data) && (game.playedTurns(this.data) > 1 || this.data.clock?.running)
      ? this.data.game.player
      : undefined;

  private readonly setQuietMode = () => {
    const was = site.quietMode;
    const is = this.isPlaying();
    if (was !== is) {
      site.quietMode = is;
      $('body').toggleClass(
        'no-select',
        is && this.clock && this.clock.millisOf(this.data.player.color) <= 3e5,
      );
    }
  };

  question = (): QuestionOpts | false => {
    if (this.toSubmit) {
      setTimeout(() => this.voiceMove?.listenForResponse('submitMove', this.submitMove));
      return {
        prompt: i18n.site.confirmMove,
        yes: { action: () => this.submitMove(true) },
        no: { action: () => this.submitMove(false), text: i18n.site.cancel },
      };
    } else if (this.data.player.proposingTakeback) {
      this.voiceMove?.listenForResponse('cancelTakeback', this.cancelTakebackPreventDraws);
      return {
        prompt: i18n.site.takebackPropositionSent,
        no: { action: this.cancelTakebackPreventDraws, text: i18n.site.cancel },
      };
    } else if (this.data.player.offeringDraw) return { prompt: i18n.site.drawOfferSent };
    else if (this.data.opponent.offeringDraw)
      return {
        prompt: i18n.site.yourOpponentOffersADraw,
        yes: { action: () => this.socket.send('draw-yes'), icon: licon.OneHalf },
        no: { action: () => this.socket.send('draw-no') },
      };
    else if (this.data.opponent.proposingTakeback)
      return {
        prompt: i18n.site.yourOpponentProposesATakeback,
        yes: { action: this.takebackYes, icon: licon.Back },
        no: { action: () => this.socket.send('takeback-no') },
      };
    else if (this.voiceMove) return this.voiceMove.question();
    else return false;
  };

  opponentRequest(req: 'takeback' | 'rematch' | 'draw', text: string): void {
    this.voiceMove?.listenForResponse(req, (v: boolean) =>
      this.socket.sendLoading(`${req}-${v ? 'yes' : 'no'}`),
    );
    notify(text);
  }

  takebackYes = (): void => {
    this.socket.sendLoading('takeback-yes');
    this.chessground.cancelPremove();
    this.promotion.cancel();
  };

  resign = (v: boolean, immediately?: boolean): void => {
    if (v) {
      if (this.resignConfirm || !this.data.pref.confirmResign || immediately) {
        this.socket.sendLoading('resign');
        clearTimeout(this.resignConfirm);
      } else {
        this.resignConfirm = setTimeout(() => this.resign(false), 3000);
      }
      this.redraw();
    } else if (this.resignConfirm) {
      clearTimeout(this.resignConfirm);
      this.resignConfirm = undefined;
      this.redraw();
    }
  };

  hasGoneBerserk = (color: Color): boolean => !!this.goneBerserk[color];

  goBerserk = (): void => {
    if (game.berserkableBy(this.data) && !this.hasGoneBerserk(this.data.player.color)) {
      this.socket.berserk();
      site.sound.play('berserk');
    }
  };

  setBerserk = (color: Color): void => {
    if (this.goneBerserk[color]) return;
    this.goneBerserk[color] = true;
    if (color !== this.data.player.color) site.sound.play('berserk');
    this.redraw();
    $(`<i data-icon="${licon.Berserk}">`).appendTo($(`.game__meta .player.${color} .user-link`));
  };

  setLoading = (v: boolean, duration = 1500): void => {
    clearTimeout(this.loadingTimeout);
    if (v) {
      this.loading = true;
      this.loadingTimeout = setTimeout(() => {
        this.loading = false;
        this.redraw();
      }, duration);
      this.redraw();
    } else if (this.loading) {
      this.loading = false;
      this.redraw();
    }
  };

  setOmokPlacementPending = (v: boolean, duration = 1500): void => {
    clearTimeout(this.omokPlacementPendingTimeout);
    if (v) {
      this.omokPlacementPending = true;
      this.omokPlacementPendingTimeout = setTimeout(() => {
        this.omokPlacementPending = false;
        this.redraw();
      }, duration);
      this.redraw();
    } else if (this.omokPlacementPending) {
      this.omokPlacementPending = false;
      this.redraw();
    }
  };

  setRedirecting = (): void => {
    this.redirecting = true;
    site.unload.expected = true;
    setTimeout(() => {
      this.redirecting = false;
      this.redraw();
    }, 2500);
    this.redraw();
  };

  submitMove = (v: boolean): void => {
    if (!this.toSubmit) return;

    const submit = this.toSubmit;
    this.toSubmit = undefined;
    this.setLoading(true, 300);

    if (v) {
      this.actualSendMove('u' in submit ? 'move' : 'drop', submit);
      site.sound.play('confirmation');
    } else this.jump(this.ply);
  };

  private readonly onChange = () => {
    if (this.opts.onChange) setTimeout(() => this.opts.onChange(this.data), 150);
  };

  toggleOmokAnalysis = (value?: boolean): void => {
    this.omokAnalysisEnabled = value ?? !this.omokAnalysisEnabled;
    if (!this.omokAnalysisEnabled) {
      this.omokAnalysisLoading = false;
      this.omokAnalysisKey = undefined;
      this.omokAnalysis = undefined;
      this.omokRapfi?.stop();
    } else this.refreshOmokRapfi();
    this.redraw();
  };

  private readonly ensureOmokRapfi = async (): Promise<OmokRapfiEngine> => {
    this.omokRapfi ??= new OmokRapfiEngine();
    await this.omokRapfi.init(this.data.omok?.ai?.threads);
    return this.omokRapfi;
  };

  private readonly recordOmokRapfiDebug = (
    stage: string,
    extra: Record<string, unknown> = {},
  ): void => {
    const root = window as typeof window & {
      __omokRapfiDebug?: unknown[];
      __omokRapfiDebugState?: Record<string, unknown>;
    };
    const state = {
      stage,
      at: Date.now(),
      playerColor: this.data.player.color,
      opponentColor: this.data.opponent.color,
      spectator: this.data.player.spectator,
      gamePlayer: this.data.game.player,
      isPlaying: this.isPlaying(),
      isOmokActive: this.isOmokActive(),
      isOmokAiTurn: this.isOmokAiTurn(),
      omokTurnColor: this.omokTurnColor(),
      omokAiColor: this.omokAiColor(),
      omokRuleSet: this.data.omok?.position.ruleSet,
      omokPly: this.data.omok?.position.ply,
      omokStatus: this.data.omok?.status,
      aiMode: this.data.omok?.ai?.mode,
      pendingKey: this.omokAiPendingKey,
      analysisEnabled: this.omokAnalysisEnabled,
      ...extra,
    };
    root.__omokRapfiDebugState = state;
    root.__omokRapfiDebug = [...(root.__omokRapfiDebug || []).slice(-39), state];
  };

  private readonly onSocketOpen = (): void => {
    if (!this.data.omok) return;
    this.recordOmokRapfiDebug('socket-open');
    this.refreshOmokRapfi();
  };

  private readonly refreshOmokRapfi = (): void => {
    if (!this.data.omok) return;
    this.recordOmokRapfiDebug('refresh');
    if (this.isOmokAiTurn()) void this.runOmokAiMove();
    else if (this.omokAnalysisEnabled) void this.runOmokAnalysis();
  };

  private readonly runOmokAiMove = async (): Promise<void> => {
    const position = this.data.omok?.position;
    if (!position || !this.isOmokAiTurn()) {
      this.recordOmokRapfiDebug('run-ai-skip', { hasPosition: !!position });
      return;
    }
    const key = `ai:${this.data.game.id}:${omokPositionKey(position)}`;
    if (this.omokAiPendingKey === key) {
      this.recordOmokRapfiDebug('run-ai-dup', { key });
      return;
    }
    this.omokAiPendingKey = key;
    this.omokAnalysisError = undefined;
    this.recordOmokRapfiDebug('run-ai-start', { key });
    this.redraw();
    try {
      const openingAction = await pickTaraguchiAiAction(position);
      if (this.omokAiPendingKey !== key) {
        this.recordOmokRapfiDebug('run-ai-stale-opening', { key, openingAction });
        return;
      }
      if (openingAction) {
        if (!wsIsOpen()) {
          this.recordOmokRapfiDebug('run-ai-wait-socket', { key, source: 'taraguchi', action: openingAction.type });
          return;
        }
        if (openingAction.type === 'swap') {
          this.recordOmokRapfiDebug('run-ai-send-swap', { key, source: 'taraguchi' });
          this.socket.send('omok-swap');
        } else if (openingAction.type === 'candidates') {
          this.recordOmokRapfiDebug('run-ai-send-candidates', { key });
          this.socket.send('omok-candidates');
        } else {
          this.setOmokPlacementPending(true, 3000);
          this.recordOmokRapfiDebug('run-ai-send-place', { key, bestMove: openingAction.move.key, source: 'taraguchi' });
          this.socket.send('place', { pos: openingAction.move.key as Key });
        }
        return;
      }

      const engine = await this.ensureOmokRapfi();
      const enginePosition =
        position.ruleSet === 'taraguchi10' && position.opening?.canSwap
          ? {
              ...position,
              opening: {
                ...position.opening,
                canSwap: false,
              },
            }
          : position;
      this.recordOmokRapfiDebug('run-ai-engine-ready', { key, status: engine.status() });
      const bestMove = await engine.bestMove(enginePosition, omokRapfiDefaults(this.data.omok?.ai));
      if (this.omokAiPendingKey !== key) {
        this.recordOmokRapfiDebug('run-ai-stale', { key, bestMove });
        return;
      }
      if (!wsIsOpen()) {
        this.recordOmokRapfiDebug('run-ai-wait-socket', {
          key,
          source: 'rapfi',
          action: bestMove === 'swap' ? 'swap' : 'place',
          bestMove: bestMove === 'swap' ? 'swap' : bestMove.key,
        });
        return;
      }
      if (bestMove === 'swap') {
        this.recordOmokRapfiDebug('run-ai-send-swap', { key, source: 'rapfi' });
        this.socket.send('omok-swap');
      } else {
        this.setOmokPlacementPending(true, 3000);
        this.recordOmokRapfiDebug('run-ai-send-place', { key, bestMove: bestMove.key, source: 'rapfi' });
        this.socket.send('place', { pos: bestMove.key as Key });
      }
    } catch (e) {
      this.omokAnalysisError = e instanceof Error ? e.message : 'Rapfi move generation failed.';
      this.recordOmokRapfiDebug('run-ai-error', {
        key,
        error: this.omokAnalysisError,
      });
    } finally {
      if (this.omokAiPendingKey === key) this.omokAiPendingKey = undefined;
      this.recordOmokRapfiDebug('run-ai-finally', { key });
      this.redraw();
    }
  };

  private readonly runOmokAnalysis = async (): Promise<void> => {
    const position = this.data.omok?.position;
    if (!position || !this.omokAnalysisEnabled || this.isOmokAiTurn()) return;
    const key = `analysis:${this.data.game.id}:${omokPositionKey(position)}`;
    if (this.omokAnalysisLoading || this.omokAnalysisKey === key) return;
    this.omokAnalysisLoading = true;
    this.omokAnalysisError = undefined;
    this.redraw();
    try {
      const engine = await this.ensureOmokRapfi();
      const analysis = await engine.analyse(position, omokRapfiDefaults(this.data.omok?.ai), 2);
      if (!this.omokAnalysisEnabled) return;
      this.omokAnalysis = analysis;
      this.omokAnalysisKey = key;
    } catch (e) {
      this.omokAnalysisError = e instanceof Error ? e.message : 'Rapfi analysis failed.';
    } finally {
      this.omokAnalysisLoading = false;
      this.redraw();
    }
  };

  private goneTick?: number;
  setGone = (gone: number | boolean): void => {
    game.setGone(this.data, this.data.opponent.color, gone);
    clearTimeout(this.goneTick);
    if (Number(gone) > 1)
      this.goneTick = setTimeout(() => {
        const g = Number(this.opponentGone());
        if (g > 1) this.setGone(g - 1);
      }, 1000);
    this.redraw();
  };

  opponentGone = (): number | boolean => {
    const d = this.data;
    return (
      defined(d.opponent.isGone) &&
      d.opponent.isGone !== false &&
      !game.isPlayerTurn(d) &&
      game.resignable(d) &&
      d.opponent.isGone
    );
  };

  rematch(accept?: boolean): boolean {
    if (accept === undefined)
      return !!this.data.opponent.offeringRematch || !!this.data.player.offeringRematch;
    else if (accept) {
      if (this.data.game.rematch) {
        this.setRedirecting();
        this.socket.send('rematch-yes');
      } else {
        if (!game.rematchable(this.data)) return false;
        if (!this.data.opponent.offeringRematch) this.data.player.offeringRematch = true;
        this.socket.send('rematch-yes');
      }
    } else {
      if (!this.data.opponent.offeringRematch) return false;
      this.socket.send('rematch-no');
    }
    this.redraw();
    return true;
  }

  canOfferDraw = (): boolean =>
    !this.preventDrawOffer &&
    game.drawable(this.data) &&
    (this.data.player.lastDrawOfferAtPly || -99) < this.lastPly() - 20;

  cancelTakebackPreventDraws = (): void => {
    this.socket.sendLoading('takeback-no');
    clearTimeout(this.preventDrawOffer);
    this.preventDrawOffer = setTimeout(() => {
      this.preventDrawOffer = undefined;
      this.redraw();
    }, 4000);
  };

  offerDraw = (v: boolean, immediately?: boolean): void => {
    if (this.canOfferDraw()) {
      if (this.drawConfirm) {
        if (v) this.doOfferDraw();
        clearTimeout(this.drawConfirm);
        this.drawConfirm = undefined;
      } else if (v) {
        if (this.data.pref.confirmResign && !immediately)
          this.drawConfirm = setTimeout(() => {
            this.offerDraw(false);
          }, 3000);
        else this.doOfferDraw();
      }
    }
    this.redraw();
  };

  private readonly doOfferDraw = () => {
    this.data.player.lastDrawOfferAtPly = this.lastPly();
    this.socket.sendLoading('draw-yes');
  };

  setChessground = (cg: CgApi): void => {
    this.chessground = cg;
    const up = { fen: this.stepAt(this.ply).fen, canMove: this.canMove(), cg };
    pubsub.on('board.change', (is3d: boolean) => {
      this.chessground.state.addPieceZIndex = is3d;
      this.chessground.redrawAll();
    });
    if (!this.isPlaying()) return;
    if (this.data.pref.keyboardMove) {
      if (!this.keyboardMove) this.keyboardMove = makeKeyboardMove(this);
      this.keyboardMove.update(up);
    }
    if (this.data.pref.voiceMove) {
      if (this.voiceMove) this.voiceMove.update(up);
      else this.voiceMove = makeVoiceMove(this, up);
    }
    if (this.keyboardMove || this.voiceMove) requestAnimationFrame(() => this.redraw());
  };

  stepAt = (ply: Ply): Step => util.plyStep(this.data, ply);

  speakClock = (): void => {
    this.clock?.speak();
  };

  blindfold = (v?: boolean): boolean => {
    this.data.player.blindfold ??= false;
    if (v === undefined || v === this.data.player.blindfold) return this.data.player.blindfold ?? false;
    this.blindfoldStorage.set(v);
    this.data.player.blindfold = v;
    this.socket.send(`blindfold-${v ? 'yes' : 'no'}`);
    this.redraw();
    return v;
  };

  onTimeTrouble = (t: boolean): void => {
    if (this.data.player.spectator) return;
    site.powertip.forcePlacementHook = t ? (el: HTMLElement) => el.closest('.crosstable') && 's' : undefined;
    this.chessground.state.touchIgnoreRadius = t ? Math.SQRT2 : 1;
  };

  yeet = (): void => {
    if (!this.data.player.spectator) this.doYeet();
  };

  private readonly doYeet = memoize(() => {
    this.chessground.stop();
    site.asset.loadEsm('round.yeet');
  });

  private googlyEyes?: () => DrawShape[];

  googlyEyesStart: () => void = memoize(async () => {
    const redraw = () => this.googlyEyes && this.chessground.setAutoShapes(this.googlyEyes());
    const { makeGooglyShapes }: any = await site.asset.loadEsm('bits.googlyHorsey', {
      init: { cg: this.chessground, redraw },
    });
    this.googlyEyes = makeGooglyShapes;
    redraw();
  });

  private readonly delayedInit = () =>
    requestIdleCallback(
      () => {
        const d = this.data;
        if (this.isPlaying()) {
          if (!d.simul) blur.init(d.steps.length > 2);

          title.init();
          this.setTitle();

          if (d.crazyhouse) crazyInit(this);

          if (!this.nvui && d.clock && !d.opponent.ai && !this.isSimulHost() && !d.local)
            window.addEventListener('beforeunload', e => {
              if (site.unload.expected || !this.isPlaying()) return;
              this.socket.send('bye2');
              e.preventDefault();
            });

          if (!this.nvui && d.pref.submitMove) {
            site.mousetrap
              .bind('esc', () => {
                this.submitMove(false);
                this.chessground.cancelMove();
              })
              .bind('return', () => this.submitMove(true));
          }
          cevalSub.subscribe(this);
        }

        if (!this.nvui) keyboardInit(this);
        if (this.isPlaying() && d.steps.length === 1) {
          this.blindfold(this.blindfoldStorage.get());
        }
        if (!d.local && d.game.speed !== 'correspondence') wakeLock.request();
        this.refreshOmokRapfi();
      },

      800,
    );
}
