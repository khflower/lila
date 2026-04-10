#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');
const BASE_URL = process.env.OMOK_CLICK_AUDIT_BASE_URL || 'http://localhost:9663';
const PLAYWRIGHT_PATH =
  process.env.OMOK_E2E_PLAYWRIGHT ||
  'C:/Users/hyeon/.openclaw/workspace/tmp-playwright/node_modules/playwright';

function tryRequirePlaywright() {
  try {
    return require(PLAYWRIGHT_PATH);
  } catch (_) {
    return require('playwright');
  }
}

function timestampId(date = new Date()) {
  const pad = value => String(value).padStart(2, '0');
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
    '-',
    pad(date.getHours()),
    pad(date.getMinutes()),
    pad(date.getSeconds()),
  ].join('');
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

function sanitize(value) {
  return String(value || '')
    .replace(/[^a-z0-9._-]+/gi, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 80);
}

function sameOrigin(url) {
  try {
    return new URL(url).origin === new URL(BASE_URL).origin;
  } catch (_) {
    return false;
  }
}

function normalizeUrl(url) {
  try {
    const next = new URL(url, BASE_URL);
    next.hash = '';
    next.searchParams.delete('referrer');
    next.searchParams.delete('any');
    return next.toString();
  } catch (_) {
    return null;
  }
}

function shouldCrawlUrl(url) {
  const normalized = normalizeUrl(url);
  if (!normalized) return false;
  if (!sameOrigin(normalized)) return false;
  try {
    const next = new URL(normalized);
    if (/\.(png|jpg|jpeg|gif|svg|webp|ico|css|js|map)$/i.test(next.pathname)) return false;
    if (/^\/(api|socket|assets|export)\b/i.test(next.pathname)) return false;
    if (/^\/auth\/logout\b/i.test(next.pathname)) return false;
    return true;
  } catch (_) {
    return false;
  }
}

function leakFlags(title, sampleText) {
  const corpus = `${title}\n${sampleText}`;
  const flags = [];
  if (/\blichess\b/i.test(corpus)) flags.push('lichess');
  if (/\bstockfish\b/i.test(corpus)) flags.push('stockfish');
  if (/\bchess\b/i.test(corpus)) flags.push('chess');
  if (/page not found/i.test(corpus) || /\b404\b/.test(corpus)) flags.push('404');
  return [...new Set(flags)];
}

async function pause(page, ms = 900) {
  await page.waitForTimeout(ms);
}

function scenarioLabel(name) {
  return `scenario_${sanitize(name)}`;
}

async function collectPageInfo(page) {
  return page.evaluate(() => {
    const textTokens = String(document.body?.innerText || '')
      .split(/\s+/)
      .map(v => v.trim())
      .filter(Boolean)
      .slice(0, 120);

    const links = Array.from(document.querySelectorAll('a'))
      .map(node => ({
        text: (node.textContent || '').trim(),
        href: node.href || '',
        cls: node.className || '',
      }))
      .filter(entry => entry.href);

    const buttons = Array.from(document.querySelectorAll('button,[role="button"]'))
      .map(node => ({
        text: (node.textContent || '').trim(),
        cls: node.className || '',
        tag: node.tagName,
      }))
      .filter(entry => entry.text);

    return {
      title: document.title,
      bodySample: textTokens,
      bodyText: String(document.body?.innerText || '').slice(0, 4000),
      links,
      buttons,
    };
  });
}

async function auditPage(page, url, label, outDir) {
  const normalized = normalizeUrl(url);
  if (!normalized) {
    return { url, label, error: 'invalid-url', status: 'fail' };
  }

  let response = null;
  try {
    response = await page.goto(normalized, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await page.waitForTimeout(1200);
  } catch (error) {
    const shotPath = path.join(outDir, `${sanitize(label)}.png`);
    await page.screenshot({ path: shotPath, fullPage: true }).catch(() => {});
    return {
      url: normalized,
      label,
      status: 'fail',
      failureReason: String(error.message || error),
      screenshot: shotPath,
    };
  }

  const info = await collectPageInfo(page);
  const bodySample = info.bodySample.join(' ');
  const flags = leakFlags(info.title, bodySample);
  const shotPath = path.join(outDir, `${sanitize(label)}.png`);
  await page.screenshot({ path: shotPath, fullPage: true });

  return {
    url: normalized,
    finalUrl: page.url(),
    label,
    status: flags.length ? 'fail' : 'pass',
    httpStatus: response?.status() || 0,
    title: info.title,
    bodySample: info.bodySample,
    bodyText: info.bodyText,
    leakFlags: flags,
    links: info.links.filter(entry => sameOrigin(entry.href)),
    buttons: info.buttons,
    screenshot: shotPath,
  };
}

async function openAiRound(browser, outDir) {
  const page = await browser.newPage();
  const actionTrace = [];
  const startTime = new Date().toISOString();
  try {
    await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await page.waitForTimeout(1200);
    await page.locator('button.lobby__start__button--ai').click({ timeout: 10000 });
    actionTrace.push('open ai modal');
    const modal = page.locator('.game-setup');
    await modal.locator('#sf_omok_rule_set').selectOption('freestyle');
    actionTrace.push('set rule freestyle');
    await modal.locator('#sf_omok_threads').fill('2');
    await modal.locator('#sf_omok_move_time').fill('700');
    await modal.locator('#sf_omok_depth').fill('6');
    await modal.locator('#sf_omok_nodes').fill('0');
    actionTrace.push('set engine inputs threads=2 moveTime=700 depth=6 nodes=0');
    const timeToggle = modal.getByText('Real time', { exact: true }).first();
    if (await timeToggle.isVisible().catch(() => false)) {
      await timeToggle.click();
      actionTrace.push('toggle time mode Real time');
      await pause(page, 300);
    } else {
      actionTrace.push('time mode control not shown for ai; kept default');
    }
    const sideToggle = modal.getByText('Black', { exact: true }).first();
    if (await sideToggle.isVisible().catch(() => false)) {
      await sideToggle.click();
      actionTrace.push('toggle side Black');
      await pause(page, 500);
    } else {
      actionTrace.push('side control not shown for ai; kept default');
    }
    await page.getByRole('button', { name: 'Play against Rapfi AI' }).last().click({ timeout: 10000 });
    actionTrace.push('start AI game');
    await page.waitForURL(url => !String(url).includes('/?any#ai') && String(url) !== `${BASE_URL}/`, {
      timeout: 20000,
    });
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1800);

    const info = await collectPageInfo(page);
    const shotPath = path.join(outDir, 'round_live.png');
    await page.screenshot({ path: shotPath, fullPage: true });
    const scenario = await buildScenarioResult(page, 'ai-setup-flow', outDir, actionTrace, { startTime });

    return {
      url: page.url(),
      title: info.title,
      bodySample: info.bodySample,
      links: info.links.filter(entry => sameOrigin(entry.href)),
      buttons: info.buttons,
      screenshot: shotPath,
      scenario,
    };
  } catch (error) {
    const shotPath = path.join(outDir, 'round_live_failed.png');
    await page.screenshot({ path: shotPath, fullPage: true }).catch(() => {});
    const scenario = {
      scenario: 'ai-setup-flow',
      status: 'fail',
      startTime,
      endTime: new Date().toISOString(),
      finalUrl: page.url(),
      failureReason: String(error.message || error),
      actionTrace,
      screenshot: shotPath,
    };
    fs.writeFileSync(path.join(outDir, `${scenarioLabel('ai-setup-flow')}.json`), JSON.stringify(scenario, null, 2));
    return {
      error: String(error.message || error),
      screenshot: shotPath,
      scenario,
    };
  } finally {
    await page.close().catch(() => {});
  }
}

async function collectModalSnapshot(browser, buttonSelector, outputName) {
  const page = await browser.newPage();
  try {
    await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await page.waitForTimeout(1200);
    await page.locator(buttonSelector).click({ timeout: 10000 });
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(800);
    const shotPath = path.join(outputName.dir, outputName.file);
    await page.screenshot({ path: shotPath, fullPage: true });
    return {
      screenshot: shotPath,
      ...(await collectPageInfo(page)),
    };
  } finally {
    await page.close().catch(() => {});
  }
}

async function buildScenarioResult(page, name, outDir, actionTrace, extra = {}) {
  const info = await collectPageInfo(page);
  const bodySample = info.bodySample.join(' ');
  const flags = leakFlags(info.title, bodySample);
  const screenshot = path.join(outDir, `${scenarioLabel(name)}.png`);
  await page.screenshot({ path: screenshot, fullPage: true });
  const result = {
    scenario: name,
    status: flags.length ? 'fail' : 'pass',
    startTime: extra.startTime || new Date().toISOString(),
    endTime: new Date().toISOString(),
    finalUrl: page.url(),
    title: info.title,
    bodySample: info.bodySample,
    bodyText: info.bodyText,
    leakFlags: flags,
    actionTrace,
    screenshot,
    ...extra,
  };
  fs.writeFileSync(path.join(outDir, `${scenarioLabel(name)}.json`), JSON.stringify(result, null, 2));
  return result;
}

async function recordScenarioFailure(page, name, outDir, actionTrace, error, extra = {}) {
  const screenshot = path.join(outDir, `${scenarioLabel(name)}.png`);
  await page.screenshot({ path: screenshot, fullPage: true }).catch(() => {});
  const result = {
    scenario: name,
    status: 'fail',
    startTime: extra.startTime || new Date().toISOString(),
    endTime: new Date().toISOString(),
    finalUrl: page.url(),
    failureReason: String(error.message || error),
    actionTrace,
    screenshot,
    ...extra,
  };
  fs.writeFileSync(path.join(outDir, `${scenarioLabel(name)}.json`), JSON.stringify(result, null, 2));
  return result;
}

async function clickGuideKey(page, key, actionTrace) {
  const box = await page.locator('.js-guide-svg').boundingBox();
  if (!box) throw new Error(`guide board not found for ${key}`);
  const files = 'ABCDEFGHIJKLMNO';
  const col = files.indexOf(key[0].toUpperCase());
  const row = Number(key.slice(1)) - 1;
  if (col < 0 || row < 0) throw new Error(`invalid guide key ${key}`);
  const svgInset = 7;
  const svgSize = 100;
  const step = (svgSize - svgInset * 2) / 14;
  const svgX = svgInset + col * step;
  const svgY = svgInset + (14 - row) * step;
  const x = box.x + (svgX / svgSize) * box.width;
  const y = box.y + (svgY / svgSize) * box.height;
  await page.mouse.move(x, y);
  await page.mouse.down();
  await page.mouse.up();
  actionTrace.push(`click board ${key}`);
  await pause(page, 250);
}

async function runQuickPairingScenarios(browser, outDir) {
  const presets = ['1+0', '2+1', '3+0', '3+2', '5+0', '5+3', '10+0', '10+5', '15+10', '30+0', '30+20', 'Custom'];
  const results = [];
  for (const preset of presets) {
    const page = await browser.newPage();
    const actionTrace = [];
    const name = `quick-pairing-${preset.replace('+', 'p')}`;
    const startTime = new Date().toISOString();
    try {
      await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 20000 });
      await pause(page, 1000);
      await page.locator('.lpool').filter({ hasText: preset }).first().click({ timeout: 10000 });
      actionTrace.push(`open quick pairing preset ${preset}`);
      if (preset === 'Custom') await pause(page, 1200);
      else {
        await Promise.race([
          page.waitForURL(url => String(url) !== `${BASE_URL}/`, { timeout: 8000 }),
          pause(page, 1500),
        ]);
        await pause(page, 1000);
      }
      results.push(await buildScenarioResult(page, name, outDir, actionTrace, { startTime }));
    } catch (error) {
      results.push(await recordScenarioFailure(page, name, outDir, actionTrace, error, { startTime }));
    } finally {
      await page.close().catch(() => {});
    }
  }
  return results;
}

async function runSetupScenario(browser, type, outDir) {
  const page = await browser.newPage();
  const actionTrace = [];
  const startTime = new Date().toISOString();
  const name = `${type}-setup-flow`;
  const buttonSelector = {
    hook: 'button.lobby__start__button--hook',
    friend: 'button.lobby__start__button--friend',
    ai: 'button.lobby__start__button--ai',
  }[type];

  try {
    await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await pause(page, 1000);
    await page.locator(buttonSelector).click({ timeout: 10000 });
    actionTrace.push(`open ${type} modal`);
    await pause(page, 700);

    const modal = page.locator('.game-setup');
    if (type !== 'ai') {
      const rule = type === 'hook' ? 'renju' : 'freestyle';
      await modal.locator('#sf_omok_rule_set').selectOption(rule);
      actionTrace.push(`set rule ${rule}`);
    } else {
      await modal.locator('#sf_omok_rule_set').selectOption('freestyle');
      actionTrace.push('set rule freestyle');
      await modal.locator('#sf_omok_threads').fill('2');
      await modal.locator('#sf_omok_move_time').fill('700');
      await modal.locator('#sf_omok_depth').fill('6');
      await modal.locator('#sf_omok_nodes').fill('0');
      actionTrace.push('set engine inputs threads=2 moveTime=700 depth=6 nodes=0');
    }

    const timeTarget = type === 'friend' ? 'Correspondence' : 'Real time';
    const timeToggle = modal.getByText(timeTarget, { exact: true }).first();
    if (await timeToggle.isVisible().catch(() => false)) {
      await timeToggle.click();
      actionTrace.push(`toggle time mode ${timeTarget}`);
      await pause(page, 300);
    } else {
      actionTrace.push(`time mode control not shown for ${type}; kept default`);
    }

    const sideTarget = type === 'ai' ? 'Black' : type === 'hook' ? 'White' : 'Random side';
    const sideToggle = modal.getByText(sideTarget, { exact: true }).first();
    if (await sideToggle.isVisible().catch(() => false)) {
      await sideToggle.click();
      actionTrace.push(`toggle side ${sideTarget}`);
      await pause(page, 500);
    } else {
      actionTrace.push(`side control not shown for ${type}; kept default`);
    }

    if (type === 'ai') {
      await modal.getByRole('button', { name: 'Play against Rapfi AI' }).last().click({ timeout: 10000 });
      actionTrace.push('start AI game');
      await page.waitForURL(url => !String(url).includes('/?any#ai') && String(url) !== `${BASE_URL}/`, {
        timeout: 20000,
      });
      await pause(page, 1800);
    }

    return await buildScenarioResult(page, name, outDir, actionTrace, { startTime });
  } catch (error) {
    return await recordScenarioFailure(page, name, outDir, actionTrace, error, { startTime });
  } finally {
    await page.close().catch(() => {});
  }
}

async function runGuideScenario(browser, outDir) {
  const page = await browser.newPage();
  const actionTrace = [];
  const startTime = new Date().toISOString();
  const name = 'opening-guide-flow';
  try {
    await page.goto(`${BASE_URL}/dev/omok/opening-guide`, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await pause(page, 1200);
    await clickGuideKey(page, 'H9', actionTrace);
    await clickGuideKey(page, 'H10', actionTrace);
    const fourthText = (await page.locator('.omok-opening-guide-move-item strong').first().textContent()) || '';
    const fourthMove = (fourthText.match(/[A-O]\d+/) || [])[0];
    if (fourthMove) await clickGuideKey(page, fourthMove, actionTrace);
    const fifthText = (await page.locator('.omok-opening-guide-move-item strong').first().textContent()) || '';
    const fifthMove = (fifthText.match(/[A-O]\d+/) || [])[0];
    if (fifthMove) await clickGuideKey(page, fifthMove, actionTrace);
    await page.getByRole('button', { name: 'Undo' }).click({ timeout: 10000 });
    actionTrace.push('click Undo');
    await pause(page, 250);
    await page.getByRole('button', { name: 'Reset' }).click({ timeout: 10000 });
    actionTrace.push('click Reset');
    await pause(page, 250);
    return await buildScenarioResult(page, name, outDir, actionTrace, { startTime });
  } catch (error) {
    return await recordScenarioFailure(page, name, outDir, actionTrace, error, { startTime });
  } finally {
    await page.close().catch(() => {});
  }
}

async function runAuthScenarios(browser, outDir) {
  const scenarios = [];

  {
    const page = await browser.newPage();
    const actionTrace = [];
    const startTime = new Date().toISOString();
    try {
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 20000 });
      await pause(page, 1000);
      await page.getByRole('link', { name: /Password reset|Forgot password/i }).first().click({ timeout: 10000 });
      actionTrace.push('login -> forgot password');
      await pause(page, 800);
      scenarios.push(await buildScenarioResult(page, 'auth-forgot-password-flow', outDir, actionTrace, { startTime }));
    } catch (error) {
      scenarios.push(await recordScenarioFailure(page, 'auth-forgot-password-flow', outDir, actionTrace, error, { startTime }));
    } finally {
      await page.close().catch(() => {});
    }
  }

  {
    const page = await browser.newPage();
    const actionTrace = [];
    const startTime = new Date().toISOString();
    try {
      await page.goto(`${BASE_URL}/login`, { waitUntil: 'domcontentloaded', timeout: 20000 });
      await pause(page, 1000);
      await page.getByRole('link', { name: 'Log in by email' }).click({ timeout: 10000 });
      actionTrace.push('login -> log in by email');
      await pause(page, 800);
      scenarios.push(await buildScenarioResult(page, 'auth-magic-link-flow', outDir, actionTrace, { startTime }));
    } catch (error) {
      scenarios.push(await recordScenarioFailure(page, 'auth-magic-link-flow', outDir, actionTrace, error, { startTime }));
    } finally {
      await page.close().catch(() => {});
    }
  }

  {
    const page = await browser.newPage();
    const actionTrace = [];
    const startTime = new Date().toISOString();
    try {
      await page.goto(`${BASE_URL}/signup`, { waitUntil: 'domcontentloaded', timeout: 20000 });
      await pause(page, 1000);
      await page.locator('a[href="/terms-of-service"]').first().click({ timeout: 10000 });
      actionTrace.push('signup -> terms of service');
      await pause(page, 800);
      scenarios.push(await buildScenarioResult(page, 'auth-terms-flow', outDir, actionTrace, { startTime }));
    } catch (error) {
      scenarios.push(await recordScenarioFailure(page, 'auth-terms-flow', outDir, actionTrace, error, { startTime }));
    } finally {
      await page.close().catch(() => {});
    }
  }

  return scenarios;
}

async function runRoundInteractionScenario(browser, roundUrl, outDir) {
  const page = await browser.newPage();
  const actionTrace = [];
  const startTime = new Date().toISOString();
  const name = 'round-analysis-back-flow';
  try {
    await page.goto(roundUrl, { waitUntil: 'domcontentloaded', timeout: 20000 });
    await pause(page, 1200);
    const visibleReview = page.getByRole('link', { name: /Review|Analysis/i }).first();
    if (await visibleReview.isVisible().catch(() => false)) {
      await visibleReview.click({ timeout: 10000 });
      actionTrace.push('open analysis from round');
    } else {
      const analysisHref = await page.evaluate(() => {
        const el = document.querySelector('a.fbt.analysis, a.analysis, a[href*="/analysis"]');
        if (el instanceof HTMLAnchorElement) return el.href;
        const match = window.location.pathname.match(/^\/([A-Za-z0-9]{8})([A-Za-z0-9]{4})?$/);
        if (!match) return null;
        const gameId = match[1];
        const text = String(document.body?.innerText || '');
        const color = /\bYou play Black\b/i.test(text)
          ? 'black'
          : /\bYou play White\b/i.test(text)
            ? 'white'
            : null;
        return color
          ? `${window.location.origin}/${gameId}/${color}/analysis#0`
          : `${window.location.origin}/${gameId}/analysis#0`;
      });
      if (!analysisHref) throw new Error('analysis entrypoint not found on round page');
      await page.goto(analysisHref, { waitUntil: 'domcontentloaded', timeout: 20000 });
      actionTrace.push('open analysis route directly');
    }
    await page.waitForURL(url => String(url).includes('/analysis'), { timeout: 15000 });
    await pause(page, 800);
    await page.getByRole('link', { name: 'Back to game' }).click({ timeout: 10000 });
    actionTrace.push('back to game from analysis');
    await page.waitForURL(url => !String(url).includes('/analysis'), { timeout: 15000 });
    await pause(page, 1200);
    return await buildScenarioResult(page, name, outDir, actionTrace, { startTime });
  } catch (error) {
    return await recordScenarioFailure(page, name, outDir, actionTrace, error, { startTime });
  } finally {
    await page.close().catch(() => {});
  }
}

async function main() {
  const { chromium } = tryRequirePlaywright();
  const outDir = path.join(ROOT_DIR, 'tmp', `omok_click_audit_${timestampId()}`);
  ensureDir(outDir);

  const browser = await chromium.launch({ headless: true });
  const auditPageInstance = await browser.newPage();

  const summary = {
    startedAt: new Date().toISOString(),
    baseUrl: BASE_URL,
    pages: [],
    issues: [],
    crawledUrls: [],
    scenarios: [],
  };

  try {
    const home = await auditPage(auditPageInstance, BASE_URL, 'home', outDir);
    summary.pages.push(home);

    const modalShots = {};
    modalShots.friend = await collectModalSnapshot(browser, 'button.lobby__start__button--friend', {
      dir: outDir,
      file: 'friend_modal.png',
    });
    modalShots.ai = await collectModalSnapshot(browser, 'button.lobby__start__button--ai', {
      dir: outDir,
      file: 'ai_modal.png',
    });
    summary.modals = {
      friend: {
        title: modalShots.friend.title,
        bodySample: modalShots.friend.bodySample,
        screenshot: modalShots.friend.screenshot,
      },
      ai: {
        title: modalShots.ai.title,
        bodySample: modalShots.ai.bodySample,
        screenshot: modalShots.ai.screenshot,
      },
    };

    const seedUrls = new Map([
      ['players', `${BASE_URL}/player`],
      ['players-online', `${BASE_URL}/player/online`],
      ['players-top', `${BASE_URL}/player/top/blitz`],
      ['tv', `${BASE_URL}/tv`],
      ['games', `${BASE_URL}/games`],
      ['games-blitz', `${BASE_URL}/games/blitz`],
      ['opening', `${BASE_URL}/opening`],
      ['training', `${BASE_URL}/training`],
      ['study', `${BASE_URL}/study`],
      ['blog-topic', `${BASE_URL}/blog/topic`],
      ['feed', `${BASE_URL}/feed`],
      ['storm', `${BASE_URL}/storm`],
      ['racer', `${BASE_URL}/racer`],
      ['fide', `${BASE_URL}/fide`],
      ['insights', `${BASE_URL}/insights/Anonymous`],
      ['faq', `${BASE_URL}/faq#correspondence`],
      ['terms', `${BASE_URL}/terms-of-service`],
      ['privacy', `${BASE_URL}/privacy`],
      ['source', `${BASE_URL}/source`],
      ['analysis', `${BASE_URL}/analysis`],
      ['guide', `${BASE_URL}/dev/omok/opening-guide`],
      ['sample-profile', `${BASE_URL}/@/Anonymous`],
      ['sample-profile-games', `${BASE_URL}/@/Anonymous/all`],
      ['sample-profile-tv', `${BASE_URL}/@/Anonymous/tv`],
      ['sample-profile-perf', `${BASE_URL}/@/Anonymous/perf/blitz`],
      ['login', `${BASE_URL}/login`],
      ['signup', `${BASE_URL}/signup`],
      ['password-reset', `${BASE_URL}/password/reset`],
      ['magic-link', `${BASE_URL}/auth/magic-link`],
    ]);

    for (const link of home.links || []) {
      if (shouldCrawlUrl(link.href)) {
        seedUrls.set(`home_${sanitize(link.text || link.href)}`, link.href);
      }
    }

    const round = await openAiRound(browser, outDir);
    summary.round = round;
    if (round.scenario) summary.scenarios.push(round.scenario);
    if (round.url) {
      seedUrls.set('round', round.url);
    }
    for (const link of round.links || []) {
      if (shouldCrawlUrl(link.href)) seedUrls.set(`round_${sanitize(link.text || link.href)}`, link.href);
    }
    for (const link of modalShots.friend.links || []) {
      if (shouldCrawlUrl(link.href)) seedUrls.set(`friend_${sanitize(link.text || link.href)}`, link.href);
    }
    for (const link of modalShots.ai.links || []) {
      if (shouldCrawlUrl(link.href)) seedUrls.set(`ai_${sanitize(link.text || link.href)}`, link.href);
    }

    const queue = [];
    const seen = new Set();
    const schedule = (label, url) => {
      const normalized = normalizeUrl(url);
      if (!normalized || !shouldCrawlUrl(normalized) || seen.has(normalized)) return;
      seen.add(normalized);
      queue.push({ label, url: normalized });
    };

    for (const [label, url] of seedUrls.entries()) schedule(label, url);

    while (queue.length && summary.pages.length < 80) {
      const { label, url } = queue.shift();
      summary.crawledUrls.push(url);
      const result = await auditPage(auditPageInstance, url, label, outDir);
      summary.pages.push(result);
      if (result.status !== 'pass') summary.issues.push(result);
      for (const link of result.links || []) {
        schedule(`${sanitize(label)}_${sanitize(link.text || link.href)}`, link.href);
      }
    }

    summary.modals.friendLeakFlags = leakFlags(
      summary.modals.friend.title,
      (summary.modals.friend.bodySample || []).join(' '),
    );
    summary.modals.aiLeakFlags = leakFlags(
      summary.modals.ai.title,
      (summary.modals.ai.bodySample || []).join(' '),
    );
    if (summary.modals.friendLeakFlags.length) {
      summary.issues.push({
        label: 'friend-modal',
        status: 'fail',
        leakFlags: summary.modals.friendLeakFlags,
        screenshot: summary.modals.friend.screenshot,
      });
    }
    if (summary.modals.aiLeakFlags.length) {
      summary.issues.push({
        label: 'ai-modal',
        status: 'fail',
        leakFlags: summary.modals.aiLeakFlags,
        screenshot: summary.modals.ai.screenshot,
      });
    }

    const scenarioResults = [
      ...(await runQuickPairingScenarios(browser, outDir)),
      await runSetupScenario(browser, 'hook', outDir),
      await runSetupScenario(browser, 'friend', outDir),
      await runGuideScenario(browser, outDir),
      ...(await runAuthScenarios(browser, outDir)),
      ...(round.url ? [await runRoundInteractionScenario(browser, round.url, outDir)] : []),
    ];
    summary.scenarios = [...summary.scenarios, ...scenarioResults];
    for (const scenario of scenarioResults) {
      if (scenario.status !== 'pass') summary.issues.push(scenario);
    }

    summary.finishedAt = new Date().toISOString();
    summary.status = summary.issues.length ? 'fail' : 'pass';
    const summaryPath = path.join(outDir, 'summary.json');
    fs.writeFileSync(summaryPath, JSON.stringify(summary, null, 2));
    console.log(summaryPath);
    console.log(`status=${summary.status}`);
  } finally {
    await auditPageInstance.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
