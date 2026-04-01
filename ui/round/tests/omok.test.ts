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

});
