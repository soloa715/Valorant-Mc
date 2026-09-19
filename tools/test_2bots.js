import mineflayer from './minecraft-mcp-server/node_modules/mineflayer/index.js';

const delay = (ms) => new Promise(res => setTimeout(res, ms));

function createPersistentBot(name, team, agent) {
  console.log(`[Bot Manager] Starting ${name} (Team: ${team}, Agent: ${agent})...`);
  
  function startBot() {
    const bot = mineflayer.createBot({
      host: '127.0.0.1',
      port: 25565,
      username: name,
      version: '1.21.4'
    });

    bot.on('login', () => {
      console.log(`[${name}] Connected to server!`);
    });

    bot.on('spawn', async () => {
      console.log(`[${name}] Spawned at position:`, bot.entity.position);
      await delay(1000);
      console.log(`[${name}] Joining game default as team ${team}...`);
      bot.chat(`/vjoin default ${team}`);
      await delay(1000);
      console.log(`[${name}] Selecting agent ${agent}...`);
      bot.chat(`/vagent ${agent}`);
    });

    bot.on('message', (jsonMsg) => {
      const text = jsonMsg.toString();
      if (text.includes('AGENT SELECT') || text.includes('Agent select')) {
        console.log(`[${name}] AGENT_SELECT detected! Selecting ${agent}...`);
        bot.chat(`/vagent ${agent}`);
      }
    });

    bot.on('end', (reason) => {
      console.log(`[${name}] Disconnected: ${reason}. Reconnecting in 3s...`);
      setTimeout(startBot, 3000);
    });

    bot.on('error', (err) => {
      console.error(`[${name}] Error:`, err.message);
    });
  }

  startBot();
}

console.log('====================================================');
console.log(' Starting Persistent Test Bots (Jett & Reyna)');
console.log('====================================================');

createPersistentBot('ValorantBot1', 'atk', 'jett');
setTimeout(() => {
  createPersistentBot('ValorantBot2', 'atk', 'reyna');
}, 2000);
