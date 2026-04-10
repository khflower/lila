const fs = require('fs');
const { chromium } = require('C:/Users/hyeon/.openclaw/workspace/tmp-playwright/node_modules/playwright');

const BASE_URL = 'http://127.0.0.1:9663';
const OUT_DIR = 'C:/Users/hyeon/OneDrive/Documents/New project/playwright-output';

function ensureDir(path) {
  fs.mkdirSync(path, { recursive: true });
}

async function collectButtons(page) {
  return await page.locator('button').evaluateAll(nodes =>
    nodes.map(node => ({
      text: (node.textContent || '').trim(),
      className: node.className,
      disabled: node.disabled,
      ariaDisabled: node.getAttribute('aria-disabled'),
    })),
  );
}

async function main() {
  ensureDir(OUT_DIR);

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1200 } });
  const page = await context.newPage();

  const consoleEvents = [];
  const pageErrors = [];
  const requestFailures = [];
  const responses = [];

  page.on('console', msg => {
    consoleEvents.push({ type: msg.type(), text: msg.text() });
  });
  page.on('pageerror', err => {
    pageErrors.push({ message: String(err) });
  });
  page.on('requestfailed', req => {
    requestFailures.push({
      url: req.url(),
      method: req.method(),
      failure: req.failure(),
    });
  });
  page.on('response', async res => {
    const url = res.url();
    if (url.includes('/setup/hook/') || url.includes('/lobby/socket') || url.includes('/assets/compiled/lobby')) {
      responses.push({
        url,
        status: res.status(),
        ok: res.ok(),
      });
    }
  });

  try {
    await page.goto(BASE_URL, { waitUntil: 'networkidle', timeout: 60000 });

    const beforeButtons = await collectButtons(page);
    await page.screenshot({ path: `${OUT_DIR}/before-click.png`, fullPage: true });

    const hookButton = page.locator('button.lobby__start__button--hook');
    await hookButton.waitFor({ state: 'visible', timeout: 15000 });

    const hookButtonState = await hookButton.evaluate(node => ({
      text: (node.textContent || '').trim(),
      className: node.className,
      disabled: node.disabled,
      ariaDisabled: node.getAttribute('aria-disabled'),
    }));

    await hookButton.click({ timeout: 15000 });
    await page.waitForTimeout(3000);

    const modalVisible = await page.locator('.game-setup').count();
    const bodyHtmlSnippet = await page.locator('body').evaluate(node => node.innerHTML.slice(0, 4000));
    const afterButtons = await collectButtons(page);

    await page.screenshot({ path: `${OUT_DIR}/after-click.png`, fullPage: true });

    const result = {
      ok: true,
      baseUrl: BASE_URL,
      hookButtonState,
      modalVisible,
      beforeButtons,
      afterButtons,
      consoleEvents,
      pageErrors,
      requestFailures,
      responses,
      bodyHtmlSnippet,
    };

    fs.writeFileSync(`${OUT_DIR}/lobby-diag.json`, JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result, null, 2));
  } catch (err) {
    const result = {
      ok: false,
      error: String(err && err.stack ? err.stack : err),
      consoleEvents,
      pageErrors,
      requestFailures,
      responses,
    };
    fs.writeFileSync(`${OUT_DIR}/lobby-diag.json`, JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result, null, 2));
    process.exitCode = 1;
  } finally {
    await context.close();
    await browser.close();
  }
}

main();
