import assert from 'node:assert/strict';
import { describe, test } from 'node:test';

import { isOmokTerminal } from '../src/omok';

describe('omok helpers', () => {
  test('terminal detection stays false for missing or ongoing omok state', () => {
    assert.equal(isOmokTerminal({}), false);
    assert.equal(
      isOmokTerminal({
        omok: {
          position: {
            boardSize: 15,
            boardRows: [],
            turn: 'black',
            ruleSet: 'renju',
            ply: 12,
          },
          status: 'ongoing',
        },
      }),
      false,
    );
  });

  test('terminal detection treats winner or terminal status as finished', () => {
    assert.equal(
      isOmokTerminal({
        omok: {
          position: {
            boardSize: 15,
            boardRows: [],
            turn: 'white',
            ruleSet: 'renju',
            ply: 13,
          },
          status: 'win',
          winner: 'black',
        },
      }),
      true,
    );
    assert.equal(
      isOmokTerminal({
        omok: {
          position: {
            boardSize: 15,
            boardRows: [],
            turn: 'black',
            ruleSet: 'renju',
            ply: 13,
          },
          winner: 'white',
        },
      }),
      true,
    );
  });

  test('title falls back to game over when omok is terminal before endData', async () => {
    document.title = 'Round';
    const favicon = document.createElement('link');
    favicon.id = 'favicon';
    document.head.appendChild(favicon);

    const { set } = await import('../src/title');
    set({
      data: {
        player: { spectator: false, color: 'white' },
        game: {
          status: { id: 20, name: 'started' },
          player: 'white',
        },
        omok: {
          position: {
            boardSize: 15,
            boardRows: [],
            turn: 'black',
            ruleSet: 'renju',
            ply: 13,
          },
          status: 'win',
          winner: 'white',
        },
      },
      isPlayerTurn: () => true,
    } as any);

    assert.equal(document.title, 'site.gameOver - Round');
  });
});
