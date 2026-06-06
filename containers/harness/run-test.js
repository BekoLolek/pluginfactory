'use strict';
/*
 * Functional-test runner. Runs INSIDE the test container (loopback to the
 * Paper server, so network=none isolation is preserved).
 *
 *   node run-test.js <scenarioFile>
 *
 * The scenario file is a CommonJS module:
 *   module.exports = { scenarios: [ { name, run: async (api) => { ... } }, ... ] };
 *
 * Emits a JSON result to stdout (and /work/result.json):
 *   { passed: bool, scenarios: [ { name, passed, error, logs:[...] } ] }
 *
 * Always exits 0 — the JSON carries the verdict (infra failures set passed:false
 * with an `error`).
 */
const fs = require('fs');
const path = require('path');
const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');
const { ScenarioApi } = require('./api');

const SCENARIO_FILE = process.argv[2] || '/work/scenario.js';
const HOST = process.env.MC_HOST || '127.0.0.1';
const PORT = parseInt(process.env.MC_PORT || '25565', 10);
const VERSION = process.env.MC_VERSION || '1.21.4';
const BOT = process.env.BOT_NAME || 'TesterBot';
const RCON_PORT = parseInt(process.env.RCON_PORT || '25575', 10);
const RCON_PASSWORD = process.env.RCON_PASSWORD || 'pf-rcon-pass';
const SPAWN_TIMEOUT = parseInt(process.env.SPAWN_TIMEOUT_MS || '45000', 10);
const SCENARIO_TIMEOUT = parseInt(process.env.SCENARIO_TIMEOUT_MS || '30000', 10);

function emit(result) {
  const json = JSON.stringify(result, null, 2);
  try { fs.writeFileSync('/work/result.json', json); } catch (_) { /* stdout is the source of truth */ }
  process.stdout.write('\n===PF_RESULT_BEGIN===\n' + json + '\n===PF_RESULT_END===\n');
}

function withTimeout(promise, ms, label) {
  return Promise.race([
    promise,
    new Promise((_, rej) => setTimeout(() => rej(new Error('timed out after ' + ms + 'ms: ' + label)), ms)),
  ]);
}

async function connectBot() {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: HOST, port: PORT, username: BOT, auth: 'offline', version: VERSION });
    const t = setTimeout(() => reject(new Error('bot did not spawn within ' + SPAWN_TIMEOUT + 'ms')), SPAWN_TIMEOUT);
    bot.once('spawn', () => { clearTimeout(t); resolve(bot); });
    bot.on('error', (e) => { clearTimeout(t); reject(e); });
    bot.on('kicked', (r) => { clearTimeout(t); reject(new Error('kicked: ' + r)); });
  });
}

async function main() {
  if (!fs.existsSync(SCENARIO_FILE)) {
    return emit({ passed: false, scenarios: [], error: 'scenario file not found: ' + SCENARIO_FILE });
  }

  let mod;
  try {
    mod = require(path.resolve(SCENARIO_FILE));
  } catch (e) {
    return emit({ passed: false, scenarios: [], error: 'scenario failed to load: ' + e.message });
  }
  const scenarios = Array.isArray(mod.scenarios) ? mod.scenarios
    : (typeof mod === 'function' ? [{ name: 'default', run: mod }] : []);
  if (scenarios.length === 0) {
    return emit({ passed: false, scenarios: [], error: 'no scenarios exported' });
  }

  let bot, rcon;
  try {
    bot = await connectBot();
    rcon = await Rcon.connect({ host: HOST, port: RCON_PORT, password: RCON_PASSWORD });
    // Op the bot and put it in creative so it can run admin commands and hold items.
    await rcon.send('op ' + BOT);
    await rcon.send('gamemode creative ' + BOT);
    await new Promise((r) => setTimeout(r, 500));
  } catch (e) {
    return emit({ passed: false, scenarios: [], error: 'setup failed: ' + e.message });
  }

  const results = [];
  for (const sc of scenarios) {
    const logs = [];
    const api = new ScenarioApi(bot, rcon, logs);
    api._reset();
    try {
      await withTimeout(Promise.resolve(sc.run(api)), SCENARIO_TIMEOUT, sc.name);
      results.push({ name: sc.name, passed: true, error: null, logs });
    } catch (e) {
      results.push({ name: sc.name, passed: false, error: e.message, logs });
    }
  }

  try { await rcon.end(); } catch (_) {}
  try { bot.quit(); } catch (_) {}

  emit({ passed: results.every((r) => r.passed), scenarios: results });
  // Give streams a tick to flush, then exit.
  setTimeout(() => process.exit(0), 300);
}

main().catch((e) => { emit({ passed: false, scenarios: [], error: 'harness crashed: ' + e.message }); setTimeout(() => process.exit(0), 300); });
