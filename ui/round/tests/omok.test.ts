import assert from 'node:assert/strict';
import { describe, test } from 'node:test';

import { isOmokTerminal } from '../src/omok';
import { chooseSelectedCandidate } from '../src/omokRapfi';

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

  test('Taraguchi chooser prefers opening-book unknown candidates first', () => {
    const selected = chooseSelectedCandidate(
      [
        { key: 'J10', row: 9, col: 9 },
        { key: 'A1', row: 0, col: 0 },
        { key: 'K7', row: 6, col: 10 },
      ],
      new Map([
        ['J10', { move: 'J10', evalIndex: 7, rank: 8 }],
        ['K7', { move: 'K7', evalIndex: 7, rank: 9 }],
      ]),
    );

    assert.equal(selected?.key, 'A1');
  });

  test('Taraguchi chooser prefers the most white-favorable known candidate when all are in book', () => {
    const selected = chooseSelectedCandidate(
      [
        { key: 'G9', row: 8, col: 6 },
        { key: 'K8', row: 7, col: 10 },
        { key: 'J10', row: 9, col: 9 },
      ],
      new Map([
        ['G9', { move: 'G9', evalIndex: 0, rank: 1 }],
        ['K8', { move: 'K8', evalIndex: 5, rank: 7 }],
        ['J10', { move: 'J10', evalIndex: 7, rank: 8 }],
      ]),
    );

    assert.equal(selected?.key, 'J10');
  });
});
