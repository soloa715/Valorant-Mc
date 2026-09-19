import mineflayer from './minecraft-mcp-server/node_modules/mineflayer/index.js';

console.log('[TestBot] Connecting to localhost:25565 as ValorantTester...');

const bot = mineflayer.createBot({
  host: '127.0.0.1',
  port: 25565,
  username: 'ValorantTester',
  version: '1.21.4'
});

bot.on('login', () => {
  console.log('[TestBot] Logged into server successfully!');
});

bot.on('spawn', async () => {
  console.log('[TestBot] Spawned in world at position:', bot.entity.position);

  const delay = (ms) => new Promise(res => setTimeout(res, ms));

  await delay(2000);
  console.log('[TestBot] Sending /vjoin ...');
  bot.chat('/vjoin');

  await delay(2000);
  console.log('[TestBot] Sending /vagent jett ...');
  bot.chat('/vagent jett');

  await delay(2000);
  console.log('[TestBot] Sending /vquick ...');
  bot.chat('/vquick');

  await delay(5000);
  console.log('[TestBot] Current position after quick match start:', bot.entity.position);
  console.log('[TestBot] Current inventory items:');
  bot.inventory.items().forEach(item => {
    console.log(` - Slot ${item.slot}: ${item.name} x${item.count}`);
  });

  // Jump and move slightly to test movement physics
  bot.setControlState('jump', true);
  bot.setControlState('forward', true);
  await delay(1000);
  bot.setControlState('jump', false);
  bot.setControlState('forward', false);

  console.log('[TestBot] Position after movement:', bot.entity.position);

  await delay(3000);
  console.log('[TestBot] Test sequence completed. Disconnecting...');
  bot.quit();
  process.exit(0);
});

bot.on('message', (jsonMsg) => {
  console.log('[Server Chat]:', jsonMsg.toAnsi());
});

bot.on('kicked', (reason) => {
  console.log('[TestBot] Kicked:', reason);
});

bot.on('error', (err) => {
  console.error('[TestBot] Error:', err);
});
