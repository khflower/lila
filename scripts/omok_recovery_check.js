#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');

const PLAYWRIGHT_PATH =
  process.env.OMOK_E2E_PLAYWRIGHT ||
  'C:/Users/hyeon/.openclaw/workspace/tmp-playwright/node_modules/playwright';

function loadPlaywright() {
  try {
    return require(PLAYWRIGHT_PATH);
  } catch (_) {
    return require('playwright');
  }
}

function stamp(date = new Date()) {
  const pad = v => String(v).padStart(2, '0');
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`;
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

async function screenshot(page, outDir, name) {
  const target = path.join(outDir, `${name}.png`);
  await page.screenshot({ path: target, fullPage: true });
  return target;
}

async function textOrNull(locator) {
  if ((await locator.count()) < 1) return null;
  return (await locator.first().innerText()).trim();
}

async function collectButtons(page) {
  return page.locator('button,[role="button"]').evaluateAll(nodes =>
    nodes
      .map(node => ({
        text: (node.textContent || '').trim(),
        className: node.className || '',
        disabled: !!node.disabled,
      }))
      .filter(entry => entry.text),
  );
}

async function openHome(page, baseUrl, outDir, locale = '') {
  const url = `${baseUrl}${locale}`;
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(1500);
  return {
    url: page.url(),
    title: await page.title(),
    buttons: await collectButtons(page),
    screenshot: await screenshot(page, outDir, locale ? `home_${locale.slice(1)}` : 'home'),
  };
}

async function createLobbyRoom(page, outDir) {
  await page.goto('http://localhost:9663', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(1200);
  await page.locator('button.lobby__start__button--hook').click();
  await page.waitForSelector('.game-setup', { timeout: 10000 });
  await page.getByRole('button', { name: 'Create omok room' }).last().click();
  await page.waitForTimeout(1800);
  const lobbyTab = page.getByRole('button', { name: /^Lobby$/ });
  if (await lobbyTab.count()) {
    await lobbyTab.click();
    await page.waitForTimeout(1200);
  }
  const bodyText = await page.locator('body').innerText();
  const hooksText = await page.locator('.lobby__app').innerText().catch(() => '');
  return {
    title: await page.title(),
    url: page.url(),
    bodyText: bodyText.slice(0, 4000),
    hooksText: hooksText.slice(0, 4000),
    screenshot: await screenshot(page, outDir, 'lobby_room_created'),
  };
}

async function friendInviteFlow(browser, outDir) {
  const host = await browser.newPage();
  const guest = await browser.newPage();
  const result = {};

  await host.goto('http://localhost:9663', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await host.waitForTimeout(1200);
  await host.locator('button.lobby__start__button--friend').click();
  await host.waitForSelector('.game-setup', { timeout: 10000 });
  await host.getByRole('button', { name: 'Invite a player' }).last().click();
  await host.waitForURL(url => String(url).includes('/omok/friend/'), { timeout: 20000 });
  await host.waitForTimeout(1200);

  const hostUrl = host.url();
  const shareValue = await host.locator('#omok-friend-share-url').inputValue();
  result.hostWaitUrl = hostUrl;
  result.hostWaitTitle = await host.title();
  result.hostWaitShot = await screenshot(host, outDir, 'friend_host_wait');

  await guest.goto(shareValue, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await guest.waitForTimeout(1800);
  result.guestWaitUrl = guest.url();
  result.guestWaitTitle = await guest.title();
  result.guestWaitShot = await screenshot(guest, outDir, 'friend_guest_wait');

  const confirmButton = host.locator('#omok-friend-confirm');
  await confirmButton.waitFor({ state: 'visible', timeout: 10000 });
  await host.waitForFunction(
    () => {
      const node = document.querySelector('#omok-friend-confirm');
      return !!node && !node.disabled;
    },
    { timeout: 15000 },
  );
  await confirmButton.click();

  await host.waitForURL(url => !String(url).includes('/omok/friend/'), { timeout: 30000 });
  await guest.waitForURL(url => !String(url).includes('/omok/friend/'), { timeout: 30000 });
  await host.waitForTimeout(2200);
  await guest.waitForTimeout(2200);

  const hostPath = new URL(host.url()).pathname;
  const guestPath = new URL(guest.url()).pathname;
  result.hostGameUrl = host.url();
  result.guestGameUrl = guest.url();
  result.sameGameId = hostPath.slice(1, 9) === guestPath.slice(1, 9);
  result.hostGameShot = await screenshot(host, outDir, 'friend_host_game');
  result.guestGameShot = await screenshot(guest, outDir, 'friend_guest_game');
  result.hostHasOmok = await host.locator('.round__app[data-board-game="omok"]').count();
  result.guestHasOmok = await guest.locator('.round__app[data-board-game="omok"]').count();
  result.hostTitle = await host.title();
  result.guestTitle = await guest.title();

  await host.close();
  await guest.close();
  return result;
}

async function aiLayoutCheck(browser, outDir) {
  const page = await browser.newPage();
  await page.goto('http://localhost:9663', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(1200);
  await page.locator('button.lobby__start__button--ai').click();
  await page.waitForSelector('.game-setup', { timeout: 10000 });
  await page.locator('#sf_omok_rule_set').selectOption('renju');
  await page.getByRole('button', { name: 'Play against Rapfi AI' }).last().click();
  await page.waitForURL(url => !String(url).includes('/?any#ai') && String(url) !== 'http://localhost:9663/', {
    timeout: 20000,
  });
  await page.waitForTimeout(2200);

  const layout = await page.evaluate(() => {
    const board = document.querySelector('.round__app__board__omok-placeholder__board');
    const state = document.querySelector('.round__app__board__omok-state');
    const sideCard = document.querySelector('.ruser-top, .ruser-bottom, .rclock-top, .rclock-bottom');
    const opponent = document.querySelector('.ruser-top, .ruser-bottom');
    const boardRect = board?.getBoundingClientRect();
    const stateRect = state?.getBoundingClientRect();
    return {
      boardRect,
      stateRect,
      overlap:
        !!boardRect &&
        !!stateRect &&
        !(stateRect.top >= boardRect.bottom || stateRect.bottom <= boardRect.top || stateRect.left >= boardRect.right || stateRect.right <= boardRect.left),
      rightPanelText: (sideCard?.textContent || '').trim(),
      opponentText: (opponent?.textContent || '').trim(),
    };
  });

  const result = {
    url: page.url(),
    title: await page.title(),
    layout,
    screenshot: await screenshot(page, outDir, 'ai_round_layout'),
  };
  await page.close();
  return result;
}

async function soloBoardCheck(browser, outDir) {
  const page = await browser.newPage();
  await page.goto('http://localhost:9663/omok/solo', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(1200);

  async function clickKey(key) {
    const box = await page.locator('.omok-solo-board-svg').boundingBox();
    if (!box) throw new Error('solo board svg missing');
    const files = 'ABCDEFGHIJKLMNO';
    const col = files.indexOf(key[0].toUpperCase());
    const row = Number(key.slice(1)) - 1;
    const inset = 7;
    const step = (100 - inset * 2) / 14;
    const x = inset + col * step;
    const y = inset + (14 - row) * step;
    await page.mouse.move(box.x + (x / 100) * box.width, box.y + (y / 100) * box.height);
    await page.mouse.down();
    await page.mouse.up();
    await page.waitForTimeout(250);
  }

  await clickKey('H8');
  await clickKey('H9');
  await clickKey('I9');
  const afterMoves = await page.locator('.omok-solo-board-sequence').innerText();
  await page.locator('.js-solo-undo').click();
  await page.waitForTimeout(250);
  const afterUndo = await page.locator('.omok-solo-board-sequence').innerText();
  await page.locator('.js-solo-reset').click();
  await page.waitForTimeout(250);
  const afterReset = await page.locator('.omok-solo-board-sequence').innerText();

  const result = {
    title: await page.title(),
    url: page.url(),
    afterMoves,
    afterUndo,
    afterReset,
    screenshot: await screenshot(page, outDir, 'solo_board'),
  };
  await page.close();
  return result;
}

async function main() {
  const { chromium } = loadPlaywright();
  const outDir = path.join('C:/Users/hyeon/OneDrive/Documents/New project/tmp', `omok_recovery_check_${stamp()}`);
  ensureDir(outDir);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1600, height: 1300 } });

  try {
    const page = await context.newPage();
    const result = {
      home: await openHome(page, 'http://localhost:9663', outDir),
      homeKo: await openHome(page, 'http://localhost:9663', outDir, '/ko'),
      lobbyCreate: await createLobbyRoom(page, outDir),
      friendInvite: await friendInviteFlow(context, outDir),
      aiLayout: await aiLayoutCheck(context, outDir),
      soloBoard: await soloBoardCheck(context, outDir),
    };
    const target = path.join(outDir, 'summary.json');
    fs.writeFileSync(target, JSON.stringify(result, null, 2));
    console.log(target);
    console.log(JSON.stringify(result, null, 2));
  } finally {
    await context.close();
    await browser.close();
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
