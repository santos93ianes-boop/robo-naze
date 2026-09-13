package com.robomaze.game;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {
    private RoboMazeView gameView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        gameView = new RoboMazeView(this);
        setContentView(gameView);
    }
    @Override protected void onResume(){ super.onResume(); if(gameView!=null) gameView.resumeMusic(); }
    @Override protected void onPause(){ if(gameView!=null) gameView.pauseMusic(); super.onPause(); }
}

class RoboMazeView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private Bitmap cover;
    private Bitmap approvedGameInterface;
    private MediaPlayer musicPlayer;

    private enum Screen { MENU, LEVELS, GAME, RECORDS, SETTINGS }
    private Screen screen = Screen.MENU;

    private int level = 1;
    private int unlocked = 1;
    private int bestLevel = 1;
    private int highScore = 0;
    private int score = 0;
    private int lives = 3;
    private boolean paused = false;
    private boolean soundOn = true;
    private int robotColorIndex = 0;
    private int robotModelIndex = 0;
    private int controlMode = 0; // 0 joystick, 1 swipe, 2 setas, 3 toque
    private final String[] controlNames = {"JOYSTICK", "DESLIZAR", "SETAS", "TOQUE"};
    private float touchStartX, touchStartY;
    private boolean joystickActive = false;
    private final int[] robotColors = {
            Color.rgb(0,120,220), Color.rgb(230,55,55), Color.rgb(45,185,85),
            Color.rgb(245,170,20), Color.rgb(150,75,210), Color.rgb(20,190,200),
            Color.rgb(35,35,45), Color.rgb(240,240,240)
    };
    private final String[] robotColorNames = {"AZUL","VERMELHO","VERDE","AMARELO","ROXO","CIANO","PRETO","BRANCO"};
    private final String[] robotModelNames = {"NOVA","TITAN","PULSE","SCOUT","ORBIT","NEXUS"};
    private final String[] themeNames = {"LAB NEON","RUÍNAS CYBER","ESTAÇÃO GELO","NÚCLEO VULCÃO","FÁBRICA QUÂNTICA","REATOR VOID"};

    private int rows, cols;
    private boolean[][] visited;
    private boolean[][] vWalls;
    private boolean[][] hWalls;
    private boolean[][] coins;
    private int remainingCoins;
    private int playerR, playerC;
    private int startR, startC;
    private int exitR, exitC;
    private final List<Enemy> enemies = new ArrayList<>();
    private long lastEnemyMove = 0;
    private long enemyDelayMs = 900;

    private RectF boardRect = new RectF();
    private RectF leftBtn = new RectF();
    private RectF rightBtn = new RectF();
    private RectF upBtn = new RectF();
    private RectF downBtn = new RectF();
    private RectF pauseBtn = new RectF();

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (screen == Screen.GAME && !paused) {
                long now = System.currentTimeMillis();
                if (now - lastEnemyMove >= enemyDelayMs) {
                    moveEnemies();
                    lastEnemyMove = now;
                }
                invalidate();
            }
            handler.postDelayed(this, 80);
        }
    };

    RoboMazeView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        prefs = context.getSharedPreferences("robo_maze", Context.MODE_PRIVATE);
        unlocked = prefs.getInt("unlocked", 1);
        bestLevel = prefs.getInt("bestLevel", 1);
        highScore = prefs.getInt("highScore", 0);
        int audioVersion = prefs.getInt("audioVersion", 0);
        if (audioVersion < 2) {
            soundOn = true;
            prefs.edit().putBoolean("sound", true).putInt("audioVersion", 2).apply();
        } else {
            soundOn = prefs.getBoolean("sound", true);
        }
        robotColorIndex = prefs.getInt("robotColor", 0) % robotColors.length;
        robotModelIndex = Math.max(0, Math.min(robotModelNames.length-1, prefs.getInt("robotModel", 0)));
        controlMode = Math.max(0, Math.min(3, prefs.getInt("controlMode", 0)));
        cover = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier("robo_maze_cover", "drawable", context.getPackageName()));
        approvedGameInterface = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier("game_interface_approved", "drawable", context.getPackageName()));
        musicPlayer = MediaPlayer.create(context, getResources().getIdentifier("suspense_loop", "raw", context.getPackageName()));
        if (musicPlayer != null) {
            musicPlayer.setLooping(true);
            musicPlayer.setVolume(0.68f, 0.68f);
            if (soundOn) musicPlayer.start();
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        handler.post(ticker);
    }

    void resumeMusic(){
        if(soundOn && musicPlayer!=null && !musicPlayer.isPlaying()) musicPlayer.start();
    }
    void pauseMusic(){
        if(musicPlayer!=null && musicPlayer.isPlaying()) musicPlayer.pause();
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        if (musicPlayer != null) { musicPlayer.stop(); musicPlayer.release(); musicPlayer = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.rgb(3, 25, 70));
        switch (screen) {
            case MENU: drawMenu(c); break;
            case LEVELS: drawLevels(c); break;
            case GAME: drawGame(c); break;
            case RECORDS: drawRecords(c); break;
            case SETTINGS: drawSettings(c); break;
        }
    }

    private void drawMenu(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (cover != null) {
            float size = Math.min(w * .68f, h * .35f);
            RectF dst = new RectF((w-size)/2, h*.025f, (w+size)/2, h*.025f+size);
            c.drawBitmap(cover, null, dst, p);
        }
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE); p.setTextSize(w*.078f);
        c.drawText("ROBO MAZE", w/2, h*.43f, p);
        p.setColor(Color.rgb(255,205,30)); p.setTextSize(w*.033f);
        c.drawText("DESVIE • COLETE • VENÇA!", w/2, h*.465f, p);

        drawMenuButton(c, "▶  JOGAR", h*.525f, Color.rgb(255,160,0));
        drawMenuButton(c, "▦  NÍVEIS", h*.615f, Color.rgb(0,150,225));
        drawMenuButton(c, "★  RECORDES", h*.705f, Color.rgb(0,120,215));
        drawMenuButton(c, "⚙  CONFIGURAÇÕES", h*.795f, Color.rgb(30,95,175));

        p.setTextSize(w*.031f); p.setColor(Color.WHITE);
        c.drawText("CONTROLE: " + controlNames[controlMode], w/2, h*.885f, p);
        c.drawText("ROBÔ: " + robotModelNames[robotModelIndex] + " / " + robotColorNames[robotColorIndex] + "   •   " + (soundOn?"♫ MÚSICA ON":"♫ MÚSICA OFF"), w/2, h*.935f, p);
    }

    private void drawMenuButton(Canvas c, String text, float cy, int color) {
        float w = getWidth(), h = getHeight();
        float bw = w*.72f, bh = h*.07f;
        RectF r = new RectF((w-bw)/2, cy-bh/2, (w+bw)/2, cy+bh/2);
        p.setColor(color); c.drawRoundRect(r, bh*.28f, bh*.28f, p);
        stroke.setColor(Color.argb(130,255,255,255)); stroke.setStrokeWidth(3); c.drawRoundRect(r, bh*.28f,bh*.28f,stroke);
        p.setColor(Color.WHITE); p.setTextSize(w*.047f); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText(text, w/2, cy + p.getTextSize()*.35f, p);
    }

    private void drawLevels(Canvas c) {
        float w=getWidth(), h=getHeight();
        drawHeader(c,"SELEÇÃO DE NÍVEIS");
        int columns=5;
        float gap=w*.025f, cell=(w-gap*(columns+1))/columns;
        float top=h*.18f;
        for(int i=1;i<=30;i++){
            int rr=(i-1)/columns, cc=(i-1)%columns;
            float x=gap+cc*(cell+gap), y=top+rr*(cell+gap);
            RectF r=new RectF(x,y,x+cell,y+cell);
            int color=i<unlocked?Color.rgb(55,190,70):(i==unlocked?Color.rgb(255,170,0):Color.rgb(45,62,90));
            p.setColor(color); c.drawRoundRect(r,18,18,p);
            stroke.setColor(Color.argb(120,255,255,255)); stroke.setStrokeWidth(2); c.drawRoundRect(r,18,18,stroke);
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setTextSize(w*.052f); p.setColor(Color.WHITE);
            c.drawText(i<=unlocked?String.valueOf(i):"🔒",r.centerX(),r.centerY()+p.getTextSize()*.35f,p);
        }
        p.setTextSize(w*.035f); p.setColor(Color.LTGRAY); p.setTextAlign(Paint.Align.CENTER);
        c.drawText("Conclua uma fase para liberar a próxima.",w/2,h*.95f,p);
    }

    private void drawRecords(Canvas c) {
        float w=getWidth(),h=getHeight();
        drawHeader(c,"RECORDES");
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setColor(Color.rgb(255,205,30)); p.setTextSize(w*.14f); c.drawText("★",w/2,h*.35f,p);
        p.setColor(Color.WHITE); p.setTextSize(w*.055f); c.drawText("MAIOR PONTUAÇÃO",w/2,h*.45f,p);
        p.setTextSize(w*.10f); p.setColor(Color.rgb(0,200,255)); c.drawText(String.valueOf(highScore),w/2,h*.54f,p);
        p.setTextSize(w*.05f); p.setColor(Color.WHITE); c.drawText("MELHOR NÍVEL: " + bestLevel,w/2,h*.67f,p);
        p.setTextSize(w*.038f); p.setColor(Color.LTGRAY); c.drawText("Continue jogando para superar seu recorde!",w/2,h*.76f,p);
    }

    private void drawSettings(Canvas c) {
        float w=getWidth(),h=getHeight();
        drawHeader(c,"CONFIGURAÇÕES");
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setColor(Color.rgb(255,205,30)); p.setTextSize(w*.052f); c.drawText("ESCOLHA O CONTROLE",w/2,h*.19f,p);
        String[] labels={"JOYSTICK VIRTUAL","DESLIZAR (SWIPE)","SETAS NA TELA","TOQUE NO DESTINO"};
        int[] colors={Color.rgb(0,155,210),Color.rgb(30,170,95),Color.rgb(145,70,190),Color.rgb(230,135,25)};
        for(int i=0;i<4;i++){
            float cy=h*(.27f+i*.105f); RectF r=new RectF(w*.10f,cy-h*.035f,w*.90f,cy+h*.035f);
            p.setColor(i==controlMode?colors[i]:Color.rgb(30,50,82)); c.drawRoundRect(r,18,18,p);
            stroke.setColor(i==controlMode?Color.WHITE:Color.rgb(75,105,145)); stroke.setStrokeWidth(i==controlMode?4:2); c.drawRoundRect(r,18,18,stroke);
            p.setColor(Color.WHITE); p.setTextSize(w*.039f); c.drawText((i==controlMode?"✓  ":"")+labels[i],w/2,cy+p.getTextSize()*.34f,p);
        }
        p.setColor(Color.rgb(255,205,30)); p.setTextSize(w*.036f); c.drawText("PERSONALIZAÇÃO",w/2,h*.69f,p);
        RectF model=new RectF(w*.10f,h*.715f,w*.90f,h*.775f); p.setColor(Color.rgb(25,100,175)); c.drawRoundRect(model,18,18,p);
        p.setColor(Color.WHITE); p.setTextSize(w*.032f); c.drawText("MODELO: "+robotModelNames[robotModelIndex]+"  ›",w/2,h*.755f,p);
        RectF color=new RectF(w*.10f,h*.79f,w*.90f,h*.85f); p.setColor(robotColors[robotColorIndex]); c.drawRoundRect(color,18,18,p);
        p.setColor(robotColorIndex==7?Color.DKGRAY:Color.WHITE); c.drawText("COR: "+robotColorNames[robotColorIndex]+"  ›",w/2,h*.83f,p);
        RectF music=new RectF(w*.10f,h*.865f,w*.90f,h*.925f); p.setColor(soundOn?Color.rgb(45,170,85):Color.rgb(105,115,135)); c.drawRoundRect(music,18,18,p);
        p.setColor(Color.WHITE); c.drawText(soundOn?"♫ SUSPENSE: LIGADO":"♫ SUSPENSE: DESLIGADO",w/2,h*.905f,p);
        p.setColor(Color.LTGRAY); p.setTextSize(w*.023f); c.drawText("Tudo fica salvo automaticamente.",w/2,h*.97f,p);
    }

    private void drawHeader(Canvas c,String title){
        float w=getWidth(),h=getHeight();
        p.setColor(Color.rgb(0,105,200)); c.drawRect(0,0,w,h*.12f,p);
        p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setTextSize(w*.055f);
        c.drawText(title,w/2,h*.077f,p);
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(w*.06f); c.drawText("‹",w*.055f,h*.078f,p);
    }

    private void drawWrapped(Canvas c,String text,float x,float y,float maxW,float size){
        p.setTextSize(size);
        String[] words=text.split(" "); StringBuilder line=new StringBuilder(); float yy=y;
        for(String word:words){ String test=line.length()==0?word:line+" "+word; if(p.measureText(test)>maxW){ c.drawText(line.toString(),x,yy,p); yy+=size*1.35f; line=new StringBuilder(word);} else line=new StringBuilder(test); }
        if(line.length()>0)c.drawText(line.toString(),x,yy,p);
    }

    private void startLevel(int n){
        level=Math.max(1,Math.min(30,n)); score=Math.max(0,score); lives=3; paused=false;
        int tier=(level-1)/5;
        rows=Math.min(21,9+tier*2);
        cols=Math.min(19,9+tier*2);
        if(rows%2==0) rows++; if(cols%2==0) cols++;
        generateMaze(rows,cols,level*9973L);
        playerR=0; playerC=0; startR=0; startC=0; exitR=rows-1; exitC=cols-1;
        placeCoins(); placeEnemies();
        enemyDelayMs=Math.max(250, 1050 - level*25L);
        lastEnemyMove=System.currentTimeMillis(); screen=Screen.GAME; invalidate();
    }

    private void generateMaze(int r,int co,long seed){
        visited=new boolean[r][co];
        vWalls=new boolean[r][co+1];
        hWalls=new boolean[r+1][co];
        for(int i=0;i<r;i++) for(int j=0;j<=co;j++) vWalls[i][j]=true;
        for(int i=0;i<=r;i++) for(int j=0;j<co;j++) hWalls[i][j]=true;
        Random rng=new Random(seed); carve(0,0,rng);
        int loops=Math.min(18, 2 + level/2);
        for(int k=0;k<loops;k++){
            int rr=rng.nextInt(r), cc=rng.nextInt(co);
            if(rng.nextBoolean() && cc<co-1) vWalls[rr][cc+1]=false;
            else if(rr<r-1) hWalls[rr+1][cc]=false;
        }
    }

    private void carve(int r,int c,Random rng){
        visited[r][c]=true;
        List<Integer> dirs=new ArrayList<>(); Collections.addAll(dirs,0,1,2,3); Collections.shuffle(dirs,rng);
        for(int d:dirs){
            int nr=r,nc=c; if(d==0)nr--; if(d==1)nc++; if(d==2)nr++; if(d==3)nc--;
            if(nr<0||nr>=rows||nc<0||nc>=cols||visited[nr][nc])continue;
            if(d==0) hWalls[r][c]=false;
            if(d==1) vWalls[r][c+1]=false;
            if(d==2) hWalls[r+1][c]=false;
            if(d==3) vWalls[r][c]=false;
            carve(nr,nc,rng);
        }
    }

    private void placeCoins(){
        coins=new boolean[rows][cols]; remainingCoins=0;
        int desired=Math.min(6+level/2,18);
        Random rng=new Random(level*31337L);
        while(remainingCoins<desired){
            int r=rng.nextInt(rows), c=rng.nextInt(cols);
            if((r==0&&c==0)||(r==exitR&&c==exitC)||coins[r][c])continue;
            coins[r][c]=true; remainingCoins++;
        }
    }

    private void placeEnemies(){
        enemies.clear(); int count=Math.min(5,1+(level-1)/6); Random rng=new Random(level*777L);
        while(enemies.size()<count){
            int r=rng.nextInt(rows),c=rng.nextInt(cols);
            if(Math.abs(r-playerR)+Math.abs(c-playerC)<4 || (r==exitR&&c==exitC))continue;
            boolean exists=false; for(Enemy e:enemies) if(e.r==r&&e.c==c) exists=true;
            if(!exists) enemies.add(new Enemy(r,c,enemies.size()));
        }
    }

    private int mix(int a,int b,float t){
        int ar=Color.red(a),ag=Color.green(a),ab=Color.blue(a);
        int br=Color.red(b),bg=Color.green(b),bb=Color.blue(b);
        return Color.rgb((int)(ar+(br-ar)*t),(int)(ag+(bg-ag)*t),(int)(ab+(bb-ab)*t));
    }

    private int themeIndex(){ return Math.min(5,(level-1)/5); }

    private void drawGame(Canvas c){
        float w=getWidth(), h=getHeight();
        int ti=themeIndex();
        int[] bg={Color.rgb(3,13,28),Color.rgb(18,12,20),Color.rgb(5,24,38),Color.rgb(30,8,8),Color.rgb(10,16,28),Color.rgb(4,4,14)};
        int[] floor={Color.rgb(10,35,58),Color.rgb(48,43,52),Color.rgb(18,48,62),Color.rgb(55,28,24),Color.rgb(27,39,55),Color.rgb(18,17,30)};
        int[] wall={Color.rgb(29,80,112),Color.rgb(88,82,90),Color.rgb(53,112,132),Color.rgb(112,48,38),Color.rgb(74,94,120),Color.rgb(54,48,84)};
        int[] glow={Color.CYAN,Color.rgb(255,145,40),Color.rgb(110,225,255),Color.rgb(255,74,36),Color.rgb(115,180,255),Color.rgb(180,90,255)};
        c.drawColor(bg[ti]);

        // subtle star/tech background
        p.setColor(Color.argb(38,255,255,255));
        for(int i=0;i<36;i++){ float sx=(i*97%1000)/1000f*w, sy=(i*53%700)/700f*h; c.drawCircle(sx,sy,1.2f+(i%3),p); }

        // HUD top bar
        p.setColor(Color.argb(235,5,18,35)); c.drawRoundRect(new RectF(w*.015f,h*.015f,w*.985f,h*.125f),18,18,p);
        stroke.setColor(mix(glow[ti],Color.WHITE,.25f)); stroke.setStrokeWidth(2); c.drawRoundRect(new RectF(w*.015f,h*.015f,w*.985f,h*.125f),18,18,stroke);
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.LEFT); p.setTextSize(h*.045f);
        c.drawText("ROBO MAZE",w*.035f,h*.078f,p);
        p.setTextSize(h*.032f); p.setColor(glow[ti]); c.drawText(themeNames[ti]+"  •  FASE "+level,w*.22f,h*.073f,p);
        p.setColor(Color.WHITE); c.drawText("VIDAS "+lives+"   ★ "+score+"   ◉ "+remainingCoins,w*.53f,h*.073f,p);
        pauseBtn.set(w*.91f,h*.035f,w*.965f,h*.105f); p.setColor(Color.argb(200,25,55,86)); c.drawRoundRect(pauseBtn,14,14,p); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h*.035f); p.setColor(Color.WHITE); c.drawText(paused?"▶":"Ⅱ",pauseBtn.centerX(),pauseBtn.centerY()+h*.012f,p);

        float regionL=w*.025f, regionT=h*.15f, regionR=w*.79f, regionB=h*.97f;
        float cell=Math.min((regionR-regionL)/cols,(regionB-regionT)/rows);
        float bw=cell*cols,bh=cell*rows;
        float left=regionL+(regionR-regionL-bw)/2f, top=regionT+(regionB-regionT-bh)/2f;
        boardRect.set(left,top,left+bw,top+bh);

        // board halo
        p.setShadowLayer(24,0,0,glow[ti]); p.setColor(Color.argb(90,Color.red(glow[ti]),Color.green(glow[ti]),Color.blue(glow[ti]))); c.drawRoundRect(new RectF(left-8,top-8,left+bw+8,top+bh+8),18,18,p); p.clearShadowLayer();

        // metallic floor tiles
        for(int r=0;r<rows;r++) for(int cc=0;cc<cols;cc++){
            float x=left+cc*cell,y=top+r*cell;
            int fc=mix(floor[ti],Color.WHITE,((r+cc)%2==0)?.035f:.0f);
            p.setColor(fc); c.drawRect(x,y,x+cell+1,y+cell+1,p);
            stroke.setColor(Color.argb(50,180,220,255)); stroke.setStrokeWidth(1); c.drawRect(x+1,y+1,x+cell-1,y+cell-1,stroke);
            if(((r*cols+cc+level)%11)==0){p.setColor(Color.argb(120,Color.red(glow[ti]),Color.green(glow[ti]),Color.blue(glow[ti]))); c.drawCircle(x+cell*.18f,y+cell*.18f,Math.max(1.4f,cell*.025f),p);}
        }

        // hazards/power conduits in later levels
        if(level>=7){
            for(int r=1;r<rows-1;r++) for(int cc=1;cc<cols-1;cc++) if(isHazard(r,cc)){
                float x=left+(cc+.5f)*cell,y=top+(r+.5f)*cell;
                p.setColor(Color.argb(120,255,55,45)); c.drawRect(x-cell*.34f,y-cell*.06f,x+cell*.34f,y+cell*.06f,p);
                p.setColor(Color.argb(210,255,180,40)); c.drawCircle(x,y,cell*.07f,p);
            }
        }

        // coins
        for(int r=0;r<rows;r++) for(int cc=0;cc<cols;cc++) if(coins[r][cc]){
            float x=left+(cc+.5f)*cell,y=top+(r+.5f)*cell;
            p.setShadowLayer(cell*.22f,0,0,Color.rgb(255,190,0)); p.setColor(Color.rgb(255,196,25)); c.drawCircle(x,y,cell*.15f,p); p.clearShadowLayer();
            p.setColor(Color.rgb(255,235,120)); c.drawCircle(x-cell*.03f,y-cell*.035f,cell*.07f,p);
            p.setColor(Color.rgb(180,110,0)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(cell*.15f); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); c.drawText("★",x,y+cell*.05f,p);
        }

        // exit portal
        float ex=left+(exitC+.5f)*cell, ey=top+(exitR+.5f)*cell;
        int portal= remainingCoins==0?Color.rgb(70,255,120):Color.rgb(255,65,65);
        p.setShadowLayer(cell*.38f,0,0,portal); p.setColor(Color.argb(180,Color.red(portal),Color.green(portal),Color.blue(portal))); c.drawCircle(ex,ey,cell*.28f,p); p.clearShadowLayer();
        stroke.setColor(Color.WHITE); stroke.setStrokeWidth(Math.max(2,cell*.04f)); c.drawCircle(ex,ey,cell*.18f,stroke);

        // extruded walls: shadow/depth first, then face and highlight
        float wt=Math.max(4,cell*.17f), depth=Math.max(3,cell*.07f);
        Paint wallPaint=p;
        for(int r=0;r<rows;r++) for(int cc=0;cc<=cols;cc++) if(vWalls[r][cc]){
            float x=left+cc*cell,y1=top+r*cell,y2=y1+cell;
            wallPaint.setColor(mix(wall[ti],Color.BLACK,.45f)); c.drawRect(x-wt*.55f+depth,y1+depth,x+wt*.55f+depth,y2+depth,wallPaint);
            wallPaint.setColor(wall[ti]); c.drawRoundRect(new RectF(x-wt*.55f,y1,x+wt*.55f,y2),wt*.22f,wt*.22f,wallPaint);
            wallPaint.setColor(mix(wall[ti],Color.WHITE,.32f)); c.drawRect(x-wt*.45f,y1+1,x-wt*.18f,y2-1,wallPaint);
        }
        for(int r=0;r<=rows;r++) for(int cc=0;cc<cols;cc++) if(hWalls[r][cc]){
            float y=top+r*cell,x1=left+cc*cell,x2=x1+cell;
            wallPaint.setColor(mix(wall[ti],Color.BLACK,.45f)); c.drawRect(x1+depth,y-wt*.55f+depth,x2+depth,y+wt*.55f+depth,wallPaint);
            wallPaint.setColor(wall[ti]); c.drawRoundRect(new RectF(x1,y-wt*.55f,x2,y+wt*.55f),wt*.22f,wt*.22f,wallPaint);
            wallPaint.setColor(mix(wall[ti],Color.WHITE,.30f)); c.drawRect(x1+1,y-wt*.43f,x2-1,y-wt*.17f,wallPaint);
        }

        for(Enemy e:enemies){ float x=left+(e.c+.5f)*cell,y=top+(e.r+.5f)*cell; drawEnemy(c,x,y,cell,e.kind); }
        float px=left+(playerC+.5f)*cell,py=top+(playerR+.5f)*cell; drawRobot(c,px,py,cell);

        // right control/status panel
        float pl=w*.815f, pr=w*.985f;
        p.setColor(Color.argb(220,6,19,36)); c.drawRoundRect(new RectF(pl,h*.15f,pr,h*.97f),22,22,p);
        stroke.setColor(Color.argb(130,Color.red(glow[ti]),Color.green(glow[ti]),Color.blue(glow[ti]))); stroke.setStrokeWidth(2); c.drawRoundRect(new RectF(pl,h*.15f,pr,h*.97f),22,22,stroke);
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setColor(glow[ti]); p.setTextSize(h*.034f); c.drawText("MISSÃO",(pl+pr)/2,h*.205f,p);
        p.setColor(Color.WHITE); p.setTextSize(h*.025f); c.drawText(remainingCoins>0?"COLETE TODAS":"PORTAL LIBERADO",(pl+pr)/2,h*.245f,p);
        c.drawText("CONTROLE",(pl+pr)/2,h*.34f,p); p.setColor(glow[ti]); c.drawText(controlNames[controlMode],(pl+pr)/2,h*.375f,p);
        c.drawText("ROBÔ",(pl+pr)/2,h*.47f,p); p.setColor(Color.WHITE); c.drawText(robotModelNames[robotModelIndex],(pl+pr)/2,h*.505f,p);

        float cx=(pl+pr)/2, cy=h*.76f, rad=Math.min(pr-pl,h*.20f)*.28f;
        if(controlMode==0){
            p.setColor(Color.argb(180,18,55,88)); c.drawCircle(cx,cy,rad*1.6f,p); stroke.setColor(glow[ti]); stroke.setStrokeWidth(3); c.drawCircle(cx,cy,rad*1.6f,stroke); p.setColor(Color.argb(230,40,110,165)); c.drawCircle(cx,cy,rad*.75f,p);
        }else if(controlMode==2){
            float s=rad*1.1f; leftBtn.set(cx-s*2.1f,cy-s*.55f,cx-s*.9f,cy+s*.55f); rightBtn.set(cx+s*.9f,cy-s*.55f,cx+s*2.1f,cy+s*.55f); upBtn.set(cx-s*.55f,cy-s*2.1f,cx+s*.55f,cy-s*.9f); downBtn.set(cx-s*.55f,cy+s*.9f,cx+s*.55f,cy+s*2.1f); drawControl(c,leftBtn,0);drawControl(c,rightBtn,1);drawControl(c,upBtn,2);drawControl(c,downBtn,3);
        }else{
            p.setColor(Color.argb(150,30,75,110)); c.drawRoundRect(new RectF(pl+12,h*.66f,pr-12,h*.87f),18,18,p); p.setColor(Color.WHITE); p.setTextSize(h*.021f); c.drawText(controlMode==1?"DESLIZE PARA MOVER":"TOQUE NO CAMINHO",cx,h*.775f,p);
        }

        if(paused){
            p.setColor(Color.argb(190,0,5,15)); c.drawRect(0,0,w,h,p); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE); p.setTextSize(h*.10f); c.drawText("PAUSADO",w/2,h*.50f,p); p.setTextSize(h*.035f); c.drawText("Toque em  ▶  para continuar",w/2,h*.58f,p);
        }
    }

    private boolean isHazard(int r,int c){
        if(level<7 || (r==playerR&&c==playerC) || (r==exitR&&c==exitC)) return false;
        return ((r*37+c*19+level*13)%41)==0;
    }

    private void drawRobot(Canvas c,float x,float y,float cell){
        int body=robotColors[robotColorIndex]; float s=cell*.34f;
        p.setShadowLayer(cell*.18f,0,cell*.06f,Color.BLACK); p.setColor(Color.argb(150,0,0,0)); c.drawOval(new RectF(x-s*.9f,y+s*.48f,x+s*.9f,y+s*.92f),p); p.clearShadowLayer();
        switch(robotModelIndex){
            case 1: // TITAN
                p.setColor(body); c.drawRoundRect(new RectF(x-s*.78f,y-s*.65f,x+s*.78f,y+s*.62f),s*.22f,s*.22f,p);
                p.setColor(Color.WHITE); c.drawRoundRect(new RectF(x-s*.58f,y-s*.48f,x+s*.58f,y+s*.05f),s*.18f,s*.18f,p); break;
            case 2: // PULSE
                p.setColor(body); c.drawCircle(x,y,s*.88f,p); p.setColor(Color.WHITE); c.drawCircle(x,y,s*.63f,p); break;
            case 3: // SCOUT
                p.setColor(body); Path tri=new Path(); tri.moveTo(x,y-s);tri.lineTo(x-s*.88f,y+s*.62f);tri.lineTo(x+s*.88f,y+s*.62f);tri.close();c.drawPath(tri,p); p.setColor(Color.WHITE); c.drawCircle(x,y-s*.10f,s*.50f,p); break;
            case 4: // ORBIT
                p.setColor(body); c.drawCircle(x,y,s*.67f,p); stroke.setColor(mix(body,Color.WHITE,.45f)); stroke.setStrokeWidth(s*.18f); c.drawCircle(x,y,s*.96f,stroke); p.setColor(Color.WHITE); c.drawCircle(x,y,s*.46f,p); break;
            case 5: // NEXUS
                p.setColor(body); c.drawRoundRect(new RectF(x-s*.62f,y-s*.72f,x+s*.62f,y+s*.72f),s*.30f,s*.30f,p); p.setColor(Color.WHITE); c.drawCircle(x,y-s*.08f,s*.48f,p); break;
            default: // NOVA
                p.setColor(body); c.drawCircle(x,y,s*.82f,p); p.setColor(Color.WHITE); c.drawCircle(x,y,s*.61f,p); break;
        }
        p.setColor(Color.rgb(10,27,48)); c.drawRoundRect(new RectF(x-s*.44f,y-s*.27f,x+s*.44f,y+s*.12f),s*.15f,s*.15f,p);
        p.setShadowLayer(s*.22f,0,0,Color.CYAN); p.setColor(Color.CYAN); c.drawCircle(x-s*.18f,y-s*.07f,s*.075f,p); c.drawCircle(x+s*.18f,y-s*.07f,s*.075f,p); p.clearShadowLayer();
        p.setColor(body); c.drawRect(x-s*.05f,y-s*.90f,x+s*.05f,y-s*.68f,p); c.drawCircle(x,y-s*.98f,s*.10f,p);
    }

    private void drawEnemy(Canvas c,float x,float y,float cell,int kind){
        int[] ec={Color.rgb(245,64,64),Color.rgb(180,70,235),Color.rgb(30,205,235),Color.rgb(255,145,25),Color.rgb(90,220,95)}; int co=ec[kind%ec.length]; float s=cell*.31f;
        p.setShadowLayer(cell*.18f,0,0,co); p.setColor(co); c.drawRoundRect(new RectF(x-s*.72f,y-s*.58f,x+s*.72f,y+s*.58f),s*.22f,s*.22f,p); p.clearShadowLayer();
        p.setColor(Color.rgb(30,18,25)); c.drawRoundRect(new RectF(x-s*.50f,y-s*.30f,x+s*.50f,y+s*.05f),s*.12f,s*.12f,p);
        p.setColor(Color.WHITE); c.drawCircle(x-s*.19f,y-s*.12f,s*.09f,p); c.drawCircle(x+s*.19f,y-s*.12f,s*.09f,p); p.setColor(Color.RED); c.drawCircle(x-s*.19f,y-s*.12f,s*.045f,p); c.drawCircle(x+s*.19f,y-s*.12f,s*.045f,p);
        stroke.setColor(co); stroke.setStrokeWidth(Math.max(2,s*.12f)); c.drawLine(x-s*.6f,y+s*.48f,x-s*.95f,y+s*.83f,stroke); c.drawLine(x+s*.6f,y+s*.48f,x+s*.95f,y+s*.83f,stroke);
    }

    private void drawControl(Canvas c,RectF r,int dir){
        p.setColor(Color.rgb(10,60,160)); c.drawOval(r,p); stroke.setColor(Color.rgb(0,130,230)); stroke.setStrokeWidth(3); c.drawOval(r,stroke);
        float cx=r.centerX(),cy=r.centerY(),s=r.width()*.25f; Path path=new Path();
        if(dir==0){path.moveTo(cx-s,cy);path.lineTo(cx+s,cy-s);path.lineTo(cx+s,cy+s);} if(dir==1){path.moveTo(cx+s,cy);path.lineTo(cx-s,cy-s);path.lineTo(cx-s,cy+s);} if(dir==2){path.moveTo(cx,cy-s);path.lineTo(cx-s,cy+s);path.lineTo(cx+s,cy+s);} if(dir==3){path.moveTo(cx,cy+s);path.lineTo(cx-s,cy-s);path.lineTo(cx+s,cy-s);} path.close(); p.setColor(Color.WHITE); c.drawPath(path,p);
    }

    private boolean canMove(int r,int c,int nr,int nc){
        if(nr<0||nr>=rows||nc<0||nc>=cols)return false;
        if(nr==r-1)return !hWalls[r][c]; if(nr==r+1)return !hWalls[r+1][c]; if(nc==c-1)return !vWalls[r][c]; if(nc==c+1)return !vWalls[r][c+1]; return false;
    }
    private void movePlayer(int dr,int dc){
        if(paused)return; int nr=playerR+dr,nc=playerC+dc; if(!canMove(playerR,playerC,nr,nc))return;
        playerR=nr;playerC=nc; score+=5;
        if(isHazard(playerR,playerC)){ lives--; score=Math.max(0,score-75); playerR=startR; playerC=startC; if(lives<=0){ saveRecords(); screen=Screen.MENU; } invalidate(); return; }
        if(coins[playerR][playerC]){coins[playerR][playerC]=false;remainingCoins--;score+=100;}
        checkCollision();
        if(playerR==exitR&&playerC==exitC&&remainingCoins==0) completeLevel();
        invalidate();
    }
    private void moveEnemies(){
        for(Enemy e:enemies){
            List<int[]> moves=new ArrayList<>(); int[][] ds={{-1,0},{1,0},{0,-1},{0,1}};
            for(int[] d:ds){int nr=e.r+d[0],nc=e.c+d[1];if(canMove(e.r,e.c,nr,nc))moves.add(new int[]{nr,nc});}
            if(moves.isEmpty())continue;
            int[] best=moves.get(random.nextInt(moves.size()));
            if(level>=8 || random.nextFloat()<0.55f){ int bd=Math.abs(best[0]-playerR)+Math.abs(best[1]-playerC); for(int[] m:moves){int d=Math.abs(m[0]-playerR)+Math.abs(m[1]-playerC); if(d<bd){best=m;bd=d;}} }
            e.r=best[0];e.c=best[1];
        }
        checkCollision(); invalidate();
    }
    private void checkCollision(){
        for(Enemy e:enemies) if(e.r==playerR&&e.c==playerC){ lives--; score=Math.max(0,score-100); playerR=startR;playerC=startC; if(lives<=0){ saveRecords(); screen=Screen.MENU; } return; }
    }
    private void completeLevel(){
        score+=500+level*50; bestLevel=Math.max(bestLevel,level); highScore=Math.max(highScore,score); if(level<30)unlocked=Math.max(unlocked,level+1); saveRecords();
        if(level<30) startLevel(level+1); else screen=Screen.RECORDS;
    }
    private void saveRecords(){ prefs.edit().putInt("unlocked",unlocked).putInt("bestLevel",bestLevel).putInt("highScore",highScore).putBoolean("sound",soundOn).putInt("robotColor",robotColorIndex).putInt("robotModel",robotModelIndex).putInt("controlMode",controlMode).apply(); }

    @Override public boolean onTouchEvent(MotionEvent event){
        float x=event.getX(),y=event.getY(); float h=getHeight(),w=getWidth();
        if(screen==Screen.MENU && event.getAction()==MotionEvent.ACTION_UP){
            if(y>h*.485f&&y<h*.565f){score=0;startLevel(Math.min(unlocked,30));}
            else if(y>h*.575f&&y<h*.655f){screen=Screen.LEVELS;invalidate();}
            else if(y>h*.665f&&y<h*.745f){screen=Screen.RECORDS;invalidate();}
            else if(y>h*.755f&&y<h*.835f){screen=Screen.SETTINGS;invalidate();}
            return true;
        }
        if((screen==Screen.LEVELS||screen==Screen.RECORDS||screen==Screen.SETTINGS)&&event.getAction()==MotionEvent.ACTION_UP&&y<h*.12f&&x<w*.20f){screen=Screen.MENU;invalidate();return true;}
        if(screen==Screen.SETTINGS && event.getAction()==MotionEvent.ACTION_UP){
            for(int i=0;i<4;i++){ float cy=h*(.27f+i*.105f); if(y>cy-h*.045f&&y<cy+h*.045f){controlMode=i;saveRecords();invalidate();return true;} }
            if(y>h*.70f&&y<h*.785f){robotModelIndex=(robotModelIndex+1)%robotModelNames.length;saveRecords();invalidate();return true;}
            if(y>h*.785f&&y<h*.86f){robotColorIndex=(robotColorIndex+1)%robotColors.length;saveRecords();invalidate();return true;}
            if(y>h*.855f&&y<h*.94f){soundOn=!soundOn;if(musicPlayer!=null){if(soundOn){if(!musicPlayer.isPlaying())musicPlayer.start();}else if(musicPlayer.isPlaying())musicPlayer.pause();}saveRecords();invalidate();return true;}
        }
        if(screen==Screen.LEVELS && event.getAction()==MotionEvent.ACTION_UP){
            int columns=5; float gap=w*.025f, cell=(w-gap*(columns+1))/columns, top=h*.18f;
            for(int i=1;i<=30;i++){int rr=(i-1)/columns,cc=(i-1)%columns;float bx=gap+cc*(cell+gap),by=top+rr*(cell+gap);if(x>=bx&&x<=bx+cell&&y>=by&&y<=by+cell&&i<=unlocked){score=0;startLevel(i);return true;}}
        }
        if(screen!=Screen.GAME)return true;
        if(event.getAction()==MotionEvent.ACTION_DOWN){
            if(pauseBtn.contains(x,y)){paused=!paused;invalidate();return true;}
            if(paused)return true;
            touchStartX=x; touchStartY=y; joystickActive=(controlMode==0 && x>w*.80f && y>h*.58f); return true;
        }
        if(paused)return true;
        if(controlMode==0 && joystickActive && event.getAction()==MotionEvent.ACTION_MOVE){
            float dx=x-touchStartX,dy=y-touchStartY; float threshold=Math.min(w,h)*.045f;
            if(Math.abs(dx)>threshold||Math.abs(dy)>threshold){ if(Math.abs(dx)>Math.abs(dy))movePlayer(0,dx>0?1:-1);else movePlayer(dy>0?1:-1,0); touchStartX=x;touchStartY=y; } return true;
        }
        if(event.getAction()==MotionEvent.ACTION_UP){
            if(controlMode==0){ joystickActive=false; float dx=x-touchStartX,dy=y-touchStartY; if(Math.abs(dx)>8||Math.abs(dy)>8){if(Math.abs(dx)>Math.abs(dy))movePlayer(0,dx>0?1:-1);else movePlayer(dy>0?1:-1,0);} }
            else if(controlMode==1){ float dx=x-touchStartX,dy=y-touchStartY; float threshold=Math.min(w,h)*.04f; if(Math.abs(dx)>threshold||Math.abs(dy)>threshold){if(Math.abs(dx)>Math.abs(dy))movePlayer(0,dx>0?1:-1);else movePlayer(dy>0?1:-1,0);} }
            else if(controlMode==2){ if(leftBtn.contains(x,y))movePlayer(0,-1); else if(rightBtn.contains(x,y))movePlayer(0,1); else if(upBtn.contains(x,y))movePlayer(-1,0); else if(downBtn.contains(x,y))movePlayer(1,0); }
            else if(controlMode==3 && boardRect.contains(x,y)){
                float cell=boardRect.width()/cols; int tc=Math.max(0,Math.min(cols-1,(int)((x-boardRect.left)/cell))); int tr=Math.max(0,Math.min(rows-1,(int)((y-boardRect.top)/cell)));
                int dr=tr-playerR,dc=tc-playerC; if(Math.abs(dc)>=Math.abs(dr)&&dc!=0)movePlayer(0,dc>0?1:-1); else if(dr!=0)movePlayer(dr>0?1:-1,0);
            }
        }
        return true;
    }

    private static class Enemy { int r,c,kind; Enemy(int r,int c,int kind){this.r=r;this.c=c;this.kind=kind;} }
}
