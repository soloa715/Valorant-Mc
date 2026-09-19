import mineflayer from './minecraft-mcp-server/node_modules/mineflayer/index.js';

const delay = (ms) => new Promise(res => setTimeout(res, ms));

async function main() {
  console.log('[Match Setup] Connecting ValorantBot1 and ValorantBot2...');

  const bot1 = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'ValorantBot1', version: '1.21.4' });
  await delay(1000);
  const bot2 = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'ValorantBot2', version: '1.21.4' });

  bot1.on('message', (m) => console.log('[Bot1 Chat]:', m.toAnsi()));
  bot2.on('message', (m) => console.log('[Bot2 Chat]:', m.toAnsi()));

  await delay(2000);
  console.log('[Match Setup] Clearing previous games and creating game test1...');
  bot1.chat('/vleave');
  bot2.chat('/vleave');

  await delay(1500);
  console.log('[Match Setup] Joining game test1 on ATTACKERS...');
  bot1.chat('/vjoin test1 atk');
  bot2.chat('/vjoin test1 atk');

  await delay(2000);
  console.log('[Match Setup] Selecting agents...');
  bot1.chat('/vagent jett');
  bot2.chat('/vagent reyna');

  await delay(2000);
  console.log('[Match Setup] Starting match test1...');
  bot1.chat('/vstart test1');
}

main().catch(console.error);
