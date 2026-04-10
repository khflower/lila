#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');
const net = require('net');
const { spawn } = require('child_process');

const ROOT_DIR = path.resolve(__dirname, '..');
const DEFAULTS = {
  baseUrl: process.env.OMOK_E2E_BASE_URL || 'http://localhost:9663',
  upstreamAppPort: Number(process.env.OMOK_E2E_UPSTREAM_APP_PORT || 19663),
  upstreamWsPort: Number(process.env.OMOK_E2E_UPSTREAM_WS_PORT || 19664),
  proxyScript: process.env.OMOK_E2E_PROXY_SCRIPT || path.join(ROOT_DIR, 'lila_dual_proxy.js'),
  outputRoot: process.env.OMOK_E2E_OUTPUT_ROOT || path.join(ROOT_DIR, 'qa-output'),
  remoteHost: process.env.OMOK_E2E_REMOTE_HOST || 'khkim@166.104.112.72',
  remoteContainer: process.env.OMOK_E2E_REMOTE_CONTAINER || 'kkh_dev',
  playwrightPath:
    process.env.OMOK_E2E_PLAYWRIGHT || 'C:/Users/hyeon/.openclaw/workspace/tmp-playwright/node_modules/playwright',
};

const SCENARIO_ORDER = [
  'lobby-create',
  'taraguchi-opening',
  'taraguchi-candidates',
  'placement-ux',
  'rules-renju',
  'clock',
  'rematch',
];

function parseArgs(argv) {
  const args = {
    scenarios: null,
    stopOnFailure: false,
    skipRecovery: false,
    outputRoot: DEFAULTS.outputRoot,
    list: false,
  };

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--scenario' || arg === '--scenarios') {
      args.scenarios = (argv[i + 1] || '')
        .split(',')
        .map(v => v.trim())
        .filter(Boolean);
      i += 1;
    } else if (arg === '--stop-on-failure') {
      args.stopOnFailure = true;
    } else if (arg === '--skip-recovery') {
      args.skipRecovery = true;
    } else if (arg === '--output-root') {
      args.outputRoot = path.resolve(argv[i + 1] || DEFAULTS.outputRoot);
      i += 1;
    } else if (arg === '--list') {
      args.list = true;
    } else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    } else if (arg) {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return args;
}

function printHelp() {
  console.log(
    [
      'Usage: node scripts/omok_e2e_regression.js [options]',
      '',
      'Options:',
      '  --list                      List available scenarios',
      '  --scenario a,b,c           Run only the selected scenarios',
      '  --output-root <path>       Override qa-output root directory',
      '  --stop-on-failure          Stop after the first failed scenario',
      '  --skip-recovery            Skip proxy/remote recovery attempts',
      '  --help                     Show this help',
    ].join('\n'),
  );
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

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function assert(condition, message, extra) {
  if (condition) return;
  const err = new Error(message);
  if (extra !== undefined) err.extra = extra;
  throw err;
}

function sanitizeFileSegment(input) {
  return input.replace(/[^a-zA-Z0-9._-]+/g, '_');
}

function parseClockText(value) {
  const match = String(value || '').match(/(\d+):(\d{2})/);
  if (!match) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}

function keyToCoord(key) {
  const match = String(key).match(/^([A-Z]+)(\d{1,2})$/i);
  if (!match) throw new Error(`Invalid board key: ${key}`);
  const letters = match[1].toUpperCase();
  let col = 0;
  for (let i = 0; i < letters.length; i += 1) col = col * 26 + (letters.charCodeAt(i) - 64);
  return { row: Number(match[2]) - 1, col: col - 1 };
}

function coordToKey(row, col) {
  return `${String.fromCharCode(65 + col)}${row + 1}`;
}

function tryRequirePlaywright() {
  try {
    return require(DEFAULTS.playwrightPath);
  } catch (_) {
    return require('playwright');
  }
}

async function httpProbe(url, timeoutMs = 3000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: controller.signal });
    const text = await res.text();
    return { ok: res.ok, status: res.status, text: text.slice(0, 200) };
  } catch (error) {
    return { ok: false, status: 0, error: String(error.message || error) };
  } finally {
    clearTimeout(timer);
  }
}

async function portOpen(port, host = '127.0.0.1', timeoutMs = 1200) {
  return new Promise(resolve => {
    const socket = new net.Socket();
    let done = false;
    const finish = result => {
      if (done) return;
      done = true;
      socket.destroy();
      resolve(result);
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
    socket.connect(port, host);
  });
}

function spawnDetached(command, args, options = {}) {
  const child = spawn(command, args, {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
    ...options,
  });
  child.unref();
  return child.pid;
}

function runCommand(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env || process.env,
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true,
    });

    const stdout = [];
    const stderr = [];

    if (options.stdin) child.stdin.write(options.stdin);
    child.stdin.end();

    child.stdout.on('data', chunk => stdout.push(Buffer.from(chunk)));
    child.stderr.on('data', chunk => stderr.push(Buffer.from(chunk)));
    child.on('error', reject);
    child.on('close', code => {
      const result = {
        code,
        stdout: Buffer.concat(stdout).toString('utf8'),
        stderr: Buffer.concat(stderr).toString('utf8'),
      };
      if (code === 0 || options.allowFailure) resolve(result);
      else reject(Object.assign(new Error(`${command} exited with code ${code}`), { result }));
    });
  });
}

async function runRemoteScript(localScriptPath, remoteHost, remoteContainer) {
  const script = fs.readFileSync(localScriptPath, 'utf8');
  return runCommand('ssh', [remoteHost, 'docker', 'exec', '-i', remoteContainer, 'bash', '-s'], { stdin: script });
}

async function waitForHealthyBase(baseUrl, timeoutMs = 45000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const probe = await httpProbe(baseUrl, 2500);
    if (probe.ok) return probe;
    await sleep(1500);
  }
  return null;
}

async function ensureRuntime(outDir, config, cliArgs) {
  const runtime = {
    startTime: new Date().toISOString(),
    baseUrl: config.baseUrl,
    steps: [],
  };

  const record = (step, details = {}) => runtime.steps.push({ at: new Date().toISOString(), step, ...details });

  const initialProbe = await httpProbe(config.baseUrl, 2500);
  record('probe-base', initialProbe);
  if (initialProbe.ok) {
    runtime.status = 'ready';
    runtime.endTime = new Date().toISOString();
    return runtime;
  }

  const upstream = {
    app: await portOpen(config.upstreamAppPort),
    ws: await portOpen(config.upstreamWsPort),
  };
  record('probe-upstream', upstream);

  if (upstream.app && upstream.ws && fs.existsSync(config.proxyScript)) {
    record('start-proxy-attempt', { proxyScript: config.proxyScript });
    const proxyPid = spawnDetached(process.execPath, [config.proxyScript], {
      cwd: ROOT_DIR,
      env: process.env,
    });
    runtime.proxyPid = proxyPid;
    const proxied = await waitForHealthyBase(config.baseUrl, 20000);
    record('probe-after-proxy', proxied || { ok: false });
    if (proxied?.ok) {
      runtime.status = 'ready';
      runtime.endTime = new Date().toISOString();
      return runtime;
    }
  }

  if (!cliArgs.skipRecovery) {
    try {
      record('remote-copy-bundles-attempt', {
        host: config.remoteHost,
        container: config.remoteContainer,
      });
      const copyBundles = await runRemoteScript(
        path.join(ROOT_DIR, 'remote_copy_bundles.sh'),
        config.remoteHost,
        config.remoteContainer,
      );
      record('remote-copy-bundles-result', copyBundles);
    } catch (error) {
      record('remote-copy-bundles-failed', {
        error: String(error.message || error),
        stdout: error.result?.stdout || '',
        stderr: error.result?.stderr || '',
      });
    }

    try {
      record('remote-restart-app-attempt', {
        host: config.remoteHost,
        container: config.remoteContainer,
      });
      const restart = await runRemoteScript(
        path.join(ROOT_DIR, 'remote_restart_app_only.sh'),
        config.remoteHost,
        config.remoteContainer,
      );
      record('remote-restart-app-result', restart);
    } catch (error) {
      record('remote-restart-app-failed', {
        error: String(error.message || error),
        stdout: error.result?.stdout || '',
        stderr: error.result?.stderr || '',
      });
    }

    const recovered = await waitForHealthyBase(config.baseUrl, 45000);
    record('probe-after-remote-recovery', recovered || { ok: false });
    if (recovered?.ok) {
      runtime.status = 'ready';
      runtime.endTime = new Date().toISOString();
      return runtime;
    }
  }

  runtime.status = 'failed';
  runtime.failureReason = !upstream.app || !upstream.ws
    ? `Local SSH tunnel appears down. Expected forwarded ports ${config.upstreamAppPort}/${config.upstreamWsPort} to accept connections.`
    : `Base URL ${config.baseUrl} did not become healthy after proxy/recovery attempts.`;
  runtime.endTime = new Date().toISOString();
  fs.writeFileSync(path.join(outDir, 'runtime.json'), JSON.stringify(runtime, null, 2));
  return runtime;
}

class ScenarioContext {
  constructor(name, dir, browser, config) {
    this.name = name;
    this.dir = dir;
    this.browser = browser;
    this.config = config;
    this.browserContexts = [];
    this.pages = new Map();
  }

  async newTwoPageContext() {
    const context = await this.browser.newContext({
      viewport: { width: 1600, height: 1200 },
      ignoreHTTPSErrors: true,
    });
    this.browserContexts.push(context);
    const owner = await context.newPage();
    const joiner = await context.newPage();
    this.pages.set('owner', owner);
    this.pages.set('joiner', joiner);
    return { context, owner, joiner };
  }

  async close() {
    for (const context of this.browserContexts.splice(0)) {
      await context.close().catch(() => {});
    }
  }

  async captureArtifacts(reason) {
    const domDir = path.join(this.dir, 'dom');
    const shotDir = path.join(this.dir, 'screens');
    ensureDir(domDir);
    ensureDir(shotDir);

    for (const [label, page] of this.pages.entries()) {
      try {
        await page.screenshot({
          path: path.join(shotDir, `${sanitizeFileSegment(label)}.png`),
          fullPage: true,
        });
      } catch (_) {}

      try {
        const state = await extractRoundState(page);
        fs.writeFileSync(path.join(domDir, `${sanitizeFileSegment(label)}.json`), JSON.stringify(state, null, 2));
      } catch (error) {
        fs.writeFileSync(
          path.join(domDir, `${sanitizeFileSegment(label)}.txt`),
          `Failed to read state: ${String(error.message || error)}\nReason: ${reason}`,
        );
      }
    }
  }
}

async function writeJson(filePath, data) {
  ensureDir(path.dirname(filePath));
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
}

async function visibleButtonByRegex(page, regex) {
  const buttons = page.locator('button');
  const count = await buttons.count();
  for (let i = 0; i < count; i += 1) {
    const button = buttons.nth(i);
    if (!(await button.isVisible().catch(() => false))) continue;
    const text = ((await button.textContent().catch(() => '')) || '').trim();
    if (regex.test(text)) return button;
  }
  return null;
}

async function extractRoundState(page) {
  return page.evaluate(() => {
    const root = document.querySelector('.round__app__board__omok-placeholder');
    const board = document.querySelector('.round__app__board__omok-placeholder__board');
    const openingButtons = Array.from(
      document.querySelectorAll('.round__app__board__omok-placeholder__opening-action'),
    ).map(node => (node.textContent || '').trim());
    const candidateLabels = Array.from(
      document.querySelectorAll('.round__app__board__omok-placeholder__svg-candidate-label'),
    ).map(node => (node.textContent || '').trim());
    const hoverRingCount = document.querySelectorAll(
      '.round__app__board__omok-placeholder__svg-hover-ring',
    ).length;
    const ghostCount = document.querySelectorAll(
      '.round__app__board__omok-placeholder__svg-ghost:not(.is-candidate)',
    ).length;
    const clocks = Array.from(document.querySelectorAll('.rclock'))
      .map(node => (node.textContent || '').trim().replace(/\s+/g, ' '))
      .filter(Boolean);
    const playerText = (document.body.innerText || '')
      .split('\n')
      .map(line => line.trim())
      .filter(Boolean)
      .slice(0, 120);

    return {
      url: location.href,
      bodyText: document.body.innerText || '',
      playerText,
      boardClickable: !!board?.classList.contains('is-clickable'),
      openingButtons,
      openingText:
        document.querySelector('.round__app__board__omok-placeholder__opening-text')?.textContent?.trim() || '',
      statusMain:
        document.querySelector('.round__app__board__omok-placeholder__status-main')?.textContent?.trim() || '',
      statusDetail:
        document.querySelector('.round__app__board__omok-placeholder__status-detail')?.textContent?.trim() || '',
      candidateLabels,
      hoverRingCount,
      ghostCount,
      clocks,
      attrs: root
        ? {
            ply: root.getAttribute('data-omok-ply'),
            turn: root.getAttribute('data-omok-turn'),
            ruleSet: root.getAttribute('data-omok-rule-set'),
            lastMove: root.getAttribute('data-omok-last-move'),
          }
        : null,
    };
  });
}

async function gotoBase(page, baseUrl) {
  await page.goto(baseUrl, { waitUntil: 'networkidle', timeout: 60000 });
}

async function openCreateModal(page) {
  const trigger = page.locator('button.lobby__start__button--hook');
  await trigger.waitFor({ state: 'visible', timeout: 20000 });
  await trigger.click();
  await page.locator('.game-setup').waitFor({ state: 'visible', timeout: 10000 });
}

async function setOmokRule(page, value) {
  await page.locator('#sf_omok_rule_set').waitFor({ state: 'visible', timeout: 10000 });
  await page.selectOption('#sf_omok_rule_set', value);
}

async function clickPreset(page, label) {
  const button = await visibleButtonByRegex(page, new RegExp(`^${label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`));
  if (button) {
    await button.click();
    return;
  }
  throw new Error(`Unable to find time preset button: ${label}`);
}

async function submitCreate(page) {
  const buttons = page.locator('.game-setup button');
  const count = await buttons.count();
  for (let i = 0; i < count; i += 1) {
    const button = buttons.nth(i);
    if (!(await button.isVisible().catch(() => false))) continue;
    const text = ((await button.textContent().catch(() => '')) || '').trim();
    if (/로비 게임 만들기|Create lobby game/i.test(text)) {
      await button.click();
      return;
    }
  }
  throw new Error('Create lobby game submit button was not found');
}

async function waitForHookRow(page, timeoutMs = 15000) {
  await page.waitForFunction(
    () => !!document.querySelector('tr.hook, tr.hook.join'),
    undefined,
    { timeout: timeoutMs },
  );
}

async function joinFirstHook(page) {
  await page.waitForFunction(
    () => !!document.querySelector('tr.hook.join td'),
    undefined,
    { timeout: 20000 },
  );
  const cell = page.locator('tr.hook.join td').first();
  await cell.click({ force: true });
}

async function waitForRound(page, timeoutMs = 30000) {
  await page.waitForFunction(
    () => !!document.querySelector('.round__app__board__omok-placeholder'),
    undefined,
    { timeout: timeoutMs },
  );
}

async function createAndJoin(ctx, { ruleSet, preset = '30+20' }) {
  const { owner, joiner } = await ctx.newTwoPageContext();
  await gotoBase(owner, ctx.config.baseUrl);
  await openCreateModal(owner);

  const options = await owner
    .locator('#sf_omok_rule_set option')
    .evaluateAll(nodes => nodes.map(node => (node.textContent || '').trim()));

  await setOmokRule(owner, ruleSet);
  await clickPreset(owner, preset);
  await submitCreate(owner);
  await waitForHookRow(owner);

  const lobbyRowText = await owner
    .locator('tr.hook, tr.hook.join')
    .first()
    .evaluate(node => (node.textContent || '').trim());

  await gotoBase(joiner, ctx.config.baseUrl);
  await joinFirstHook(joiner);
  await Promise.all([waitForRound(owner), waitForRound(joiner)]);

  return {
    pages: [owner, joiner],
    owner,
    joiner,
    ruleOptions: options,
    lobbyRowText,
  };
}

async function readPages(pages) {
  return Promise.all(pages.map(page => extractRoundState(page)));
}

function getActiveIndex(states) {
  const active = [];
  states.forEach((state, index) => {
    if (state.boardClickable) active.push(index);
  });
  if (active.length !== 1) {
    throw new Error(
      `Expected exactly one active page, got ${active.length}: ${JSON.stringify(
        states.map(s => ({
          url: s.url,
          boardClickable: s.boardClickable,
          openingButtons: s.openingButtons,
          ply: s.attrs?.ply,
        })),
        null,
        2,
      )}`,
    );
  }
  return active[0];
}

async function boardPoint(page, row, col) {
  const box = await page.locator('.round__app__board__omok-placeholder__svg').boundingBox();
  if (!box) throw new Error('Omok SVG board is not visible');
  return {
    x: box.x + (box.width * col) / 14,
    y: box.y + (box.height * row) / 14,
  };
}

async function moveMouse(page, row, col) {
  const point = await boardPoint(page, row, col);
  await page.mouse.move(point.x, point.y);
  await page.waitForTimeout(120);
}

async function hoverInfo(page, row, col) {
  await moveMouse(page, row, col);
  return extractRoundState(page);
}

async function clickBoard(page, row, col) {
  const point = await boardPoint(page, row, col);
  await page.mouse.move(point.x, point.y);
  await page.waitForTimeout(80);
  await page.mouse.click(point.x, point.y);
}

async function waitForPly(pages, expectedPly, timeoutMs = 15000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const states = await readPages(pages);
    const values = states.map(state => Number(state.attrs?.ply || 0));
    if (values.every(value => value === expectedPly)) return states;
    await sleep(300);
  }
  throw new Error(`Timed out waiting for ply ${expectedPly}`);
}

async function waitForCandidateCount(pages, expectedCount, timeoutMs = 10000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const states = await readPages(pages);
    if (states.every(state => state.candidateLabels.length === expectedCount)) return states;
    await sleep(250);
  }
  throw new Error(`Timed out waiting for candidate count ${expectedCount}`);
}

async function waitForUrlChange(page, previousUrl, timeoutMs = 20000) {
  await page.waitForFunction(oldUrl => location.href !== oldUrl, previousUrl, { timeout: timeoutMs });
}

async function clickVisibleTextButton(page, regex) {
  const button = await visibleButtonByRegex(page, regex);
  if (!button) throw new Error(`Unable to find button matching ${regex}`);
  await button.click();
}

async function expectHover(page, row, col, shouldBeActive, label) {
  const state = await hoverInfo(page, row, col);
  const active = state.hoverRingCount > 0 || state.ghostCount > 0;
  assert(
    active === shouldBeActive,
    `${label}: expected hover ${shouldBeActive ? 'active' : 'inactive'} at ${coordToKey(row, col)}`,
    state,
  );
  return state;
}

async function placeAndWait(pages, page, row, col, expectedPly) {
  await clickBoard(page, row, col);
  return waitForPly(pages, expectedPly);
}

async function scenarioLobbyCreate(ctx) {
  const created = await createAndJoin(ctx, { ruleSet: 'taraguchi10', preset: '30+20' });
  const states = await readPages(created.pages);
  return {
    urls: states.map(state => state.url),
    lobbyRowText: created.lobbyRowText,
    ruleOptions: created.ruleOptions,
    states,
  };
}

async function scenarioTaraguchiOpening(ctx) {
  const opening = await createAndJoin(ctx, { ruleSet: 'taraguchi10', preset: '30+20' });
  const pages = opening.pages;
  let states = await readPages(pages);

  assert(
    opening.ruleOptions.join('|') === 'Taraguchi-10|Renju|Freestyle',
    'Unexpected omok ruleset dropdown contents',
    opening.ruleOptions,
  );
  assert(states.every(state => state.attrs?.ruleSet?.toLowerCase().includes('taraguchi')), 'Taraguchi rule set was not active', states);
  assert(states.every(state => Number(state.attrs?.ply || 0) === 1), 'Taraguchi game should auto-start at ply 1', states);
  assert(states.every(state => state.attrs?.lastMove === 'H8'), 'Taraguchi opening should auto-place H8', states);

  let activeIndex = getActiveIndex(states);
  let passiveIndex = activeIndex === 0 ? 1 : 0;
  assert(states[activeIndex].openingButtons.includes('Swap'), 'White should see Swap on move 2', states[activeIndex]);
  assert(!states[passiveIndex].openingButtons.includes('Swap'), 'Passive page should not see Swap', states[passiveIndex]);
  await expectHover(pages[activeIndex], 0, 0, false, 'Taraguchi move 2');
  await expectHover(pages[activeIndex], 6, 6, true, 'Taraguchi move 2');
  states = await placeAndWait(pages, pages[activeIndex], 6, 6, 2);

  activeIndex = getActiveIndex(states);
  passiveIndex = activeIndex === 0 ? 1 : 0;
  assert(states[activeIndex].openingButtons.includes('Swap'), 'Black should see Swap on move 3', states[activeIndex]);
  assert(!states[passiveIndex].openingButtons.includes('Swap'), 'Passive page should not see Swap on move 3', states[passiveIndex]);
  await expectHover(pages[activeIndex], 0, 0, false, 'Taraguchi move 3');
  await expectHover(pages[activeIndex], 7, 6, true, 'Taraguchi move 3');
  states = await placeAndWait(pages, pages[activeIndex], 7, 6, 3);

  activeIndex = getActiveIndex(states);
  assert(states[activeIndex].openingButtons.includes('Swap'), 'White should still see Swap on move 4', states[activeIndex]);
  await expectHover(pages[activeIndex], 0, 0, false, 'Taraguchi move 4');
  await expectHover(pages[activeIndex], 6, 8, true, 'Taraguchi move 4');
  states = await placeAndWait(pages, pages[activeIndex], 6, 8, 4);

  activeIndex = getActiveIndex(states);
  assert(states[activeIndex].openingButtons.includes('Swap'), 'Black should still be able to swap after move 4', states[activeIndex]);
  assert(states[activeIndex].openingButtons.includes('Propose 10'), 'Black should be able to start candidate mode after move 4', states[activeIndex]);
  await expectHover(pages[activeIndex], 0, 0, false, 'Taraguchi simple fifth');

  const swapCase = await createAndJoin(ctx, { ruleSet: 'taraguchi10', preset: '5+0' });
  const swapPages = swapCase.pages;
  let swapStates = await readPages(swapPages);
  let swapActiveIndex = getActiveIndex(swapStates);
  assert(swapStates[swapActiveIndex].openingButtons.includes('Swap'), 'Initial Taraguchi state should expose Swap', swapStates[swapActiveIndex]);
  await clickVisibleTextButton(swapPages[swapActiveIndex], /^Swap$/i);
  await sleep(1200);
  swapStates = await readPages(swapPages);
  swapActiveIndex = getActiveIndex(swapStates);
  assert(!swapStates[0].openingButtons.includes('Swap') && !swapStates[1].openingButtons.includes('Swap'), 'Swap should disappear immediately after the first swap', swapStates);
  await expectHover(swapPages[swapActiveIndex], 0, 0, false, 'Taraguchi swapped move 2');
  await expectHover(swapPages[swapActiveIndex], 6, 6, true, 'Taraguchi swapped move 2');

  return {
    noSwapFlow: states,
    swapFlow: swapStates,
  };
}

async function scenarioTaraguchiCandidates(ctx) {
  const setup = await createAndJoin(ctx, { ruleSet: 'taraguchi10', preset: '5+0' });
  const pages = setup.pages;
  let states = await readPages(pages);

  let activeIndex = getActiveIndex(states);
  states = await placeAndWait(pages, pages[activeIndex], 6, 6, 2);
  activeIndex = getActiveIndex(states);
  states = await placeAndWait(pages, pages[activeIndex], 7, 6, 3);
  activeIndex = getActiveIndex(states);
  states = await placeAndWait(pages, pages[activeIndex], 6, 8, 4);
  activeIndex = getActiveIndex(states);
  assert(states[activeIndex].openingButtons.includes('Propose 10'), 'Propose 10 should be available at ply 4', states[activeIndex]);
  await clickVisibleTextButton(pages[activeIndex], /^Propose 10$/i);
  await sleep(1000);

  const candidateKeys = ['A1', 'O15', 'A15', 'O1', 'B1', 'C1', 'D1', 'E1', 'F1', 'G1'];
  for (let i = 0; i < candidateKeys.length; i += 1) {
    const { row, col } = keyToCoord(candidateKeys[i]);
    await expectHover(pages[activeIndex], row, col, true, `Candidate placement ${candidateKeys[i]}`);
    await clickBoard(pages[activeIndex], row, col);
    states = await waitForCandidateCount(pages, i + 1);
  }

  const candidateStates = await readPages(pages);
  const selectorIndex = getActiveIndex(candidateStates);
  assert(
    candidateStates[selectorIndex].candidateLabels.length === 10,
    'White should see 10 proposed candidates',
    candidateStates[selectorIndex],
  );

  for (let i = 0; i < candidateKeys.length - 1; i += 1) {
    const { row, col } = keyToCoord(candidateKeys[i]);
    await clickBoard(pages[selectorIndex], row, col);
    const expectedCount = candidateKeys.length - (i + 1);
    if (expectedCount > 1) {
      states = await waitForCandidateCount(pages, expectedCount);
    } else {
      states = await waitForPly(pages, 5);
    }
  }

  states = await readPages(pages);
  assert(states.every(state => state.candidateLabels.length === 0), 'Candidate labels should disappear after the final selection', states);
  assert(states.every(state => Number(state.attrs?.ply || 0) === 5), 'Taraguchi should advance to ply 5 after candidate resolution', states);
  assert(states.every(state => state.attrs?.lastMove === 'G1'), 'The remaining candidate should become the final fifth move', states);

  return {
    candidateKeys,
    finalStates: states,
  };
}

async function scenarioPlacementUx(ctx) {
  const setup = await createAndJoin(ctx, { ruleSet: 'freestyle', preset: '5+0' });
  const pages = setup.pages;
  let states = await readPages(pages);

  assert(states.every(state => state.attrs?.ruleSet?.toLowerCase().includes('freestyle')), 'Freestyle game should stay freestyle', states);
  assert(states.every(state => Number(state.attrs?.ply || 0) === 0), 'Freestyle should start from an empty board', states);

  const activeIndex = getActiveIndex(states);
  await expectHover(pages[activeIndex], 7, 7, true, 'Freestyle first move hover');
  states = await placeAndWait(pages, pages[activeIndex], 7, 7, 1);

  const nextActiveIndex = getActiveIndex(states);
  await expectHover(pages[nextActiveIndex], 7, 7, false, 'Occupied intersection hover');
  const beforePly = Number(states[nextActiveIndex].attrs?.ply || 0);
  await clickBoard(pages[nextActiveIndex], 7, 7);
  await sleep(700);
  states = await readPages(pages);
  assert(states.every(state => Number(state.attrs?.ply || 0) === beforePly), 'Clicking an occupied point must not advance the game', states);
  await expectHover(pages[nextActiveIndex], 7, 8, true, 'Freestyle second move hover');
  states = await placeAndWait(pages, pages[nextActiveIndex], 7, 8, 2);
  assert(states.every(state => Number(state.attrs?.ply || 0) === 2), 'Both players should place successfully in one shared browser context', states);

  return { states };
}

async function playRenjuForbiddenGame(pages) {
  const sequence = ['A1', 'B8', 'A2', 'C8', 'A3', 'D8', 'A4', 'E8', 'A5', 'G8', 'A6', 'F8'];
  let expectedPly = 2;
  for (const key of sequence) {
    const states = await readPages(pages);
    const activeIndex = getActiveIndex(states);
    const { row, col } = keyToCoord(key);
    await clickBoard(pages[activeIndex], row, col);
    await waitForPly(pages, expectedPly);
    expectedPly += 1;
  }
  return readPages(pages);
}

async function scenarioRulesRenju(ctx) {
  const setup = await createAndJoin(ctx, { ruleSet: 'renju', preset: '5+0' });
  const pages = setup.pages;
  let states = await readPages(pages);

  assert(states.every(state => state.attrs?.ruleSet?.toLowerCase().includes('renju')), 'Renju game should stay Renju', states);
  assert(states.every(state => Number(state.attrs?.ply || 0) === 1), 'Renju should start after the automatic H8 move', states);
  assert(states.every(state => state.attrs?.lastMove === 'H8'), 'Renju should auto-place H8', states);

  states = await playRenjuForbiddenGame(pages);
  assert(states.every(state => state.attrs?.lastMove === 'F8'), 'Forbidden move should still be recorded on the board', states);
  assert(
    states.some(state => /white|백/i.test(`${state.statusMain} ${state.statusDetail} ${state.bodyText}`)),
    'Renju forbidden move should award the game to White',
    states,
  );

  return { states };
}

async function scenarioClock(ctx) {
  const setup = await createAndJoin(ctx, { ruleSet: 'taraguchi10', preset: '30+20' });
  const pages = setup.pages;
  let states = await readPages(pages);
  const whiteIndex = getActiveIndex(states);
  const blackIndex = whiteIndex === 0 ? 1 : 0;

  const initialWhiteBottom = parseClockText(states[whiteIndex].clocks[states[whiteIndex].clocks.length - 1]);
  const initialBlackBottom = parseClockText(states[blackIndex].clocks[states[blackIndex].clocks.length - 1]);
  assert(initialWhiteBottom === 1800 && initialBlackBottom === 1800, '30+20 should begin at 30:00 for both sides', states);
  assert(!states[whiteIndex].bodyText.includes('첫 수') && !states[blackIndex].bodyText.includes('첫 수'), 'Omok clocks should not show first-move expiration text', states);

  states = await placeAndWait(pages, pages[whiteIndex], 6, 6, 2);
  await sleep(1400);
  states = await readPages(pages);
  const whiteAfterSecond = parseClockText(states[whiteIndex].clocks[states[whiteIndex].clocks.length - 1]);
  const blackAfterSecond = parseClockText(states[blackIndex].clocks[states[blackIndex].clocks.length - 1]);
  assert(whiteAfterSecond !== null && whiteAfterSecond > 1800, 'White should receive the +20 increment after move 2', states);
  assert(blackAfterSecond !== null && blackAfterSecond < 1800, 'Only Black should be ticking after White moves', states);

  states = await placeAndWait(pages, pages[blackIndex], 7, 6, 3);
  await sleep(1400);
  states = await readPages(pages);
  const whiteAfterThird = parseClockText(states[whiteIndex].clocks[states[whiteIndex].clocks.length - 1]);
  const blackAfterThird = parseClockText(states[blackIndex].clocks[states[blackIndex].clocks.length - 1]);
  assert(blackAfterThird !== null && blackAfterThird > blackAfterSecond, 'Black should receive the +20 increment after move 3', states);
  assert(whiteAfterThird !== null && whiteAfterThird < whiteAfterSecond, 'Only White should be ticking after Black moves', states);

  return {
    initial: { white: initialWhiteBottom, black: initialBlackBottom },
    afterWhite: { white: whiteAfterSecond, black: blackAfterSecond },
    afterBlack: { white: whiteAfterThird, black: blackAfterThird },
  };
}

async function scenarioRematch(ctx) {
  const setup = await createAndJoin(ctx, { ruleSet: 'renju', preset: '5+0' });
  const pages = setup.pages;
  const beforeUrls = (await readPages(pages)).map(state => state.url);
  let states = await playRenjuForbiddenGame(pages);

  assert(
    states.some(state => /rematch|재대결/i.test(state.bodyText)),
    'Finished Renju game should expose rematch controls',
    states,
  );

  await clickVisibleTextButton(pages[0], /rematch|재대결/i);
  await clickVisibleTextButton(pages[1], /rematch|재대결/i);
  await Promise.all([waitForUrlChange(pages[0], beforeUrls[0]), waitForUrlChange(pages[1], beforeUrls[1])]);
  await Promise.all([waitForRound(pages[0]), waitForRound(pages[1])]);
  states = await readPages(pages);

  assert(states.every(state => state.url.includes(ctx.config.baseUrl.replace(/\/$/, ''))), 'Rematch should stay on the omok local base URL', states);
  assert(states.every(state => state.attrs?.ruleSet?.toLowerCase().includes('renju')), 'Rematch should preserve Renju rules', states);
  const clockSeconds = states.map(state => parseClockText(state.clocks[state.clocks.length - 1]));
  assert(clockSeconds.every(value => value !== null && value >= 295), 'Rematch should preserve the 5+0 clock setting', states);

  return {
    beforeUrls,
    afterUrls: states.map(state => state.url),
    states,
  };
}

const SCENARIOS = {
  'lobby-create': scenarioLobbyCreate,
  'taraguchi-opening': scenarioTaraguchiOpening,
  'taraguchi-candidates': scenarioTaraguchiCandidates,
  'placement-ux': scenarioPlacementUx,
  'rules-renju': scenarioRulesRenju,
  clock: scenarioClock,
  rematch: scenarioRematch,
};

async function runScenario(browser, config, outDir, name, fn) {
  const scenarioDir = path.join(outDir, name);
  ensureDir(scenarioDir);
  const ctx = new ScenarioContext(name, scenarioDir, browser, config);
  const startedAt = new Date().toISOString();
  let result = null;
  let failureReason = null;
  let status = 'passed';

  try {
    result = await fn(ctx);
  } catch (error) {
    status = 'failed';
    failureReason = String(error.message || error);
    result = {
      error: String(error.stack || error),
      extra: error.extra || null,
    };
    await ctx.captureArtifacts(failureReason);
  } finally {
    await ctx.close();
  }

  const endedAt = new Date().toISOString();
  const scenarioJson = {
    scenario: name,
    status,
    startTime: startedAt,
    endTime: endedAt,
    urls:
      result?.states?.map?.(state => state.url) ||
      result?.afterUrls ||
      result?.urls ||
      [],
    keyState: result,
    failureReason,
  };
  await writeJson(path.join(scenarioDir, `${name}.json`), scenarioJson);
  return scenarioJson;
}

async function main() {
  const cliArgs = parseArgs(process.argv.slice(2));
  if (cliArgs.list) {
    console.log(SCENARIO_ORDER.join('\n'));
    return;
  }

  const config = {
    ...DEFAULTS,
    outputRoot: cliArgs.outputRoot,
  };
  const selected = cliArgs.scenarios || SCENARIO_ORDER;
  for (const name of selected) assert(SCENARIOS[name], `Unknown scenario: ${name}`);

  const runDir = path.join(config.outputRoot, timestampId());
  ensureDir(runDir);

  const runtime = await ensureRuntime(runDir, config, cliArgs);
  await writeJson(path.join(runDir, 'runtime.json'), runtime);
  if (runtime.status !== 'ready') {
    await writeJson(path.join(runDir, 'summary.json'), {
      status: 'failed',
      startTime: runtime.startTime,
      endTime: runtime.endTime,
      failureReason: runtime.failureReason,
      scenarios: [],
      runtime,
    });
    console.error(runtime.failureReason);
    process.exitCode = 1;
    return;
  }

  const { chromium } = tryRequirePlaywright();
  const browser = await chromium.launch({ headless: true });
  const summary = [];
  const startedAt = new Date().toISOString();

  try {
    for (const name of selected) {
      const scenarioSummary = await runScenario(browser, config, runDir, name, SCENARIOS[name]);
      summary.push(scenarioSummary);
      if (scenarioSummary.status === 'failed' && cliArgs.stopOnFailure) break;
    }
  } finally {
    await browser.close().catch(() => {});
  }

  const failed = summary.filter(item => item.status !== 'passed');
  const finalSummary = {
    status: failed.length ? 'failed' : 'passed',
    startTime: startedAt,
    endTime: new Date().toISOString(),
    failureReason: failed[0]?.failureReason || null,
    runtime,
    scenarios: summary,
  };
  await writeJson(path.join(runDir, 'summary.json'), finalSummary);

  console.log(JSON.stringify({ runDir, status: finalSummary.status, failed: failed.map(item => item.scenario) }, null, 2));
  if (failed.length) process.exitCode = 1;
}

main().catch(error => {
  console.error(error.stack || error);
  process.exitCode = 1;
});
