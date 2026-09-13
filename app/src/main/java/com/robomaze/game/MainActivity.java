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
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.media.AudioManager;
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
    private ToneGenerator fxTone;

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
        fxTone = new ToneGenerator(AudioManager.STREAM_MUSIC, 72);
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
        if (fxTone != null) { fxTone.release(); fxTone = null; }
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
        float w=getWidth(), h=getHeight();
        int navy=Color.rgb(3,13,30), cyan=Color.rgb(0,220,255), gold=Color.rgb(255,190,32);
        c.drawColor(navy);
        // cinematic background grid
        p.setColor(Color.argb(28,0,220,255));
        for(int i=0;i<18;i++) c.drawLine(0,h*(.12f+i*.055f),w,h*(.12f+i*.055f),p);
        for(int i=0;i<28;i++) c.drawLine(w*(i*.04f),0,w*(i*.04f),h,p);
        // subtle approved art as atmosphere
        if(approvedGameInterface!=null){ p.setAlpha(40); c.drawBitmap(approvedGameInterface,null,new RectF(0,0,w,h),p); p.setAlpha(255); }
        // left hero card
        RectF hero=new RectF(w*.035f,h*.07f,w*.43f,h*.93f);
        p.setColor(Color.argb(235,4,20,42)); c.drawRoundRect(hero,30,30,p);
        stroke.setColor(Color.argb(170,0,220,255)); stroke.setStrokeWidth(2.5f); c.drawRoundRect(hero,30,30,stroke);
        if(cover!=null){ float size=Math.min(hero.width()*.78f,hero.height()*.50f); RectF dst=new RectF(hero.centerX()-size/2,hero.top+h*.045f,hero.centerX()+size/2,hero.top+h*.045f+size); c.drawBitmap(cover,null,dst,p); }
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(Color.WHITE); p.setTextSize(h*.070f); c.drawText("ROBO MAZE",hero.centerX(),hero.top+hero.height()*.66f,p);
        p.setColor(gold); p.setTextSize(h*.032f); c.drawText("DESVIE • COLETE • VENÇA!",hero.centerX(),hero.top+hero.height()*.72f,p);
        p.setColor(Color.rgb(155,195,220)); p.setTextSize(h*.022f); c.drawText("6 MUNDOS  •  30 FASES  •  6 ROBÔS",hero.centerX(),hero.top+hero.height()*.79f,p);
        // mini badges
        drawPill(c,"DIFICULDADE PROGRESSIVA",hero.left+hero.width()*.08f,hero.top+hero.height()*.84f,hero.width()*.84f,h*.055f,Color.rgb(20,70,105));
        // right menu panel
        RectF panel=new RectF(w*.47f,h*.07f,w*.965f,h*.93f);
        p.setColor(Color.argb(242,5,18,38)); c.drawRoundRect(panel,30,30,p);
        stroke.setColor(Color.argb(120,0,220,255)); stroke.setStrokeWidth(2); c.drawRoundRect(panel,30,30,stroke);
        p.setTextAlign(Paint.Align.LEFT); p.setColor(Color.WHITE); p.setTextSize(h*.036f); c.drawText("CENTRAL DE MISSÃO",panel.left+w*.025f,panel.top+h*.065f,p);
        p.setColor(Color.rgb(100,235,255)); p.setTextSize(h*.021f); c.drawText("Prepare seu robô e entre no labirinto",panel.left+w*.025f,panel.top+h*.102f,p);
        float bx=panel.left+w*.025f, bw=panel.width()-w*.05f, bh=h*.105f, gap=h*.024f, y=panel.top+h*.155f;
        drawPremiumButton(c,"▶  JOGAR AGORA","Continuar da fase " + Math.min(unlocked,30),bx,y,bw,bh,Color.rgb(0,155,220)); y+=bh+gap;
        drawPremiumButton(c,"▦  MAPA DE FASES","30 missões em 6 mundos",bx,y,bw,bh,Color.rgb(25,105,210)); y+=bh+gap;
        drawPremiumButton(c,"★  RECORDES","Pontuação e progresso",bx,y,bw,bh,Color.rgb(80,70,190)); y+=bh+gap;
        drawPremiumButton(c,"⚙  HANGAR & CONTROLES","Robôs, cores, áudio e comando",bx,y,bw,bh,Color.rgb(30,90,135));
        float bottom=panel.bottom-h*.055f;
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(h*.021f); p.setColor(Color.rgb(175,205,225));
        c.drawText("CONTROLE: "+controlNames[controlMode]+"   •   ROBÔ: "+robotModelNames[robotModelIndex]+"   •   "+(soundOn?"MÚSICA ON":"MÚSICA OFF"),panel.centerX(),bottom,p);
    }

    private void drawPill(Canvas c,String text,float x,float y,float ww,float hh,int color){
        p.setColor(color); c.drawRoundRect(new RectF(x,y,x+ww,y+hh),hh/2,hh/2,p);
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(hh*.38f); p.setColor(Color.WHITE); c.drawText(text,x+ww/2,y+hh*.64f,p);
    }

    private void drawPremiumButton(Canvas c,String title,String sub,float x,float y,float ww,float hh,int color){
        LinearGradient g=new LinearGradient(x,y,x+ww,y,color,mix(color,Color.BLACK,.24f),Shader.TileMode.CLAMP); p.setShader(g); c.drawRoundRect(new RectF(x,y,x+ww,y+hh),20,20,p); p.setShader(null);
        stroke.setColor(Color.argb(100,255,255,255)); stroke.setStrokeWidth(2); c.drawRoundRect(new RectF(x,y,x+ww,y+hh),20,20,stroke);
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE); p.setTextSize(hh*.33f); c.drawText(title,x+ww*.05f,y+hh*.43f,p);
        p.setTypeface(Typeface.DEFAULT); p.setColor(Color.argb(220,225,245,255)); p.setTextSize(hh*.20f); c.drawText(sub,x+ww*.05f,y+hh*.75f,p);
        p.setTextAlign(Paint.Align.RIGHT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(hh*.40f); p.setColor(Color.WHITE); c.drawText("›",x+ww*.95f,y+hh*.58f,p);
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
        float w=getWidth(),h=getHeight();
        c.drawColor(Color.rgb(3,15,34)); drawHeader(c,"MAPA DE FASES");
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT_BOLD); p.setColor(Color.rgb(100,225,255)); p.setTextSize(h*.028f);
        c.drawText("6 MUNDOS • dificuldade crescente",w*.06f,h*.165f,p);
        int columns=10; float gap=w*.012f, left=w*.055f, right=w*.945f; float cell=(right-left-gap*(columns-1))/columns; float top=h*.22f;
        for(int i=1;i<=30;i++){
            int rr=(i-1)/columns,cc=(i-1)%columns; float x=left+cc*(cell+gap), y=top+rr*(cell+gap+h*.030f);
            RectF r=new RectF(x,y,x+cell,y+cell*.78f); int tier=(i-1)/5;
            int[] tc={Color.rgb(0,165,220),Color.rgb(210,120,30),Color.rgb(65,170,215),Color.rgb(220,75,45),Color.rgb(70,115,210),Color.rgb(145,75,210)};
            int col=i<=unlocked?tc[tier]:Color.rgb(30,45,68); p.setColor(col); c.drawRoundRect(r,14,14,p);
            stroke.setColor(i==unlocked?Color.WHITE:Color.argb(80,255,255,255)); stroke.setStrokeWidth(i==unlocked?3:1.5f); c.drawRoundRect(r,14,14,stroke);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.WHITE);p.setTextSize(cell*.29f);c.drawText(i<=unlocked?String.valueOf(i):"×",r.centerX(),r.centerY()+cell*.09f,p);
            if(i==unlocked){p.setTextSize(cell*.12f);p.setColor(Color.rgb(255,225,90));c.drawText("ATUAL",r.centerX(),r.bottom+cell*.17f,p);}
        }
        String[] worlds={"LAB NEON","RUÍNAS CYBER","ESTAÇÃO GELO","NÚCLEO VULCÃO","FÁBRICA QUÂNTICA","REATOR VOID"};
        p.setTextAlign(Paint.Align.CENTER);p.setTextSize(h*.020f);p.setColor(Color.rgb(160,190,215));
        for(int i=0;i<6;i++) c.drawText((i+1)+". "+worlds[i],w*(.12f+i*.152f),h*.86f,p);
        p.setTextSize(h*.021f);p.setColor(Color.WHITE);c.drawText("Complete uma fase para liberar a próxima.",w/2,h*.94f,p);
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
        float w=getWidth(),h=getHeight(); c.drawColor(Color.rgb(3,15,34)); drawHeader(c,"HANGAR & CONTROLES");
        float left=w*.055f, mid=w*.50f, right=w*.945f, top=h*.16f, bottom=h*.94f;
        // Controls card
        RectF a=new RectF(left,top,mid-w*.018f,bottom); p.setColor(Color.rgb(6,26,52)); c.drawRoundRect(a,24,24,p); stroke.setColor(Color.argb(100,0,220,255));stroke.setStrokeWidth(2);c.drawRoundRect(a,24,24,stroke);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.rgb(100,230,255));p.setTextSize(h*.032f);c.drawText("CONTROLE",a.left+w*.025f,a.top+h*.055f,p);
        String[] labels={"JOYSTICK VIRTUAL","DESLIZAR (SWIPE)","SETAS NA TELA","TOQUE NO DESTINO"};
        String[] subs={"Preciso e confortável","Rápido e intuitivo","Clássico e fácil","Toque para indicar direção"};
        for(int i=0;i<4;i++){
            float cy=a.top+h*(.13f+i*.13f);RectF r=new RectF(a.left+w*.02f,cy,a.right-w*.02f,cy+h*.095f);
            p.setColor(i==controlMode?Color.rgb(0,145,205):Color.rgb(20,48,78));c.drawRoundRect(r,16,16,p); stroke.setColor(i==controlMode?Color.WHITE:Color.rgb(45,80,115));stroke.setStrokeWidth(i==controlMode?2.5f:1.3f);c.drawRoundRect(r,16,16,stroke);
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.WHITE);p.setTextSize(h*.025f);c.drawText((i==controlMode?"✓  ":"○  ")+labels[i],r.left+w*.015f,r.top+h*.039f,p);
            p.setTypeface(Typeface.DEFAULT);p.setColor(Color.rgb(175,205,225));p.setTextSize(h*.018f);c.drawText(subs[i],r.left+w*.045f,r.top+h*.071f,p);
        }
        // Hangar card
        RectF b=new RectF(mid+w*.018f,top,right,bottom);p.setColor(Color.rgb(6,26,52));c.drawRoundRect(b,24,24,p);stroke.setColor(Color.argb(100,255,190,35));stroke.setStrokeWidth(2);c.drawRoundRect(b,24,24,stroke);
        p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.rgb(255,205,60));p.setTextSize(h*.032f);c.drawText("ROBÔ",b.left+w*.025f,b.top+h*.055f,p);
        // robot preview
        float px=b.left+b.width()*.22f, py=b.top+h*.22f; drawRobot(c,px,py,h*.22f);
        p.setTextAlign(Paint.Align.LEFT);p.setColor(Color.WHITE);p.setTextSize(h*.033f);c.drawText(robotModelNames[robotModelIndex],b.left+b.width()*.42f,b.top+h*.18f,p);
        p.setTypeface(Typeface.DEFAULT);p.setColor(Color.rgb(170,205,225));p.setTextSize(h*.019f);c.drawText("Toque em MODELO para trocar",b.left+b.width()*.42f,b.top+h*.22f,p);
        RectF model=new RectF(b.left+w*.025f,b.top+h*.31f,b.right-w*.025f,b.top+h*.39f);p.setColor(Color.rgb(25,95,165));c.drawRoundRect(model,16,16,p);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.WHITE);p.setTextSize(h*.024f);c.drawText("MODELO  ‹  "+robotModelNames[robotModelIndex]+"  ›",model.centerX(),model.centerY()+h*.009f,p);
        // color chips
        p.setTextAlign(Paint.Align.LEFT);p.setColor(Color.rgb(255,205,60));p.setTextSize(h*.022f);c.drawText("COR",b.left+w*.025f,b.top+h*.455f,p);
        float chipY=b.top+h*.50f, chipR=h*.025f; for(int i=0;i<robotColors.length;i++){float xx=b.left+w*.045f+i*(b.width()-w*.09f)/(robotColors.length-1);p.setColor(robotColors[i]);c.drawCircle(xx,chipY,chipR,p);if(i==robotColorIndex){stroke.setColor(Color.WHITE);stroke.setStrokeWidth(4);c.drawCircle(xx,chipY,chipR+5,stroke);}}
        RectF music=new RectF(b.left+w*.025f,b.top+h*.585f,b.right-w*.025f,b.top+h*.675f);p.setColor(soundOn?Color.rgb(30,155,85):Color.rgb(75,82,95));c.drawRoundRect(music,16,16,p);p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(h*.025f);c.drawText(soundOn?"♫  MÚSICA DE SUSPENSE: LIGADA":"♫  MÚSICA: DESLIGADA",music.centerX(),music.centerY()+h*.009f,p);
        p.setTextSize(h*.018f);p.setColor(Color.rgb(160,190,210));c.drawText("Todas as escolhas ficam salvas automaticamente.",b.centerX(),b.bottom-h*.035f,p);
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
        // wide labyrinths designed for landscape phones
        rows=Math.min(17,9+tier*2);
        cols=Math.min(23,15+tier*2);
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
        int loops=Math.min(26, 4 + level);
        carveRooms(rng);
        for(int k=0;k<loops;k++){
            int rr=rng.nextInt(r), cc=rng.nextInt(co);
            if(rng.nextBoolean() && cc<co-1) vWalls[rr][cc+1]=false;
            else if(rr<r-1) hWalls[rr+1][cc]=false;
        }
    }

    private void carveRooms(Random rng){
        int rooms = Math.min(5, 1 + level/6);
        for(int n=0;n<rooms;n++){
            int rh = 2 + rng.nextInt(level>14?3:2);
            int rw = 2 + rng.nextInt(level>9?4:3);
            int rr = 1 + rng.nextInt(Math.max(1, rows-rh-2));
            int cc = 1 + rng.nextInt(Math.max(1, cols-rw-2));
            for(int r=rr;r<rr+rh;r++) for(int c=cc;c<cc+rw;c++){
                if(c<cc+rw-1) vWalls[r][c+1]=false;
                if(r<rr+rh-1) hWalls[r+1][c]=false;
            }
            // punch 2-3 doorways so rooms feel intentional, not sealed boxes
            if(cc>0) vWalls[rr+rng.nextInt(rh)][cc]=false;
            if(cc+rw<cols) vWalls[rr+rng.nextInt(rh)][cc+rw]=false;
            if(rr>0 && rng.nextBoolean()) hWalls[rr][cc+rng.nextInt(rw)]=false;
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

        // premium modular floor: beveled plates, seams, vents and theme details
        for(int r=0;r<rows;r++) for(int cc=0;cc<cols;cc++){
            float x=left+cc*cell,y=top+r*cell;
            drawFloorTile(c,x,y,cell,floor[ti],glow[ti],ti,r,cc);
        }
        drawWorldDecor(c,left,top,cell,ti,glow[ti]);

        // hazards/power conduits in later levels
        if(level>=7){
            for(int r=1;r<rows-1;r++) for(int cc=1;cc<cols-1;cc++) if(isHazard(r,cc)){
                float x=left+(cc+.5f)*cell,y=top+(r+.5f)*cell;
                p.setColor(Color.argb(120,255,55,45)); c.drawRect(x-cell*.34f,y-cell*.06f,x+cell*.34f,y+cell*.06f,p);
                p.setColor(Color.argb(210,255,180,40)); c.drawCircle(x,y,cell*.07f,p);
            }
        }

        // coins with animated energy halo
        for(int r=0;r<rows;r++) for(int cc=0;cc<cols;cc++) if(coins[r][cc]){
            float x=left+(cc+.5f)*cell,y=top+(r+.5f)*cell; drawCoin(c,x,y,cell);
        }

        // animated exit portal
        float ex=left+(exitC+.5f)*cell, ey=top+(exitR+.5f)*cell;
        int portal= remainingCoins==0?Color.rgb(70,255,120):Color.rgb(255,65,65);
        drawPortal(c,ex,ey,cell,portal,remainingCoins==0);

        // modular 3D walls: depth, bevel, bolts, neon strips and panel seams
        float wt=Math.max(5,cell*.19f);
        for(int r=0;r<rows;r++) for(int cc=0;cc<=cols;cc++) if(vWalls[r][cc]){
            float x=left+cc*cell,y1=top+r*cell,y2=y1+cell; drawWallV(c,x,y1,y2,wt,wall[ti],glow[ti],ti,r,cc);
        }
        for(int r=0;r<=rows;r++) for(int cc=0;cc<cols;cc++) if(hWalls[r][cc]){
            float y=top+r*cell,x1=left+cc*cell,x2=x1+cell; drawWallH(c,x1,x2,y,wt,wall[ti],glow[ti],ti,r,cc);
        }

        // enemy scanner cones under characters on advanced phases
        if(level>=6){ for(Enemy e:enemies){ float x=left+(e.c+.5f)*cell,y=top+(e.r+.5f)*cell; drawScannerCone(c,x,y,cell,e.kind); } }
        for(Enemy e:enemies){ float x=left+(e.c+.5f)*cell,y=top+(e.r+.5f)*cell; drawEnemy(c,x,y,cell,e.kind); }
        float px=left+(playerC+.5f)*cell,py=top+(playerR+.5f)*cell; drawRobot(c,px,py,cell);

        // right tactical panel: minimap, mission and selected control
        float pl=w*.815f, pr=w*.985f, pc=(pl+pr)/2f;
        p.setColor(Color.argb(232,5,18,36)); c.drawRoundRect(new RectF(pl,h*.15f,pr,h*.97f),22,22,p);
        stroke.setColor(Color.argb(145,Color.red(glow[ti]),Color.green(glow[ti]),Color.blue(glow[ti]))); stroke.setStrokeWidth(2); c.drawRoundRect(new RectF(pl,h*.15f,pr,h*.97f),22,22,stroke);
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT_BOLD);p.setColor(Color.WHITE);p.setTextSize(h*.024f);c.drawText("MAPA TÁTICO",pc,h*.19f,p);
        RectF mini=new RectF(pl+w*.012f,h*.215f,pr-w*.012f,h*.43f); drawMiniMap(c,mini,glow[ti]);
        p.setColor(glow[ti]);p.setTextSize(h*.026f);c.drawText("MISSÃO",pc,h*.485f,p);p.setColor(Color.WHITE);p.setTextSize(h*.019f);c.drawText(remainingCoins>0?"COLETE "+remainingCoins+" MOEDAS":"PORTAL LIBERADO",pc,h*.518f,p);
        p.setColor(Color.rgb(155,190,215));p.setTextSize(h*.017f);c.drawText("ROBÔ  "+robotModelNames[robotModelIndex],pc,h*.555f,p);c.drawText("CONTROLE  "+controlNames[controlMode],pc,h*.585f,p);

        float cx=pc, cy=h*.79f, rad=Math.min(pr-pl,h*.18f)*.27f;
        if(controlMode==0){
            p.setShadowLayer(18,0,0,glow[ti]);p.setColor(Color.argb(175,12,46,78));c.drawCircle(cx,cy,rad*1.7f,p);p.clearShadowLayer();stroke.setColor(glow[ti]);stroke.setStrokeWidth(3);c.drawCircle(cx,cy,rad*1.7f,stroke);p.setColor(Color.argb(235,45,125,185));c.drawCircle(cx,cy,rad*.80f,p);
            p.setColor(Color.rgb(180,220,245));p.setTextSize(h*.016f);c.drawText("ARRASTE",cx,h*.92f,p);
        }else if(controlMode==2){
            float ss=rad*1.05f;leftBtn.set(cx-ss*2.05f,cy-ss*.55f,cx-ss*.85f,cy+ss*.55f);rightBtn.set(cx+ss*.85f,cy-ss*.55f,cx+ss*2.05f,cy+ss*.55f);upBtn.set(cx-ss*.55f,cy-ss*2.05f,cx+ss*.55f,cy-ss*.85f);downBtn.set(cx-ss*.55f,cy+ss*.85f,cx+ss*.55f,cy+ss*2.05f);drawControl(c,leftBtn,0);drawControl(c,rightBtn,1);drawControl(c,upBtn,2);drawControl(c,downBtn,3);
        }else{
            RectF hint=new RectF(pl+w*.014f,h*.70f,pr-w*.014f,h*.88f);p.setColor(Color.rgb(14,45,72));c.drawRoundRect(hint,16,16,p);stroke.setColor(Color.argb(90,Color.red(glow[ti]),Color.green(glow[ti]),Color.blue(glow[ti])));stroke.setStrokeWidth(1.5f);c.drawRoundRect(hint,16,16,stroke);p.setColor(Color.WHITE);p.setTextSize(h*.019f);c.drawText(controlMode==1?"DESLIZE NA TELA":"TOQUE NO CAMINHO",pc,h*.80f,p);
        }

        if(paused){
            p.setColor(Color.argb(190,0,5,15)); c.drawRect(0,0,w,h,p); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setColor(Color.WHITE); p.setTextSize(h*.10f); c.drawText("PAUSADO",w/2,h*.50f,p); p.setTextSize(h*.035f); c.drawText("Toque em  ▶  para continuar",w/2,h*.58f,p);
        }
    }

    private void drawFloorTile(Canvas c,float x,float y,float cell,int base,int accent,int ti,int r,int col){
        int dark=mix(base,Color.BLACK,.16f), light=mix(base,Color.WHITE,.10f);
        p.setColor(dark); c.drawRect(x,y,x+cell+1,y+cell+1,p);
        p.setColor(base); c.drawRoundRect(new RectF(x+cell*.045f,y+cell*.045f,x+cell*.955f,y+cell*.955f),cell*.055f,cell*.055f,p);
        // top and left bevels
        p.setColor(light); c.drawRect(x+cell*.07f,y+cell*.07f,x+cell*.93f,y+cell*.115f,p); c.drawRect(x+cell*.07f,y+cell*.07f,x+cell*.115f,y+cell*.93f,p);
        p.setColor(mix(base,Color.BLACK,.28f)); c.drawRect(x+cell*.07f,y+cell*.885f,x+cell*.93f,y+cell*.93f,p); c.drawRect(x+cell*.885f,y+cell*.07f,x+cell*.93f,y+cell*.93f,p);
        // panel seams / vents vary by coordinate and world
        int code=(r*31+col*17+level*7+ti*13)%12;
        if(code==0||code==7){
            p.setColor(Color.argb(95,Color.red(accent),Color.green(accent),Color.blue(accent)));
            c.drawRoundRect(new RectF(x+cell*.24f,y+cell*.44f,x+cell*.76f,y+cell*.54f),cell*.03f,cell*.03f,p);
        } else if(code==3){
            stroke.setStrokeWidth(Math.max(1,cell*.025f)); stroke.setColor(Color.argb(80,210,235,255));
            for(int k=0;k<3;k++) c.drawLine(x+cell*(.28f+k*.16f),y+cell*.30f,x+cell*(.28f+k*.16f),y+cell*.70f,stroke);
        }
        // screws
        p.setColor(Color.argb(120,190,215,230)); float rr=Math.max(1.2f,cell*.022f);
        c.drawCircle(x+cell*.14f,y+cell*.14f,rr,p); c.drawCircle(x+cell*.86f,y+cell*.86f,rr,p);
    }

    private void drawWorldDecor(Canvas c,float left,float top,float cell,int ti,int accent){
        // deterministic scene props so every world looks distinct without blocking movement
        for(int r=1;r<rows-1;r++) for(int cc=1;cc<cols-1;cc++){
            int code=(r*43+cc*29+level*11)%97; if(code>3) continue;
            float x=left+(cc+.5f)*cell,y=top+(r+.5f)*cell;
            if(ti==0){ // lab: cyan conduits
                stroke.setColor(Color.argb(120,0,230,255));stroke.setStrokeWidth(Math.max(2,cell*.045f));c.drawLine(x-cell*.20f,y,x+cell*.20f,y,stroke);
            } else if(ti==1){ // ruins: cracks
                stroke.setColor(Color.argb(110,255,165,70));stroke.setStrokeWidth(Math.max(1,cell*.025f));Path q=new Path();q.moveTo(x-cell*.20f,y-cell*.18f);q.lineTo(x-cell*.05f,y);q.lineTo(x+cell*.08f,y-cell*.05f);q.lineTo(x+cell*.20f,y+cell*.18f);c.drawPath(q,stroke);
            } else if(ti==2){ // ice: crystalline shine
                p.setColor(Color.argb(80,170,245,255));Path q=new Path();q.moveTo(x,y-cell*.25f);q.lineTo(x-cell*.16f,y);q.lineTo(x,y+cell*.25f);q.lineTo(x+cell*.16f,y);q.close();c.drawPath(q,p);
            } else if(ti==3){ // volcano: lava slit
                p.setShadowLayer(cell*.12f,0,0,Color.RED);p.setColor(Color.argb(165,255,80,20));c.drawRoundRect(new RectF(x-cell*.24f,y-cell*.04f,x+cell*.24f,y+cell*.04f),cell*.03f,cell*.03f,p);p.clearShadowLayer();
            } else if(ti==4){ // factory: hazard stripe
                p.setColor(Color.argb(100,255,205,40)); for(int k=-2;k<=2;k+=2) c.drawRect(x+cell*k*.06f,y-cell*.22f,x+cell*(k*.06f+.05f),y+cell*.22f,p);
            } else { // void: purple energy node
                p.setShadowLayer(cell*.18f,0,0,accent);p.setColor(Color.argb(130,Color.red(accent),Color.green(accent),Color.blue(accent)));c.drawCircle(x,y,cell*.09f,p);p.clearShadowLayer();
            }
        }
    }

    private void drawWallV(Canvas c,float x,float y1,float y2,float wt,int base,int accent,int ti,int r,int col){
        float depth=wt*.42f;
        p.setColor(mix(base,Color.BLACK,.55f)); c.drawRoundRect(new RectF(x-wt*.56f+depth,y1+depth,x+wt*.56f+depth,y2+depth),wt*.16f,wt*.16f,p);
        LinearGradient g=new LinearGradient(x-wt*.55f,y1,x+wt*.55f,y1,mix(base,Color.WHITE,.26f),base,Shader.TileMode.CLAMP);p.setShader(g);c.drawRoundRect(new RectF(x-wt*.56f,y1,x+wt*.56f,y2),wt*.16f,wt*.16f,p);p.setShader(null);
        // segmented armor plates
        stroke.setStrokeWidth(Math.max(1.2f,wt*.06f));stroke.setColor(Color.argb(90,230,245,255));c.drawLine(x-wt*.48f,(y1+y2)/2,x+wt*.48f,(y1+y2)/2,stroke);
        p.setColor(Color.argb(180,Color.red(accent),Color.green(accent),Color.blue(accent)));c.drawRoundRect(new RectF(x-wt*.11f,y1+wt*.18f,x+wt*.11f,y2-wt*.18f),wt*.08f,wt*.08f,p);
        p.setColor(Color.argb(150,220,230,235));c.drawCircle(x-wt*.35f,y1+wt*.28f,Math.max(1.2f,wt*.055f),p);c.drawCircle(x+wt*.35f,y2-wt*.28f,Math.max(1.2f,wt*.055f),p);
    }

    private void drawWallH(Canvas c,float x1,float x2,float y,float wt,int base,int accent,int ti,int r,int col){
        float depth=wt*.42f;
        p.setColor(mix(base,Color.BLACK,.55f)); c.drawRoundRect(new RectF(x1+depth,y-wt*.56f+depth,x2+depth,y+wt*.56f+depth),wt*.16f,wt*.16f,p);
        LinearGradient g=new LinearGradient(x1,y-wt*.55f,x1,y+wt*.55f,mix(base,Color.WHITE,.30f),base,Shader.TileMode.CLAMP);p.setShader(g);c.drawRoundRect(new RectF(x1,y-wt*.56f,x2,y+wt*.56f),wt*.16f,wt*.16f,p);p.setShader(null);
        stroke.setStrokeWidth(Math.max(1.2f,wt*.06f));stroke.setColor(Color.argb(90,230,245,255));c.drawLine((x1+x2)/2,y-wt*.48f,(x1+x2)/2,y+wt*.48f,stroke);
        p.setColor(Color.argb(180,Color.red(accent),Color.green(accent),Color.blue(accent)));c.drawRoundRect(new RectF(x1+wt*.18f,y-wt*.11f,x2-wt*.18f,y+wt*.11f),wt*.08f,wt*.08f,p);
        p.setColor(Color.argb(150,220,230,235));c.drawCircle(x1+wt*.28f,y-wt*.35f,Math.max(1.2f,wt*.055f),p);c.drawCircle(x2-wt*.28f,y+wt*.35f,Math.max(1.2f,wt*.055f),p);
    }

    private void drawCoin(Canvas c,float x,float y,float cell){
        float pulse=1f+(float)Math.sin(System.currentTimeMillis()/180.0)*.08f; float rr=cell*.16f*pulse;
        p.setShadowLayer(cell*.25f,0,0,Color.rgb(255,180,0));p.setColor(Color.rgb(255,183,18));c.drawCircle(x,y,rr,p);p.clearShadowLayer();
        p.setColor(Color.rgb(255,231,94));c.drawCircle(x-cell*.035f,y-cell*.045f,rr*.53f,p);stroke.setColor(Color.rgb(180,95,0));stroke.setStrokeWidth(Math.max(1.5f,cell*.025f));c.drawCircle(x,y,rr*.76f,stroke);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(cell*.15f);p.setColor(Color.rgb(155,80,0));c.drawText("★",x,y+cell*.052f,p);
    }

    private void drawPortal(Canvas c,float x,float y,float cell,int color,boolean open){
        float a=(System.currentTimeMillis()%1400)/1400f;float r1=cell*(.20f+.07f*a),r2=cell*(.31f-.05f*a);
        p.setShadowLayer(cell*.35f,0,0,color);p.setColor(Color.argb(open?190:130,Color.red(color),Color.green(color),Color.blue(color)));c.drawCircle(x,y,cell*.26f,p);p.clearShadowLayer();
        stroke.setColor(Color.WHITE);stroke.setStrokeWidth(Math.max(2,cell*.035f));c.drawCircle(x,y,r1,stroke);stroke.setColor(color);stroke.setStrokeWidth(Math.max(2,cell*.06f));c.drawCircle(x,y,r2,stroke);
        p.setColor(Color.argb(open?230:160,Color.red(color),Color.green(color),Color.blue(color)));c.drawCircle(x,y,cell*.09f,p);
    }

    private void drawScannerCone(Canvas c,float x,float y,float cell,int kind){
        float ang=(float)(((System.currentTimeMillis()/900.0)+(kind*1.7))%(Math.PI*2));float len=cell*.95f,spread=.36f;
        Path q=new Path();q.moveTo(x,y);q.lineTo(x+(float)Math.cos(ang-spread)*len,y+(float)Math.sin(ang-spread)*len);q.lineTo(x+(float)Math.cos(ang+spread)*len,y+(float)Math.sin(ang+spread)*len);q.close();p.setColor(Color.argb(38,255,55,55));c.drawPath(q,p);
    }

    private void playFx(int type){
        if(!soundOn || fxTone==null) return;
        if(type==1) fxTone.startTone(ToneGenerator.TONE_PROP_BEEP,90);
        else if(type==2) fxTone.startTone(ToneGenerator.TONE_PROP_NACK,130);
        else if(type==3) fxTone.startTone(ToneGenerator.TONE_PROP_ACK,220);
    }

    private boolean isHazard(int r,int c){
        if(level<7 || (r==playerR&&c==playerC) || (r==exitR&&c==exitC)) return false;
        return ((r*37+c*19+level*13)%41)==0;
    }

    private void drawMiniMap(Canvas c, RectF r, int accent){
        p.setColor(Color.rgb(3,13,26)); c.drawRoundRect(r,14,14,p); stroke.setColor(Color.argb(120,Color.red(accent),Color.green(accent),Color.blue(accent)));stroke.setStrokeWidth(1.5f);c.drawRoundRect(r,14,14,stroke);
        float pad=8, cw=(r.width()-pad*2)/cols, ch=(r.height()-pad*2)/rows;
        stroke.setStrokeWidth(Math.max(1f,Math.min(cw,ch)*.12f)); stroke.setColor(Color.argb(135,125,180,215));
        for(int rr=0;rr<rows;rr++) for(int cc=0;cc<=cols;cc++) if(vWalls[rr][cc]){float x=r.left+pad+cc*cw,y1=r.top+pad+rr*ch; c.drawLine(x,y1,x,y1+ch,stroke);}
        for(int rr=0;rr<=rows;rr++) for(int cc=0;cc<cols;cc++) if(hWalls[rr][cc]){float y=r.top+pad+rr*ch,x1=r.left+pad+cc*cw; c.drawLine(x1,y,x1+cw,y,stroke);}
        float px=r.left+pad+(playerC+.5f)*cw,py=r.top+pad+(playerR+.5f)*ch; p.setColor(accent);c.drawCircle(px,py,Math.max(2.5f,Math.min(cw,ch)*.35f),p);
        p.setColor(remainingCoins==0?Color.GREEN:Color.RED);c.drawCircle(r.left+pad+(exitC+.5f)*cw,r.top+pad+(exitR+.5f)*ch,Math.max(2f,Math.min(cw,ch)*.28f),p);
    }

    private void drawRobot(Canvas c,float x,float y,float cell){
        int body=robotColors[robotColorIndex]; float s=cell*.43f;
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
        int[] ec={Color.rgb(245,64,64),Color.rgb(180,70,235),Color.rgb(30,205,235),Color.rgb(255,145,25),Color.rgb(90,220,95)}; int co=ec[kind%ec.length]; float s=cell*.36f;
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
        if(isHazard(playerR,playerC)){ playFx(2); lives--; score=Math.max(0,score-75); playerR=startR; playerC=startC; if(lives<=0){ saveRecords(); screen=Screen.MENU; } invalidate(); return; }
        if(coins[playerR][playerC]){coins[playerR][playerC]=false;remainingCoins--;score+=100; playFx(1);}
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
        for(Enemy e:enemies) if(e.r==playerR&&e.c==playerC){ playFx(2); lives--; score=Math.max(0,score-100); playerR=startR;playerC=startC; if(lives<=0){ saveRecords(); screen=Screen.MENU; } return; }
    }
    private void completeLevel(){
        playFx(3);
        score+=500+level*50; bestLevel=Math.max(bestLevel,level); highScore=Math.max(highScore,score); if(level<30)unlocked=Math.max(unlocked,level+1); saveRecords();
        if(level<30) startLevel(level+1); else screen=Screen.RECORDS;
    }
    private void saveRecords(){ prefs.edit().putInt("unlocked",unlocked).putInt("bestLevel",bestLevel).putInt("highScore",highScore).putBoolean("sound",soundOn).putInt("robotColor",robotColorIndex).putInt("robotModel",robotModelIndex).putInt("controlMode",controlMode).apply(); }

    @Override public boolean onTouchEvent(MotionEvent event){
        float x=event.getX(),y=event.getY(); float h=getHeight(),w=getWidth();
        if(screen==Screen.MENU && event.getAction()==MotionEvent.ACTION_UP){
            if(x>w*.47f){
                float top=h*.07f, bh=h*.105f, gap=h*.024f, y0=top+h*.155f;
                if(y>=y0&&y<=y0+bh){score=0;startLevel(Math.min(unlocked,30));}
                else if(y>=y0+bh+gap&&y<=y0+2*bh+gap){screen=Screen.LEVELS;invalidate();}
                else if(y>=y0+2*(bh+gap)&&y<=y0+3*bh+2*gap){screen=Screen.RECORDS;invalidate();}
                else if(y>=y0+3*(bh+gap)&&y<=y0+4*bh+3*gap){screen=Screen.SETTINGS;invalidate();}
            }
            return true;
        }
        if((screen==Screen.LEVELS||screen==Screen.RECORDS||screen==Screen.SETTINGS)&&event.getAction()==MotionEvent.ACTION_UP&&y<h*.12f&&x<w*.20f){screen=Screen.MENU;invalidate();return true;}
        if(screen==Screen.SETTINGS && event.getAction()==MotionEvent.ACTION_UP){
            float left=w*.055f, mid=w*.50f, right=w*.945f, top=h*.16f;
            if(x<mid){
                for(int i=0;i<4;i++){ float yy=top+h*(.13f+i*.13f); if(y>=yy&&y<=yy+h*.095f){controlMode=i;saveRecords();invalidate();return true;} }
            } else {
                if(y>=top+h*.29f&&y<=top+h*.42f){robotModelIndex=(robotModelIndex+1)%robotModelNames.length;saveRecords();invalidate();return true;}
                if(y>=top+h*.445f&&y<=top+h*.545f){
                    float first=mid+w*.018f+w*.045f, span=(right-(mid+w*.018f)-w*.09f); int idx=Math.round((x-first)/(span/(robotColors.length-1))); robotColorIndex=Math.max(0,Math.min(robotColors.length-1,idx));saveRecords();invalidate();return true;
                }
                if(y>=top+h*.57f&&y<=top+h*.70f){soundOn=!soundOn;if(musicPlayer!=null){if(soundOn){if(!musicPlayer.isPlaying())musicPlayer.start();}else if(musicPlayer.isPlaying())musicPlayer.pause();}saveRecords();invalidate();return true;}
            }
        }
        if(screen==Screen.LEVELS && event.getAction()==MotionEvent.ACTION_UP){
            int columns=10; float gap=w*.012f, left=w*.055f, right=w*.945f; float cell=(right-left-gap*(columns-1))/columns; float top=h*.22f;
            for(int i=1;i<=30;i++){ int rr=(i-1)/columns,cc=(i-1)%columns; float bx=left+cc*(cell+gap),by=top+rr*(cell+gap+h*.030f); if(x>=bx&&x<=bx+cell&&y>=by&&y<=by+cell*.78f&&i<=unlocked){score=0;startLevel(i);return true;} }
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
