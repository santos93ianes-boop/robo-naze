
(() => {
'use strict';

const canvas = document.getElementById('game');
const ctx = canvas.getContext('2d');
const ui = {
  start: document.getElementById('startScreen'), game: document.getElementById('gameScreen'),
  score: document.getElementById('score'), level: document.getElementById('levelText'),
  difficulty: document.getElementById('difficulty'), lives: document.getElementById('lives'),
  coins: document.getElementById('coins'), stars: document.getElementById('stars'),
  power: document.getElementById('power'), toast: document.getElementById('toast'),
  pause: document.getElementById('pauseScreen'), result: document.getElementById('resultScreen'),
  settings: document.getElementById('settingsScreen')
};

const saved = JSON.parse(localStorage.getItem('roboMazeSave') || '{}');
let level = saved.level || 1;
let score = saved.score || 0;
let lives = 3;
let maze, rows, cols, cell, player, enemies=[], coins=[], powerups=[], exitCell;
let paused=false, running=false, startTime=0, collected=0, requiredCoins=0, lastEnemyMove=0;
let shieldUntil=0, freezeUntil=0;
let settings = Object.assign({sound:true,vibration:true,swipe:true}, saved.settings || {});

document.getElementById('soundToggle').checked = settings.sound;
document.getElementById('vibrationToggle').checked = settings.vibration;
document.getElementById('swipeToggle').checked = settings.swipe;

function rand(seed){
  let t = seed += 0x6D2B79F5;
  return function(){
    t += 0x6D2B79F5;
    let x=t; x=Math.imul(x ^ x>>>15, x|1); x^=x+Math.imul(x ^ x>>>7,x|61);
    return ((x ^ x>>>14)>>>0)/4294967296;
  }
}
function key(r,c){ return r+','+c; }
function neighbors(r,c){
  return [[-1,0],[1,0],[0,-1],[0,1]].map(([dr,dc])=>[r+dr,c+dc,dr,dc])
    .filter(([nr,nc])=>nr>=0&&nr<rows&&nc>=0&&nc<cols);
}
function generateMaze(seed){
  const random = rand(seed*99991+77);
  const m = Array.from({length:rows},()=>Array.from({length:cols},()=>({v:false,w:[1,1,1,1]})));
  const stack=[[0,0]]; m[0][0].v=true;
  const dirIndex = (dr,dc)=> dr===-1?0:dc===1?1:dr===1?2:3;
  while(stack.length){
    let [r,c]=stack[stack.length-1];
    let opts=neighbors(r,c).filter(([nr,nc])=>!m[nr][nc].v);
    if(!opts.length){stack.pop();continue}
    let [nr,nc,dr,dc]=opts[Math.floor(random()*opts.length)];
    const a=dirIndex(dr,dc), b=(a+2)%4;
    m[r][c].w[a]=0; m[nr][nc].w[b]=0; m[nr][nc].v=true; stack.push([nr,nc]);
  }
  // Mais rotas de fuga: cria loops e cruzamentos extras em todas as fases.
  // O jogo continua ficando difícil pelos inimigos, velocidade e tamanho do mapa,
  // mas o jogador quase sempre tem mais de uma rota para escapar.
  const escapeBase = level <= 10 ? 0.24 : level <= 25 ? 0.22 : level <= 40 ? 0.20 : level <= 60 ? 0.18 : 0.16;
  const extra = Math.min(0.32, escapeBase + (level % 5) * 0.006);
  for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){
    if(random()<extra){
      const opts=neighbors(r,c);
      const [nr,nc,dr,dc]=opts[Math.floor(random()*opts.length)];
      const a=dirIndex(dr,dc), b=(a+2)%4; m[r][c].w[a]=0;m[nr][nc].w[b]=0;
    }
  }

  // Garante alguns cruzamentos amplos distribuídos pelo mapa para permitir fuga.
  const junctions = Math.max(3, Math.floor(rows * cols * 0.045));
  for(let i=0;i<junctions;i++){
    const r=1+Math.floor(random()*Math.max(1,rows-2));
    const c=1+Math.floor(random()*Math.max(1,cols-2));
    const dirs=neighbors(r,c);
    for(let n=0;n<Math.min(2,dirs.length);n++){
      const pick=dirs[Math.floor(random()*dirs.length)];
      const [nr,nc,dr,dc]=pick;
      const a=dirIndex(dr,dc), b=(a+2)%4;
      m[r][c].w[a]=0; m[nr][nc].w[b]=0;
    }
  }
  return m;
}
function difficultyName(){
  if(level<=10)return 'TREINO';
  if(level<=25)return 'FÁCIL';
  if(level<=40)return 'MÉDIO';
  if(level<=60)return 'DIFÍCIL';
  if(level<=80)return 'EXPERT';
  return 'INSANO';
}
function initLevel(){
  const size = Math.min(15, 7 + Math.floor((level-1)/10));
  rows=cols=size; cell=canvas.width/cols;
  maze=generateMaze(level);
  player={r:rows-1,c:Math.floor(cols/2)};
  exitCell={r:0,c:Math.floor(cols/2)};
  const random=rand(level*131);
  const openCells=[];
  for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){
    if((r!==player.r||c!==player.c)&&(r!==exitCell.r||c!==exitCell.c)) openCells.push({r,c});
  }
  const shuffle=a=>{for(let i=a.length-1;i>0;i--){let j=Math.floor(random()*(i+1));[a[i],a[j]]=[a[j],a[i]]}return a};
  shuffle(openCells);
  requiredCoins = Math.min(openCells.length-5, 6 + Math.floor(level*0.55));
  coins = openCells.splice(0, requiredCoins).map(x=>({...x}));
  const enemyCount = Math.min(7, 1 + Math.floor((level-1)/12));
  enemies = openCells.splice(0, enemyCount).map((x,i)=>({...x,type:i%4}));
  powerups = [];
  if(level>=18 && openCells.length) powerups.push({...openCells.shift(),type:'shield'});
  if(level>=32 && openCells.length) powerups.push({...openCells.shift(),type:'freeze'});
  collected=0;lives=3;shieldUntil=0;freezeUntil=0;paused=false;running=true;startTime=performance.now();
  updateUI(); draw(); showToast(level%10===0 ? '⚠ FASE CHEFÃO!' : `Nível ${level} • ${difficultyName()}`);
}
function canMove(entity,dr,dc){
  const r=entity.r,c=entity.c;
  const idx=dr===-1?0:dc===1?1:dr===1?2:3;
  return !maze[r][c].w[idx];
}
function movePlayer(dr,dc){
  if(!running||paused)return;
  if(canMove(player,dr,dc)){ player.r+=dr;player.c+=dc; checkCell(); draw(); }
}
function checkCell(){
  const i=coins.findIndex(x=>x.r===player.r&&x.c===player.c);
  if(i>=0){coins.splice(i,1);collected++;score+=10+level;beep(720,0.035);vibe(18)}
  const p=powerups.findIndex(x=>x.r===player.r&&x.c===player.c);
  if(p>=0){
    const item=powerups.splice(p,1)[0];
    if(item.type==='shield'){shieldUntil=performance.now()+8000;showToast('🛡 ESCUDO: 8s')}
    if(item.type==='freeze'){freezeUntil=performance.now()+6500;showToast('❄ INIMIGOS CONGELADOS')}
    beep(980,.08);
  }
  if(player.r===exitCell.r&&player.c===exitCell.c){
    if(collected>=requiredCoins) finishLevel();
    else showToast(`Colete mais ${requiredCoins-collected} moeda(s)!`);
  }
  updateUI();
}
function enemyStep(now){
  if(now<freezeUntil)return;
  const interval=Math.max(180, 920-level*7);
  if(now-lastEnemyMove<interval)return;
  lastEnemyMove=now;
  enemies.forEach((e)=>{
    const opts=neighbors(e.r,e.c).filter(([nr,nc,dr,dc])=>canMove(e,dr,dc));
    if(!opts.length)return;
    let choice;
    if(e.type===0 || level>45){
      opts.sort((a,b)=>(Math.abs(a[0]-player.r)+Math.abs(a[1]-player.c))-(Math.abs(b[0]-player.r)+Math.abs(b[1]-player.c)));
      choice=Math.random()<Math.min(.82,.35+level/180)?opts[0]:opts[Math.floor(Math.random()*opts.length)];
    } else choice=opts[Math.floor(Math.random()*opts.length)];
    e.r=choice[0];e.c=choice[1];
  });
  hitCheck();
}
function hitCheck(){
  if(performance.now()<shieldUntil)return;
  if(enemies.some(e=>e.r===player.r&&e.c===player.c)){
    lives--;vibe([60,40,90]);beep(130,.15);
    if(lives<=0){
      running=false;
      document.getElementById('resultTitle').textContent='TENTE NOVAMENTE';
      document.getElementById('resultStars').textContent='🤖💥';
      document.getElementById('resultInfo').textContent=`Você chegou ao nível ${level}.`;
      document.getElementById('nextBtn').textContent='REINICIAR';
      ui.result.classList.remove('hidden');
    } else {
      player={r:rows-1,c:Math.floor(cols/2)};showToast(`❤️ ${lives} vida(s)`);
    }
    updateUI();
  }
}
function finishLevel(){
  running=false;
  const elapsed=(performance.now()-startTime)/1000;
  let s= elapsed < 30+level*2 ? 3 : elapsed < 55+level*3 ? 2 : 1;
  score += s*100 + level*15;
  document.getElementById('resultTitle').textContent=level%10===0?'CHEFÃO SUPERADO!':'FASE CONCLUÍDA!';
  document.getElementById('resultStars').textContent='⭐'.repeat(s)+'☆'.repeat(3-s);
  document.getElementById('resultInfo').textContent=`${collected} moedas • ${elapsed.toFixed(1)}s • +${s*100+level*15} pontos`;
  document.getElementById('nextBtn').textContent='PRÓXIMA FASE';
  ui.result.classList.remove('hidden');
  save(Math.min(100,level+1));
  beep(1040,.12);
}
function save(nextLevel=level){
  localStorage.setItem('roboMazeSave',JSON.stringify({level:nextLevel,score,settings}));
}
function updateUI(){
  ui.score.textContent=score;ui.level.textContent=`NÍVEL ${level}`;ui.difficulty.textContent=difficultyName();
  ui.lives.textContent=lives;ui.coins.textContent=`${collected}/${requiredCoins}`;
  const now=performance.now();
  ui.power.textContent=now<shieldUntil?'🛡':now<freezeUntil?'❄':'—';
}
function draw(){
  ctx.clearRect(0,0,canvas.width,canvas.height);
  const g=ctx.createLinearGradient(0,0,canvas.width,canvas.height);g.addColorStop(0,'#06152d');g.addColorStop(1,'#09264e');
  ctx.fillStyle=g;ctx.fillRect(0,0,canvas.width,canvas.height);
  ctx.strokeStyle='#27d9ff';ctx.lineWidth=Math.max(5,cell*.08);ctx.lineCap='round';ctx.shadowColor='#00c8ff';ctx.shadowBlur=10;
  for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){
    const x=c*cell,y=r*cell,w=maze[r][c].w;
    ctx.beginPath();
    if(w[0]){ctx.moveTo(x,y);ctx.lineTo(x+cell,y)}
    if(w[1]){ctx.moveTo(x+cell,y);ctx.lineTo(x+cell,y+cell)}
    if(w[2]){ctx.moveTo(x,y+cell);ctx.lineTo(x+cell,y+cell)}
    if(w[3]){ctx.moveTo(x,y);ctx.lineTo(x,y+cell)}
    ctx.stroke();
  }
  ctx.shadowBlur=0;
  // dotted path
  ctx.fillStyle='#b9e9ff66';
  for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){ctx.beginPath();ctx.arc((c+.5)*cell,(r+.5)*cell,Math.max(1.7,cell*.035),0,Math.PI*2);ctx.fill()}
  // exit
  const ex=(exitCell.c+.5)*cell,ey=(exitCell.r+.5)*cell;
  ctx.fillStyle=collected>=requiredCoins?'#5cff7a':'#ff455a';ctx.beginPath();ctx.arc(ex,ey,cell*.18,0,Math.PI*2);ctx.fill();
  ctx.fillStyle='#fff';ctx.font=`${cell*.23}px system-ui`;ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText('🚪',ex,ey);
  // coins
  coins.forEach(q=>drawCoin(q.r,q.c));
  powerups.forEach(q=>drawPower(q));
  enemies.forEach((e,i)=>drawBot(e.r,e.c,['#ff3b45','#9b4dff','#ff951f','#39d85a'][e.type],false));
  drawBot(player.r,player.c,performance.now()<shieldUntil?'#8affff':'#f6fbff',true);
}
function drawCoin(r,c){
  const x=(c+.5)*cell,y=(r+.5)*cell;
  ctx.save();ctx.shadowColor='#ffd11c';ctx.shadowBlur=12;ctx.fillStyle='#ffbe13';ctx.beginPath();ctx.arc(x,y,cell*.13,0,Math.PI*2);ctx.fill();
  ctx.strokeStyle='#fff08a';ctx.lineWidth=2;ctx.stroke();ctx.fillStyle='#7b4900';ctx.font=`bold ${cell*.16}px system-ui`;ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText('★',x,y);ctx.restore();
}
function drawPower(q){
  const x=(q.c+.5)*cell,y=(q.r+.5)*cell;
  ctx.save();ctx.shadowColor=q.type==='shield'?'#51f6ff':'#b36cff';ctx.shadowBlur=16;ctx.fillStyle=q.type==='shield'?'#1ab6d4':'#7034c9';
  ctx.beginPath();ctx.arc(x,y,cell*.16,0,Math.PI*2);ctx.fill();ctx.fillStyle='#fff';ctx.font=`${cell*.2}px system-ui`;ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(q.type==='shield'?'🛡':'❄',x,y);ctx.restore();
}
function drawBot(r,c,color,hero){
  const x=(c+.5)*cell,y=(r+.5)*cell,R=cell*(hero?.25:.22);
  ctx.save();ctx.shadowColor=hero?'#5edcff':color;ctx.shadowBlur=14;
  ctx.fillStyle=color;ctx.beginPath();ctx.roundRect(x-R,y-R*.8,R*2,R*1.6,R*.45);ctx.fill();
  ctx.fillStyle='#061422';ctx.beginPath();ctx.roundRect(x-R*.72,y-R*.45,R*1.44,R*.7,R*.25);ctx.fill();
  ctx.fillStyle=hero?'#1ee8ff':'#fff';ctx.beginPath();ctx.arc(x-R*.3,y-R*.1,R*.12,0,Math.PI*2);ctx.arc(x+R*.3,y-R*.1,R*.12,0,Math.PI*2);ctx.fill();
  ctx.strokeStyle=color;ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(x,y-R*.8);ctx.lineTo(x,y-R*1.25);ctx.stroke();ctx.fillStyle='#ffd12c';ctx.beginPath();ctx.arc(x,y-R*1.32,R*.12,0,Math.PI*2);ctx.fill();
  ctx.restore();
}
function loop(now){
  if(running&&!paused){enemyStep(now);draw();updateUI()}
  requestAnimationFrame(loop);
}
function showToast(t){ui.toast.textContent=t;ui.toast.classList.add('show');clearTimeout(showToast.t);showToast.t=setTimeout(()=>ui.toast.classList.remove('show'),1300)}
let audioCtx;
function beep(freq,dur){ if(!settings.sound)return; try{audioCtx ||= new (window.AudioContext||window.webkitAudioContext)();let o=audioCtx.createOscillator(),g=audioCtx.createGain();o.frequency.value=freq;g.gain.value=.025;o.connect(g);g.connect(audioCtx.destination);o.start();o.stop(audioCtx.currentTime+dur)}catch(e){}}
function vibe(v){if(settings.vibration&&navigator.vibrate)navigator.vibrate(v)}

function showGame(){
  ui.start.classList.remove('active');ui.game.classList.add('active');initLevel();
}
document.getElementById('playBtn').onclick=()=>{level=1;score=0;showGame()};
document.getElementById('continueBtn').onclick=()=>{level=saved.level||1;score=saved.score||0;showGame()};
document.getElementById('settingsBtn').onclick=()=>ui.settings.classList.remove('hidden');
document.getElementById('closeSettingsBtn').onclick=()=>ui.settings.classList.add('hidden');
document.getElementById('pauseBtn').onclick=()=>{paused=true;ui.pause.classList.remove('hidden')};
document.getElementById('resumeBtn').onclick=()=>{paused=false;ui.pause.classList.add('hidden')};
document.getElementById('restartBtn').onclick=()=>{ui.pause.classList.add('hidden');initLevel()};
document.getElementById('homeBtn').onclick=home;
document.getElementById('resultHomeBtn').onclick=home;
document.getElementById('nextBtn').onclick=()=>{
  ui.result.classList.add('hidden');
  if(lives<=0){initLevel();return}
  level=Math.min(100,level+1);initLevel();
};
function home(){save(level);running=false;paused=false;ui.pause.classList.add('hidden');ui.result.classList.add('hidden');ui.game.classList.remove('active');ui.start.classList.add('active')}
['sound','vibration','swipe'].forEach(n=>{
  const el=document.getElementById(n+'Toggle');el.onchange=()=>{settings[n]=el.checked;save(level)}
});
document.querySelectorAll('[data-dir]').forEach(b=>b.addEventListener('pointerdown',()=>{
  const d=b.dataset.dir; movePlayer(d==='up'?-1:d==='down'?1:0,d==='left'?-1:d==='right'?1:0)
}));
window.addEventListener('keydown',e=>{
  const map={ArrowUp:[-1,0],ArrowDown:[1,0],ArrowLeft:[0,-1],ArrowRight:[0,1],w:[-1,0],s:[1,0],a:[0,-1],d:[0,1]};
  if(map[e.key]){e.preventDefault();movePlayer(...map[e.key])}
});
let touchStart=null;
canvas.addEventListener('pointerdown',e=>{touchStart=[e.clientX,e.clientY]});
canvas.addEventListener('pointerup',e=>{
  if(!settings.swipe||!touchStart)return;
  let dx=e.clientX-touchStart[0],dy=e.clientY-touchStart[1];touchStart=null;
  if(Math.max(Math.abs(dx),Math.abs(dy))<18)return;
  if(Math.abs(dx)>Math.abs(dy))movePlayer(0,dx>0?1:-1);else movePlayer(dy>0?1:-1,0);
});

if('serviceWorker' in navigator) navigator.serviceWorker.register('service-worker.js').catch(()=>{});
requestAnimationFrame(loop);
})();
