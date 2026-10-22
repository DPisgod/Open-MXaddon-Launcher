package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Orthographic textured cuboids, with independent limb pivots and alpha skin layers. */
final class PlayerDollView extends JComponent {
    private record Part(double x,double y,double z,double w,double h,double d,int u,int v,boolean mirror,double inflate,double pivotY,double rotation){}
    private record Face(double[] x,double[] y,double depth,BufferedImage texture,double light){}
    private final List<Face> faces=new ArrayList<>();
    private final Map<String,BufferedImage> patches=new HashMap<>();
    private PlayerTextures.Profile profile=new PlayerTextures.Profile("","Steve",PlayerTextures.defaultSkin(),null,false);
    boolean showCape=true,back;
    private long actionStarted;
    private int action=-1,nextAction;
    double angle;
    private String speech="";
    private long speechUntil;
    PlayerDollView(){setOpaque(false);setName("playerDollView");setToolTipText("左键互动，按住拖动；滚轮转身；右键设置");}
    void profile(PlayerTextures.Profile value){profile=value;patches.clear();repaint();}
    PlayerTextures.Profile profile(){return profile;}
    void say(String value){speech=value;speechUntil=System.nanoTime()+3_000_000_000L;repaint();}
    void interact(){action=nextAction++%3;actionStarted=System.nanoTime();say(switch(action){case 0->"你好呀！";case 1->"一起跳一下！";default->profile.cape()==null?"转个圈～":"看看我的披风！";});}
    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double seconds=System.nanoTime()/1e9,elapsed=(System.nanoTime()-actionStarted)/1e9,phase=0;
        if(action>=0){double duration=action==2?1.8:1.3;if(elapsed>=duration)action=-1;else phase=elapsed/duration;}
        double jump=action==1?Math.sin(phase*Math.PI)*3.4:0,turn=angle+(back?Math.PI:0)-.30+Math.sin(seconds*.7)*.08+(action==2?phase*Math.PI*2:0);
        double scale=Math.min(getWidth()/24.0,(getHeight()-55)/38.0),center=getWidth()/2.0,baseline=getHeight()-26;
        g.setColor(new Color(0,0,0,38));g.fill(new Ellipse2D.Double(center-scale*7,baseline-3,scale*14,scale*2.0));
        faces.clear();double sway=Math.sin(seconds*1.8)*.035,wave=action==0?-.9-Math.sin(phase*Math.PI*6)*.55:sway;
        double breath=Math.sin(seconds*2)*.10;
        if(showCape&&profile.cape()!=null){double flap=.12+Math.sin(seconds*1.5)*.07+(action==1?.4*Math.sin(phase*Math.PI):0);add(new Part(0,16,-2.8,10,16,1,0,0,false,0,24,flap),profile.cape(),true,turn,scale,center,baseline,jump+breath);}
        part(0,18,0,8,12,4,16,16,16,32,24,0,false,turn,scale,center,baseline,jump+breath);
        double arm=profile.slim()?3:4;
        part(-4-arm/2,18,0,arm,12,4,40,16,40,32,24,wave,false,turn,scale,center,baseline,jump+breath);
        boolean legacy=profile.skin().getHeight()==32;
        part(4+arm/2,18,0,arm,12,4,legacy?40:32,legacy?16:48,legacy?-1:48,48,24,-sway,legacy,turn,scale,center,baseline,jump+breath);
        part(-2,6,0,4,12,4,0,16,0,32,12,-sway*.7,false,turn,scale,center,baseline,jump);
        part(2,6,0,4,12,4,legacy?0:16,legacy?16:48,legacy?-1:0,48,12,sway*.7,legacy,turn,scale,center,baseline,jump);
        part(0,28,0,8,8,8,0,0,32,0,28,0,false,turn,scale,center,baseline,jump+breath);
        faces.sort(Comparator.comparingDouble(Face::depth));
        for(Face face:faces){Path2D path=new Path2D.Double();path.moveTo(face.x()[0],face.y()[0]);for(int i=1;i<4;i++)path.lineTo(face.x()[i],face.y()[i]);path.closePath();
            Graphics2D paint=(Graphics2D)g.create();paint.clip(path);BufferedImage tex=face.texture();AffineTransform transform=new AffineTransform((face.x()[1]-face.x()[0])/tex.getWidth(),(face.y()[1]-face.y()[0])/tex.getWidth(),(face.x()[3]-face.x()[0])/tex.getHeight(),(face.y()[3]-face.y()[0])/tex.getHeight(),face.x()[0],face.y()[0]);paint.drawImage(tex,transform,null);paint.dispose();
        }
        g.setFont(UiTheme.font(11,true));FontMetrics fm=g.getFontMetrics();String text=profile.name();int labelWidth=fm.stringWidth(text)+14;g.setColor(new Color(10,18,28,175));g.fillRoundRect((getWidth()-labelWidth)/2,getHeight()-20,labelWidth,19,12,12);g.setColor(Color.WHITE);g.drawString(text,(getWidth()-fm.stringWidth(text))/2,getHeight()-6);
        if(System.nanoTime()<speechUntil&&!speech.isBlank()){g.setFont(UiTheme.font(12,true));fm=g.getFontMetrics();int width=Math.min(getWidth()-8,fm.stringWidth(speech)+20),x=(getWidth()-width)/2;g.setColor(new Color(255,255,255,235));g.fillRoundRect(x,3,width,29,15,15);g.setColor(new Color(0x243448));g.drawString(speech,(getWidth()-fm.stringWidth(speech))/2,23);}
        g.dispose();
    }
    private void part(double x,double y,double z,double w,double h,double d,int u,int v,int overlayU,int overlayV,double pivot,double rotation,boolean mirror,double yaw,double scale,double center,double baseline,double jump){
        add(new Part(x,y,z,w,h,d,u,v,mirror,0,pivot,rotation),profile.skin(),false,yaw,scale,center,baseline,jump);
        if(overlayU>=0&&(profile.skin().getHeight()==64||overlayV==0))add(new Part(x,y,z,w,h,d,overlayU,overlayV,mirror,w==8&&h==8?.45:.22,pivot,rotation),profile.skin(),false,yaw,scale,center,baseline,jump);
    }
    private void add(Part p,BufferedImage texture,boolean cape,double yaw,double scale,double center,double baseline,double jump){
        double a=p.w()/2+p.inflate(),b=p.h()/2+p.inflate(),c=p.d()/2+p.inflate();double[][][] corners={
            {{-a,b,c},{a,b,c},{a,-b,c},{-a,-b,c}},{{a,b,c},{a,b,-c},{a,-b,-c},{a,-b,c}},{{a,b,-c},{-a,b,-c},{-a,-b,-c},{a,-b,-c}},{{-a,b,-c},{-a,b,c},{-a,-b,c},{-a,-b,-c}},{{-a,b,-c},{a,b,-c},{a,b,c},{-a,b,c}},{{-a,-b,c},{a,-b,c},{a,-b,-c},{-a,-b,-c}}};
        int w=(int)p.w(),h=(int)p.h(),d=(int)p.d(),u=p.u(),v=p.v();int[][] uv={{u+d,v+d,w,h},{u+d+w,v+d,d,h},{u+2*d+w,v+d,w,h},{u,v+d,d,h},{u+d,v,w,d},{u+d+w,v,w,d}};
        if(cape){uv[2]=new int[]{1,1,10,16};uv[0]=new int[]{12,1,10,16};}
        double cy=Math.cos(yaw),sy=Math.sin(yaw),pitch=.12,cp=Math.cos(pitch),sp=Math.sin(pitch),cr=Math.cos(p.rotation()),sr=Math.sin(p.rotation());
        for(int f=0;f<6;f++){
            double[] px=new double[4],py=new double[4];double depth=0;
            for(int i=0;i<4;i++){double[] point=corners[f][i];double x=point[0]+p.x(),y=point[1]+p.y()-p.pivotY(),z=point[2]+p.z();double ry=y*cr-z*sr+p.pivotY()+jump,rz=y*sr+z*cr;double rx=x*cy+rz*sy,zz=-x*sy+rz*cy;px[i]=center+rx*scale;py[i]=baseline+(-ry*cp+zz*sp)*scale;depth+=ry*sp+zz*cp;}
            double area=(px[1]-px[0])*(py[2]-py[0])-(py[1]-py[0])*(px[2]-px[0]);if(area<=.01)continue;
            double shade=f==4?1:f==0?.98:f==2?.82:.74;int[] region=uv[f];BufferedImage patch=patch(texture,region,p.mirror(),shade);if(patch!=null)faces.add(new Face(px,py,depth/4,patch,shade));
        }
    }
    private BufferedImage patch(BufferedImage texture,int[] r,boolean mirror,double light){
        if(r[0]<0||r[1]<0||r[0]+r[2]>texture.getWidth()||r[1]+r[3]>texture.getHeight())return null;
        String key=System.identityHashCode(texture)+":"+Arrays.toString(r)+":"+mirror+":"+light;return patches.computeIfAbsent(key,k->{BufferedImage patch=new BufferedImage(r[2],r[3],BufferedImage.TYPE_INT_ARGB);for(int y=0;y<r[3];y++)for(int x=0;x<r[2];x++){int color=texture.getRGB(r[0]+(mirror?r[2]-1-x:x),r[1]+y),alpha=color>>>24;int red=(int)((color>>16&255)*light),green=(int)((color>>8&255)*light),blue=(int)((color&255)*light);patch.setRGB(x,y,(alpha<<24)|(red<<16)|(green<<8)|blue);}return patch;});
    }
}
