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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new RoboMazeView(this));
    }
}

class RoboMazeView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private Bitmap cover;
    private MediaPlayer musicPlayer;

    private enum Screen { MENU, LEVELS, GAME, RECORDS, HELP }
    private Screen screen = Screen.MENU;

    private int level = 1;
    private int unlocked = 1;
    private int bestLevel = 1;
    private int highScore = 0;
    private int score = 0;
    private int lives = 3;
    private boolean paused = false;
    private boolean soundOn = true;

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
        soundOn = prefs.getBoolean("sound", true);
        cover = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier("robo_maze_cover", "drawable", context.getPackageName()));
        musicPlayer = MediaPlayer.create(context, getResources().getIdentifier("suspense_loop", "raw", context.getPackageName()));
        if (musicPlayer != null) {
            musicPlayer.setLooping(true);
            musicPlayer.setVolume(0.38f, 0.38f);
            if (soundOn) musicPlayer.start();
        }
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        handler.post(ticker);
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
            case HELP: drawHelp(c); break;
        }
    }

    private void drawMenu(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (cover != null) {
            float size = Math.min(w * .72f, h * .40f);
            RectF dst = new RectF((w-size)/2, h*.04f, (w+size)/2, h*.04f+size);
            c.drawBitmap(cover, null, dst, p);
        }
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE);
        p.setTextSize(w*.082f);
        c.drawText("ROBO MAZE", w/2, h*.48f, p);
        p.setColor(Color.rgb(255, 205, 30));
        p.setTextSize(w*.035f);
        c.drawText("DESVIE • COLETE • VENÇA!", w/2, h*.515f, p);

        drawMenuButton(c, "▶  JOGAR", h*.56f, Color.rgb(255,170,0));
        drawMenuButton(c, "▦  NÍVEIS", h*.655f, Color.rgb(0,155,235));
        drawMenuButton(c, "★  RECORDES", h*.75f, Color.rgb(0,125,220));
        drawMenuButton(c, "?  COMO JOGAR", h*.845f, Color.rgb(20,105,190));

        p.setTextSize(w*.033f);
        p.setColor(Color.WHITE);
        c.drawText(soundOn ? "🔊 SOM LIGADO" : "🔇 SOM DESLIGADO", w/2, h*.965f, p);
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

    private void drawHelp(Canvas c) {
        float w=getWidth(),h=getHeight();
        drawHeader(c,"COMO JOGAR");
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setTextSize(w*.052f); p.setColor(Color.rgb(255,205,30));
        c.drawText("MISSÃO",w*.08f,h*.24f,p);
        p.setTypeface(android.graphics.Typeface.DEFAULT); p.setTextSize(w*.041f); p.setColor(Color.WHITE);
        String[] lines={"Use as 4 setas para mover o robô pelo labirinto.","Colete todas as moedas douradas para abrir a saída.","Evite os inimigos: encostar neles custa uma vida.","Você começa com 3 vidas em cada fase.","A dificuldade aumenta gradualmente até o nível 30."};
        float y=h*.31f;
        for(String s: lines){ drawWrapped(c,s,w*.08f,y,w*.84f,w*.041f); y+=h*.10f; }
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
        rows=Math.min(15,7+tier*2); cols=Math.min(13,7+tier);
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
        int desired=Math.min(4+level/2,12);
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

    private void drawGame(Canvas c){
        float w=getWidth(),h=getHeight();
        p.setColor(Color.rgb(235,249,255)); c.drawRect(0,0,w,h,p);
        p.setColor(Color.rgb(4,37,93)); c.drawRect(0,0,w,h*.10f,p);
        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); p.setTextSize(w*.035f); p.setColor(Color.WHITE);
        c.drawText("NÍVEL",w*.035f,h*.036f,p); p.setTextSize(w*.06f); c.drawText(String.valueOf(level),w*.035f,h*.078f,p);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(w*.033f); c.drawText("PONTOS",w*.46f,h*.035f,p); p.setTextSize(w*.055f); c.drawText(String.valueOf(score),w*.46f,h*.078f,p);
        p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(w*.052f); p.setColor(Color.rgb(235,45,45)); c.drawText("♥".repeat(Math.max(0,lives)),w*.83f,h*.065f,p);
        pauseBtn.set(w*.88f,h*.018f,w*.97f,h*.087f); p.setColor(Color.rgb(0,125,220)); c.drawOval(pauseBtn,p); p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(w*.045f); c.drawText(paused?"▶":"Ⅱ",pauseBtn.centerX(),pauseBtn.centerY()+p.getTextSize()*.34f,p);

        float top=h*.12f,bottom=h*.72f,left=w*.055f,right=w*.945f;
        float cell=Math.min((right-left)/cols,(bottom-top)/rows);
        float bw=cell*cols,bh=cell*rows; float bx=(w-bw)/2, by=top+(bottom-top-bh)/2;
        boardRect.set(bx,by,bx+bw,by+bh);
        p.setColor(Color.WHITE); c.drawRoundRect(new RectF(bx-8,by-8,bx+bw+8,by+bh+8),18,18,p);
        stroke.setColor(Color.rgb(15,145,225)); stroke.setStrokeWidth(Math.max(3,cell*.08f));
        for(int r=0;r<rows;r++) for(int cc=0;cc<=cols;cc++) if(vWalls[r][cc]) c.drawLine(bx+cc*cell,by+r*cell,bx+cc*cell,by+(r+1)*cell,stroke);
        for(int rr=0;rr<=rows;rr++) for(int cc=0;cc<cols;cc++) if(hWalls[rr][cc]) c.drawLine(bx+cc*cell,by+rr*cell,bx+(cc+1)*cell,by+rr*cell,stroke);

        for(int r=0;r<rows;r++) for(int cc=0;cc<cols;cc++){
            float cx=bx+(cc+.5f)*cell, cy=by+(r+.5f)*cell;
            p.setColor(Color.rgb(120,205,245)); c.drawCircle(cx,cy,Math.max(1.7f,cell*.045f),p);
            if(coins[r][cc]){ p.setColor(Color.rgb(255,184,0)); c.drawCircle(cx,cy,cell*.20f,p); p.setColor(Color.rgb(255,225,75)); c.drawCircle(cx,cy,cell*.11f,p); }
        }
        // Exit
        float ex=bx+(exitC+.5f)*cell, ey=by+(exitR+.5f)*cell;
        p.setColor(remainingCoins==0?Color.rgb(55,205,80):Color.rgb(120,130,150)); c.drawCircle(ex,ey,cell*.24f,p);
        p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(cell*.34f); c.drawText("★",ex,ey+p.getTextSize()*.35f,p);

        for(Enemy e:enemies) drawEnemy(c,bx+(e.c+.5f)*cell,by+(e.r+.5f)*cell,cell,e.kind);
        drawRobot(c,bx+(playerC+.5f)*cell,by+(playerR+.5f)*cell,cell);

        float cy=h*.845f, radius=Math.min(w*.095f,h*.06f), gap=w*.03f;
        float total=radius*8+gap*3, sx=(w-total)/2+radius;
        leftBtn.set(sx-radius,cy-radius,sx+radius,cy+radius);
        rightBtn.set(sx+2*radius+gap-radius,cy-radius,sx+2*radius+gap+radius,cy+radius);
        upBtn.set(sx+4*radius+2*gap-radius,cy-radius,sx+4*radius+2*gap+radius,cy+radius);
        downBtn.set(sx+6*radius+3*gap-radius,cy-radius,sx+6*radius+3*gap+radius,cy+radius);
        drawControl(c,leftBtn,0); drawControl(c,rightBtn,1); drawControl(c,upBtn,2); drawControl(c,downBtn,3);
        p.setColor(Color.rgb(4,37,93)); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(w*.032f); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("MOEDAS: "+remainingCoins, w/2,h*.955f,p);
        if(paused){ p.setColor(Color.argb(190,3,25,70)); c.drawRect(0,h*.1f,w,h,p); p.setColor(Color.WHITE); p.setTextSize(w*.085f); c.drawText("PAUSADO",w/2,h*.48f,p); p.setTextSize(w*.038f); c.drawText("Toque em ▶ para continuar",w/2,h*.55f,p); }
    }

    private void drawRobot(Canvas c,float x,float y,float cell){
        p.setColor(Color.WHITE); c.drawCircle(x,y,cell*.27f,p);
        p.setColor(Color.rgb(20,75,130)); c.drawRoundRect(new RectF(x-cell*.19f,y-cell*.12f,x+cell*.19f,y+cell*.11f),cell*.08f,cell*.08f,p);
        p.setColor(Color.CYAN); c.drawCircle(x-cell*.08f,y-cell*.01f,cell*.035f,p); c.drawCircle(x+cell*.08f,y-cell*.01f,cell*.035f,p);
        p.setColor(Color.rgb(0,120,220)); c.drawCircle(x,y-cell*.31f,cell*.045f,p); c.drawRect(x-cell*.015f,y-cell*.29f,x+cell*.015f,y-cell*.23f,p);
    }
    private void drawEnemy(Canvas c,float x,float y,float cell,int kind){
        int[] colors={Color.rgb(235,55,55),Color.rgb(145,60,200),Color.rgb(20,185,235),Color.rgb(245,145,20),Color.rgb(70,190,65)};
        p.setColor(colors[kind%colors.length]); c.drawCircle(x,y,cell*.22f,p); stroke.setColor(colors[kind%colors.length]); stroke.setStrokeWidth(cell*.055f);
        for(int i=-1;i<=1;i+=2){ c.drawLine(x+i*cell*.13f,y+cell*.12f,x+i*cell*.28f,y+cell*.25f,stroke); c.drawLine(x+i*cell*.13f,y-cell*.10f,x+i*cell*.28f,y-cell*.22f,stroke); }
        p.setColor(Color.WHITE); c.drawCircle(x-cell*.07f,y-cell*.04f,cell*.035f,p); c.drawCircle(x+cell*.07f,y-cell*.04f,cell*.035f,p);
        p.setColor(Color.BLACK); c.drawCircle(x-cell*.07f,y-cell*.04f,cell*.016f,p); c.drawCircle(x+cell*.07f,y-cell*.04f,cell*.016f,p);
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
    private void saveRecords(){ prefs.edit().putInt("unlocked",unlocked).putInt("bestLevel",bestLevel).putInt("highScore",highScore).putBoolean("sound",soundOn).apply(); }

    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_UP)return true; float x=event.getX(),y=event.getY(); float h=getHeight(),w=getWidth();
        if(screen==Screen.MENU){
            if(y>h*.525f&&y<h*.61f){score=0;startLevel(Math.min(unlocked,30));}
            else if(y>h*.615f&&y<h*.70f){screen=Screen.LEVELS;invalidate();}
            else if(y>h*.705f&&y<h*.80f){screen=Screen.RECORDS;invalidate();}
            else if(y>h*.80f&&y<h*.90f){screen=Screen.HELP;invalidate();}
            else if(y>h*.925f){
                soundOn=!soundOn;
                if(musicPlayer!=null){ if(soundOn){ if(!musicPlayer.isPlaying())musicPlayer.start(); } else if(musicPlayer.isPlaying()) musicPlayer.pause(); }
                saveRecords();invalidate();
            }
            return true;
        }
        if((screen==Screen.LEVELS||screen==Screen.RECORDS||screen==Screen.HELP)&&y<h*.12f&&x<w*.20f){screen=Screen.MENU;invalidate();return true;}
        if(screen==Screen.LEVELS){
            int columns=5; float gap=w*.025f, cell=(w-gap*(columns+1))/columns, top=h*.18f;
            for(int i=1;i<=30;i++){int rr=(i-1)/columns,cc=(i-1)%columns;float bx=gap+cc*(cell+gap),by=top+rr*(cell+gap);if(x>=bx&&x<=bx+cell&&y>=by&&y<=by+cell&&i<=unlocked){score=0;startLevel(i);return true;}}
        }
        if(screen==Screen.GAME){
            if(pauseBtn.contains(x,y)){paused=!paused;invalidate();return true;}
            if(paused)return true;
            if(leftBtn.contains(x,y))movePlayer(0,-1); else if(rightBtn.contains(x,y))movePlayer(0,1); else if(upBtn.contains(x,y))movePlayer(-1,0); else if(downBtn.contains(x,y))movePlayer(1,0);
        }
        return true;
    }

    private static class Enemy { int r,c,kind; Enemy(int r,int c,int kind){this.r=r;this.c=c;this.kind=kind;} }
}
