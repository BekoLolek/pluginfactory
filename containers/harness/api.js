'use strict';
const vec3 = require('vec3');
const nbt = require('prismarine-nbt');

/**
 * The helper API handed to every test scenario. It wraps a connected Mineflayer
 * bot (client view: chat received, inventory, blocks, health, position) and an
 * RCON client (server view: run commands, read responses, set up world state).
 *
 * Scenarios should assert on OBSERVABLE effects — chat messages, item
 * material/display-name/count, block types, health — and use rcon() for
 * server-side checks. Server-only state like PersistentDataContainer tags is
 * NOT visible to a client bot.
 */
class ScenarioApi {
  constructor(bot, rcon, log) {
    this.bot = bot;
    this.rcon = rcon;
    this._log = log;
    this.messages = [];
    bot.on('messagestr', (m) => this.messages.push(String(m)));
  }

  // Reset the per-scenario chat buffer (called by the runner before each scenario).
  _reset() { this.messages = []; }

  log(msg) { this._log.push(String(msg)); }

  wait(ms) { return new Promise((r) => setTimeout(r, ms)); }

  assert(cond, msg) {
    if (!cond) throw new Error('Assertion failed: ' + (msg || 'condition was false'));
    this.log('OK: ' + (msg || 'assertion passed'));
  }

  /** Run a plugin/server command AS THE BOT (the bot is op'd). No leading slash needed. */
  async runCommand(cmd) {
    const c = cmd.startsWith('/') ? cmd : '/' + cmd;
    this.log('runCommand ' + c);
    this.bot.chat(c);
    await this.wait(400); // let the server process + reply
  }

  /** Send a plain chat message as the bot. */
  async say(msg) { this.log('say ' + msg); this.bot.chat(msg); await this.wait(200); }

  /** Run a command via RCON (server console) and return its response text. */
  async serverCommand(cmd) {
    const res = await this.rcon.send(cmd);
    this.log('rcon ' + cmd + ' -> ' + JSON.stringify(res));
    return res;
  }

  /** Wait until a chat/system message containing `substr` is received (or throw on timeout). */
  expectChat(substr, timeoutMs = 6000) {
    this.log('expectChat "' + substr + '"');
    const deadline = Date.now() + timeoutMs;
    return new Promise((resolve, reject) => {
      const check = () => {
        if (this.messages.some((m) => m.includes(substr))) {
          this.log('OK: saw chat "' + substr + '"');
          return resolve(true);
        }
        if (Date.now() > deadline) {
          return reject(new Error(
            'Expected chat containing "' + substr + '" but it never arrived. Recent: '
            + this.messages.slice(-6).join(' | ')));
        }
        setTimeout(check, 200);
      };
      check();
    });
  }

  /** Current inventory as plain objects. */
  getInventory() {
    return this.bot.inventory.items().map((it) => ({
      name: it.name,
      count: it.count,
      displayName: itemDisplayName(it) || it.displayName || it.name,
    }));
  }

  /** Assert the bot holds an item matching material and/or display-name, with at least minCount. */
  async expectItem({ material, nameContains, minCount = 1 }, timeoutMs = 4000) {
    this.log('expectItem ' + JSON.stringify({ material, nameContains, minCount }));
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      const items = this.getInventory();
      const total = items
        .filter((it) => (!material || it.name === material)
          && (!nameContains || (it.displayName || '').toLowerCase().includes(nameContains.toLowerCase())))
        .reduce((s, it) => s + it.count, 0);
      if (total >= minCount) { this.log('OK: have ' + total + ' matching item(s)'); return true; }
      if (Date.now() > deadline) {
        throw new Error('Expected >=' + minCount + ' item matching '
          + JSON.stringify({ material, nameContains }) + ' but inventory was '
          + JSON.stringify(items));
      }
      await this.wait(300);
    }
  }

  /** Block type name at world coords. */
  getBlock(x, y, z) {
    const b = this.bot.blockAt(vec3(x, y, z));
    return b ? b.name : null;
  }

  async expectBlock(x, y, z, material, timeoutMs = 4000) {
    this.log('expectBlock ' + [x, y, z].join(',') + ' == ' + material);
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      const name = this.getBlock(x, y, z);
      if (name === material) { this.log('OK: block is ' + material); return true; }
      if (Date.now() > deadline) {
        throw new Error('Expected block ' + material + ' at ' + [x, y, z].join(',') + ' but found ' + name);
      }
      await this.wait(300);
    }
  }

  /** Teleport the bot via RCON (deterministic; no pathfinding needed). */
  async tp(x, y, z) {
    await this.serverCommand('tp ' + this.bot.username + ' ' + x + ' ' + y + ' ' + z);
    await this.wait(600);
  }

  health() { return { health: this.bot.health, food: this.bot.food }; }
}

/**
 * Reduce a Minecraft chat component (string, JSON string, object, or array) to
 * plain text. Item display names arrive as components in 1.20.5+.
 */
/** Best-effort plain-text display name of a Mineflayer item (handles tagged NBT). */
function itemDisplayName(it) {
  let cn = it && it.customName;
  if (cn == null) return '';
  try {
    if (typeof cn === 'object' && cn.type) cn = nbt.simplify(cn);
  } catch (_) { /* fall through with the raw value */ }
  return componentText(cn);
}

function componentText(c) {
  if (c == null) return '';
  if (typeof c === 'string') {
    const t = c.trim();
    if (t.startsWith('{') || t.startsWith('[')) {
      try { return componentText(JSON.parse(t)); } catch (_) { return c; }
    }
    return c;
  }
  if (Array.isArray(c)) return c.map(componentText).join('');
  let s = typeof c.text === 'string' ? c.text : '';
  if (Array.isArray(c.extra)) s += c.extra.map(componentText).join('');
  if (!s && typeof c.translate === 'string') s = c.translate;
  return s;
}

module.exports = { ScenarioApi, componentText };
