package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

/** Wallpaper rendering shared by the home canvas and its live appearance preview. */
final class WallpaperView extends JPanel {
    final WallpaperPlayer player;
    private Appearance options;
    private final int radius;
    private BufferedImage last,blurred;
    private int lastBlur=-1;
    private BufferedImage shade;
    private int shadeMask=-1;
    private BufferedImage scene,sceneSource;
    private String sceneFit;
    private int sceneMask=-1;
    private UiTheme.Palette scenePalette;
    boolean previewUi;
    WallpaperView(WallpaperPlayer player,Appearance options,int radius){this.player=player;this.options=options.copy();this.radius=radius;setOpaque(false);setLayout(new BorderLayout());}
    void options(Appearance value){options=value.copy();repaint();}
    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();UiTheme.quality(g);int w=getWidth(),h=getHeight();
        if(radius>0)g.clip(new java.awt.geom.RoundRectangle2D.Double(0,0,w,h,radius,radius));
        BufferedImage image=player.frame();
        if(image!=null&&options.blur>0){if(image!=last||lastBlur!=options.blur){last=image;lastBlur=options.blur;blurred=soften(image,options.blur);}image=blurred;}
        // Resolve wallpaper and translucent shading into an opaque RGB scene before the
        // device transform. Fractional-DPI ARGB scaling otherwise stalls the Swing thread.
        if(w>0&&h>0){
            int width=Math.max(1,(int)Math.ceil(w*Math.abs(g.getTransform().getScaleX()))),height=Math.max(1,(int)Math.ceil(h*Math.abs(g.getTransform().getScaleY())));
            if(scene==null||sceneSource!=image||scene.getWidth()!=width||scene.getHeight()!=height||sceneMask!=options.mask||!options.fit.equals(sceneFit)||scenePalette!=UiTheme.p()){
                if(scene==null||scene.getWidth()!=width||scene.getHeight()!=height){scene=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);scene.setAccelerationPriority(0);}
                sceneSource=image;sceneFit=options.fit;sceneMask=options.mask;scenePalette=UiTheme.p();Graphics2D canvas=scene.createGraphics();UiTheme.quality(canvas);paintWallpaper(canvas,image,width,height);canvas.dispose();
            }
            double sx=Math.abs(g.getTransform().getScaleX()),sy=Math.abs(g.getTransform().getScaleY());
            g.drawImage(scene,java.awt.geom.AffineTransform.getScaleInstance(1/sx,1/sy),null);
        }
        if(previewUi){
            int opacity=Math.round(255*(1-options.uiTransparency/100f));g.setColor(UiTheme.alpha(UiTheme.p().surface(),opacity));g.fillRect(0,0,w,27);g.fillRoundRect(10,h-36,w-20,26,10,10);
            g.setFont(UiTheme.font(10,true));g.setColor(UiTheme.p().text());g.drawString("OML",11,18);g.setFont(UiTheme.font(9,false));g.drawString("游戏账号",20,h-19);g.setColor(UiTheme.p().accent());g.fillRoundRect(w-76,h-31,55,16,8,8);g.setColor(UiTheme.p().accentText());g.drawString("启动游戏",w-67,h-19);
        }
        g.dispose();super.paintComponent(graphics);
    }
    private void paintWallpaper(Graphics2D g,BufferedImage image,int w,int h){
        g.setColor(UiTheme.p().background());g.fillRect(0,0,w,h);
        if(image!=null){
            double scale="contain".equals(options.fit)?Math.min(w/(double)image.getWidth(),h/(double)image.getHeight()):Math.max(w/(double)image.getWidth(),h/(double)image.getHeight());
            int iw="stretch".equals(options.fit)?w:(int)Math.ceil(image.getWidth()*scale),ih="stretch".equals(options.fit)?h:(int)Math.ceil(image.getHeight()*scale);
            g.drawImage(image,(w-iw)/2,(h-ih)/2,iw,ih,null);
        }
        // Cache the two black overlays as one premultiplied raster. Rebuilding a translucent
        // GradientPaint across the full window on every video frame is costly on software GDI.
        if(w>0&&h>0){
            if(shade==null||shade.getWidth()!=w||shade.getHeight()!=h||shadeMask!=options.mask){
                shade=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB_PRE);shade.setAccelerationPriority(0);shadeMask=options.mask;int[] pixels=((DataBufferInt)shade.getRaster().getDataBuffer()).getData();int mask=(int)(options.mask*2.55);
                for(int y=0;y<h;y++){int gradient=35-30*y/h,alpha=255-(255-mask)*(255-gradient)/255;java.util.Arrays.fill(pixels,y*w,(y+1)*w,alpha<<24);}
            }
            g.drawImage(shade,0,0,null);
        }
    }
    private static BufferedImage soften(BufferedImage source,int amount){
        BufferedImage small=WallpaperLibrary.scale(source,360,225);int radius=Math.max(1,(int)Math.ceil(amount/14.0)),size=radius*2+1;
        float[] values=new float[size];java.util.Arrays.fill(values,1f/size);
        BufferedImage horizontal=new ConvolveOp(new Kernel(size,1,values),ConvolveOp.EDGE_NO_OP,null).filter(small,null);
        return new ConvolveOp(new Kernel(1,size,values),ConvolveOp.EDGE_NO_OP,null).filter(horizontal,null);
    }
}
