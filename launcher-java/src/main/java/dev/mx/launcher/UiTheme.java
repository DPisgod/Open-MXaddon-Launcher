package dev.mx.launcher;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

final class UiTheme {
    record Palette(boolean dark,Color background,Color surface,Color text,Color muted,Color line,Color accent,Color accentText){}
    static final Palette LIGHT=new Palette(false,new Color(0xF0F4F6),Color.WHITE,new Color(0x182738),new Color(0x64758A),new Color(0xDFE6EB),new Color(0x245EEA),Color.WHITE);
    static final Palette DARK=new Palette(true,new Color(0x111C28),new Color(0x1C2A3A),new Color(0xF0F4F8),new Color(0x9BAEC3),new Color(0x304054),new Color(0x9AC5FF),new Color(0x112846));
    private static Palette current=LIGHT;
    private static String style="light";
    private static final Palette CYBER_DARK=new Palette(true,new Color(0x090E1D),new Color(0x111B32),new Color(0xEDF6FF),new Color(0x9DADCA),new Color(0x334568),new Color(0x39DEFF),new Color(0x071426));
    static String style(){return style;}
    static boolean cyber(){return "cyber".equals(style);}
    private static boolean keyboardFocus;
    static {
        Toolkit.getDefaultToolkit().addAWTEventListener(event->{
            boolean previous=keyboardFocus;
            if(event instanceof MouseEvent mouse&&mouse.getID()==MouseEvent.MOUSE_PRESSED)keyboardFocus=false;
            else if(event instanceof KeyEvent key&&key.getID()==KeyEvent.KEY_PRESSED)keyboardFocus=true;
            if(previous!=keyboardFocus){Component owner=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();if(owner!=null)owner.repaint();}
        },AWTEvent.MOUSE_EVENT_MASK|AWTEvent.KEY_EVENT_MASK);
    }
    static boolean showFocus(Component component){return keyboardFocus&&component.hasFocus();}
    static Palette p(){return current;}
    static boolean set(String value){style="cyber".equals(value)?"cyber":("dark".equals(value)||("system".equals(value)&&systemDark()))?"dark":"light";Palette next=cyber()?CYBER_DARK:"dark".equals(style)?DARK:LIGHT;boolean changed=current!=next;current=next;return changed;}
    static boolean systemDark(){
        try{return Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER,"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize","AppsUseLightTheme")==0;}
        catch(Throwable ignored){return false;}
    }
    static Font font(int size,boolean bold){return new Font("Microsoft YaHei UI",bold?Font.BOLD:Font.PLAIN,size);}
    static JLabel label(String text,int size,boolean bold){JLabel l=new JLabel(text);l.setFont(font(size,bold));l.setForeground(p().text());return l;}
    static Color alpha(Color c,int a){return new Color(c.getRed(),c.getGreen(),c.getBlue(),a);}
    static void quality(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);}
    static void refresh(Component c){
        if(c instanceof JPanel panel&&!(panel instanceof Surface)&&panel.isOpaque())panel.setBackground(p().surface());
        if(c instanceof javax.swing.text.JTextComponent text){text.setBackground(p().background());text.setForeground(p().text());text.setCaretColor(p().accent());}
        if(c instanceof JButton button&&!(button instanceof Button)){button.setBackground(p().accent());button.setForeground(p().accentText());}
        if(c instanceof JLabel l)l.setForeground(Boolean.TRUE.equals(l.getClientProperty("muted"))?p().muted():p().text());
        if(c instanceof JScrollPane s){s.getViewport().setBackground(p().background());s.setBackground(p().background());}
        if(c instanceof JComboBox<?> box){box.setBackground(p().surface());box.setForeground(p().text());}
        if(c instanceof JList<?> list){list.setBackground(p().surface());list.setForeground(p().text());list.setSelectionBackground(p().line());list.setSelectionForeground(p().text());}
        if(c instanceof JTable table){table.setBackground(p().surface());table.setForeground(p().text());table.setGridColor(p().line());table.setSelectionBackground(p().line());table.setSelectionForeground(p().text());}
        if(c instanceof JCheckBox b){b.setForeground(p().text());b.setBackground(p().surface());}
        if(c instanceof JSlider s){s.setForeground(p().accent());s.setBackground(p().surface());}
        if(c instanceof Container parent)for(Component child:parent.getComponents())refresh(child);
        c.repaint();
    }
    static JLabel muted(String text,int size){JLabel l=label(text,size,false);l.putClientProperty("muted",true);l.setForeground(p().muted());return l;}
    static JPanel transparent(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    static void slider(JSlider slider){slider.setOpaque(false);slider.setUI(new BasicSliderUI(slider){
        @Override protected Dimension getThumbSize(){return new Dimension(14,14);}
        @Override public void paintTrack(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();quality(g);int y=trackRect.y+trackRect.height/2-2;g.setColor(p().line());g.fillRoundRect(trackRect.x,y,trackRect.width,4,4,4);g.setColor(slider.isEnabled()?p().accent():alpha(p().muted(),90));g.fillRoundRect(trackRect.x,y,Math.max(0,thumbRect.x+thumbRect.width/2-trackRect.x),4,4,4);g.dispose();}
        @Override public void paintThumb(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setColor(slider.isEnabled()?p().accent():p().line());g.fillOval(thumbRect.x,thumbRect.y,14,14);g.setColor(p().surface());g.fillOval(thumbRect.x+4,thumbRect.y+4,6,6);g.dispose();}
        @Override public void paintFocus(Graphics g){}
    });}
    static void scroll(JScrollPane scroll){JScrollBar bar=scroll.getVerticalScrollBar();bar.setPreferredSize(new Dimension(8,0));bar.setUI(new BasicScrollBarUI(){
        @Override protected JButton createDecreaseButton(int orientation){return empty();}@Override protected JButton createIncreaseButton(int orientation){return empty();}
        private JButton empty(){JButton b=new JButton();b.setPreferredSize(new Dimension());b.setMinimumSize(new Dimension());b.setMaximumSize(new Dimension());return b;}
        @Override protected void paintTrack(Graphics g,JComponent c,Rectangle bounds){g.setColor(p().surface());g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);}
        @Override protected void paintThumb(Graphics graphics,JComponent c,Rectangle bounds){Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setColor(alpha(p().muted(),110));g.fillRoundRect(bounds.x+1,bounds.y,Math.max(4,bounds.width-2),bounds.height,6,6);g.dispose();}
    });}
    static final class Toggle extends JCheckBox {
        Toggle(String text,boolean selected){super(text,selected);setOpaque(false);setFont(font(12,false));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setPreferredSize(new Dimension(240,28));}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setFont(getFont());g.setColor(isEnabled()?p().text():p().muted());FontMetrics metrics=g.getFontMetrics();g.drawString(getText(),0,(getHeight()-metrics.getHeight())/2+metrics.getAscent());int x=getWidth()-36,y=(getHeight()-20)/2;g.setColor(isEnabled()&&isSelected()?p().accent():p().line());if(cyber()&&isEnabled()&&isSelected())g.setPaint(new GradientPaint(x,y,new Color(0xA13DEC),x+36,y,new Color(0x16BADC)));g.fillRoundRect(x,y,36,20,20,20);g.setColor(isEnabled()&&isSelected()?p().accentText():p().muted());g.fillOval(x+(isSelected()?19:3),y+3,14,14);if(UiTheme.showFocus(this)){g.setColor(p().accent());g.drawRoundRect(x-2,y-2,40,24,24,24);}g.dispose();}
    }
    static void combo(JComboBox<?> box){
        box.setFont(font(13,false));box.setBackground(p().surface());box.setForeground(p().text());box.setFocusable(false);
        box.setUI(new BasicComboBoxUI(){@Override protected JButton createArrowButton(){return new Button("","chevron",Tone.PLAIN);}});
        box.setRenderer((list,value,index,selected,focus)->{
            JLabel l=label(value==null?"":value.toString(),13,false);l.setBorder(new EmptyBorder(9,12,9,12));l.setOpaque(true);
            l.setBackground(selected&&index>=0?p().accent():p().surface());l.setForeground(selected&&index>=0?p().accentText():p().text());return l;
        });
        box.setBorder(new AbstractBorder(){@Override public Insets getBorderInsets(Component c){return new Insets(2,3,2,3);}@Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){Graphics2D q=(Graphics2D)g.create();quality(q);q.setColor(cyber()?alpha(p().accent(),130):p().line());q.drawRoundRect(x,y,w-1,h-1,12,12);q.dispose();}});
    }
    enum Tone{PLAIN,SURFACE,PRIMARY,NAV}
    /** Paint every pixel ourselves: Windows' native progress painter ignores dark theme colors. */
    static final class ProgressBar extends JProgressBar {
        ProgressBar(){super(0,1000);setOpaque(false);setBorderPainted(false);setUI(new javax.swing.plaf.basic.BasicProgressBarUI(){@Override protected void setAnimationIndex(int index){super.setAnimationIndex(index);progressBar.repaint();}});}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();quality(g);int width=getWidth(),height=Math.min(8,getHeight()),top=(getHeight()-height)/2;if(width<=0||height<=0){g.dispose();return;}
            RoundRectangle2D track=new RoundRectangle2D.Double(0,top,width,height,height,height);g.setColor(p().line());g.fill(track);g.clip(track);
            int fill=(int)Math.round(width*Math.max(0,Math.min(1,getPercentComplete()))),left=0;
            if(isIndeterminate()){fill=Math.max(height,width/4);double phase=(System.nanoTime()/1_000_000L%1400)/1400.0;left=(int)Math.round((width-fill)*(1-Math.cos(phase*2*Math.PI))/2);}
            else if(!getComponentOrientation().isLeftToRight())left=width-fill;
            if(fill>0){g.setPaint(new GradientPaint(left,0,p().dark()?new Color(0x729EFF):p().accent(),left+Math.max(1,fill),0,p().dark()?p().accent():new Color(0x6898FA)));g.fillRoundRect(left,top,fill,height,height,height);}g.dispose();
        }
    }
    static final class Button extends JButton {
        final String symbol;final Tone tone;boolean selected;
        Button(String text,String symbol,Tone tone){super(text);this.symbol=symbol;this.tone=tone;setFont(font(13,tone==Tone.PRIMARY));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);setOpaque(false);setRolloverEnabled(true);setBorder(new EmptyBorder(8,14,8,14));getAccessibleContext().setAccessibleName(text.isBlank()?symbol:text);}
        @Override public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();if(isPreferredSizeSet())return d;return new Dimension(Math.max(38,d.width+(symbol.isBlank()?0:22)),Math.max(38,d.height));}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();quality(g);Palette p=p();
            boolean hover=getModel().isRollover()&&isEnabled();
            Color bg=tone==Tone.PRIMARY?p.accent():p.surface();
            int arc=tone==Tone.PRIMARY?28:tone==Tone.NAV?20:12;
            if(cyber()&&isEnabled()&&(tone==Tone.PRIMARY||selected||hover)){
                for(int i=4;i>0;i--){g.setColor(alpha(p.accent(),12+i*3));g.drawRoundRect(i,i,getWidth()-1-i*2,getHeight()-1-i*2,arc,arc);}
            }
            if(tone!=Tone.PLAIN||hover||selected){if(tone==Tone.NAV){g.setColor(selected?p.surface():hover?alpha(p.surface(),130):new Color(0,0,0,0));}else g.setColor(alpha(bg,isEnabled()?(tone==Tone.PRIMARY?255:hover?245:232):110));
                if(cyber()&&tone==Tone.PRIMARY&&isEnabled())g.setPaint(new GradientPaint(0,0,p.dark()?new Color(0x733DE0):new Color(0x7738D0),getWidth(),getHeight(),p.dark()?new Color(0x087EAD):new Color(0x225AC7)));
                g.fillRoundRect(3,3,getWidth()-6,getHeight()-6,arc,arc);}
            if(tone==Tone.SURFACE||cyber()&&(tone==Tone.PRIMARY||selected||hover)){g.setPaint(cyber()?new GradientPaint(0,0,new Color(0xE655FF),getWidth(),getHeight(),new Color(0x36DEFF)):p.line());g.drawRoundRect(3,3,getWidth()-7,getHeight()-7,arc,arc);}
            Color fg=tone==Tone.PRIMARY?p.accentText():selected?p.accent():p.text();if(!isEnabled())fg=alpha(fg,110);
            if(cyber()&&tone==Tone.PRIMARY&&isEnabled())fg=Color.WHITE;
            g.setFont(getFont());g.setColor(fg);FontMetrics fm=g.getFontMetrics();int width=fm.stringWidth(getText())+(symbol.isBlank()?0:getText().isBlank()?18:28),x=(getWidth()-width)/2;
            if(!symbol.isBlank()){icon(g,symbol,x,(getHeight()-18)/2,18);x+=getText().isBlank()?18:28;}
            g.drawString(getText(),x,(getHeight()-fm.getHeight())/2+fm.getAscent());
            if(selected&&tone==Tone.PLAIN){g.setColor(p.accent());g.fillRoundRect(12,getHeight()-3,getWidth()-24,2,2,2);}
            if(UiTheme.showFocus(this)){g.setColor(p.accent());g.drawRoundRect(1,1,getWidth()-3,getHeight()-3,arc,arc);}g.dispose();
        }
    }
    static final class PowerButton extends JButton {
        PowerButton(){setName("stopGames");getAccessibleContext().setAccessibleName("结束全部游戏");setPreferredSize(new Dimension(38,38));setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);setOpaque(false);setRolloverEnabled(true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();quality(g);int size=Math.min(getWidth(),getHeight())-4,x=(getWidth()-size)/2,y=(getHeight()-size)/2;g.setColor(isEnabled()?p().accent():alpha(p().surface(),230));g.fillOval(x,y,size,size);g.setColor(isEnabled()?p().accentText():p().muted());icon(g,"power",(getWidth()-20)/2,(getHeight()-20)/2,20);if(UiTheme.showFocus(this)||getModel().isRollover()){g.setColor(p().accent());g.drawOval(x-2,y-2,size+4,size+4);}g.dispose();}
    }
    static class Surface extends JPanel {
        final int radius;boolean background;int transparency;
        Surface(LayoutManager layout,int radius){super(layout);this.radius=radius;setOpaque(false);}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setComposite(AlphaComposite.SrcOver.derive(1-Math.max(0,Math.min(100,transparency))/100f));int arc=radius;g.setColor(background?p().background():alpha(p().surface(),244));if(cyber()&&!background)g.setPaint(new GradientPaint(0,0,p().surface(),getWidth(),getHeight(),p().dark()?new Color(0x17152E):new Color(0xEEF3FE)));g.fillRoundRect(0,0,getWidth(),getHeight(),arc,arc);if(!background){g.setPaint(cyber()?new GradientPaint(0,0,alpha(new Color(0xDB49FF),120),getWidth(),getHeight(),alpha(new Color(0x26DAF9),140)):alpha(p().line(),210));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,arc,arc);}g.dispose();super.paintComponent(graphics);}
    }
    /** Shared 24-unit outline system: rounded ends, consistent optical size and real gear teeth. */
    static void icon(Graphics2D original,String name,int x,int y,int size){
        Graphics2D g=(Graphics2D)original.create();g.translate(x,y);g.scale(size/24.0,size/24.0);g.setStroke(new BasicStroke(1.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(name){
            case "sun"->{g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(new Ellipse2D.Double(8.6,8.6,6.8,6.8));for(int i=0;i<8;i++){double a=i*Math.PI/4;g.draw(new Line2D.Double(12+8*Math.cos(a),12+8*Math.sin(a),12+10.5*Math.cos(a),12+10.5*Math.sin(a)));}}
            case "moon"->{Path2D p=new Path2D.Double();p.moveTo(20.5,14);p.curveTo(18.7,20.3,10.7,23,5.7,17.4);p.curveTo(.8,11.8,4.3,4.5,10.1,3.2);p.curveTo(6.6,8.6,11.8,16.9,20.5,14);p.closePath();g.draw(p);}
            case "system"->{g.draw(new RoundRectangle2D.Double(3,4,18,13,2.5,2.5));line(g,9,21,15,21);line(g,12,17,12,21);}
            case "settings"->{Path2D gear=new Path2D.Double();for(int i=0;i<8;i++)for(int j=0;j<4;j++){double a=(i+j/4.0)*Math.PI/4-Math.PI/16;double r=(j==1||j==2)?10:7.7;double px=12+r*Math.cos(a),py=12+r*Math.sin(a);if(i==0&&j==0)gear.moveTo(px,py);else gear.lineTo(px,py);}gear.closePath();g.draw(gear);g.draw(new Ellipse2D.Double(8.5,8.5,7,7));}
            case "play"->{Path2D p=new Path2D.Double();p.moveTo(8,4.5);p.lineTo(20,12);p.lineTo(8,19.5);p.closePath();g.draw(p);}
            case "pause"->{g.draw(new RoundRectangle2D.Double(6,4.5,4,15,1,1));g.draw(new RoundRectangle2D.Double(15,4.5,4,15,1,1));}
            case "home"->{Path2D p=new Path2D.Double();p.moveTo(3,10.5);p.lineTo(12,3);p.lineTo(21,10.5);p.moveTo(5,9);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,14);p.lineTo(14,14);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,9);g.draw(p);}
            case "user"->{g.draw(new Ellipse2D.Double(8,3,8,8));Path2D p=new Path2D.Double();p.moveTo(4,21);p.curveTo(4,12,20,12,20,21);g.draw(p);}
            case "image"->{g.draw(new RoundRectangle2D.Double(3,3,18,18,4,4));g.draw(new Ellipse2D.Double(7,7,3,3));Path2D p=new Path2D.Double();p.moveTo(4,17);p.lineTo(10,12);p.lineTo(14,16);p.lineTo(18,12);p.lineTo(21,15);g.draw(p);}
            case "upload"->{line(g,12,16,12,3);line(g,7,8,12,3);line(g,17,8,12,3);Path2D p=new Path2D.Double();p.moveTo(4,15);p.lineTo(4,21);p.lineTo(20,21);p.lineTo(20,15);g.draw(p);}
            case "download"->{line(g,12,3,12,16);line(g,7,11,12,16);line(g,17,11,12,16);Path2D p=new Path2D.Double();p.moveTo(4,16);p.lineTo(4,21);p.lineTo(20,21);p.lineTo(20,16);g.draw(p);}
            case "chevron"->{Path2D p=new Path2D.Double();p.moveTo(7,10);p.lineTo(12,15);p.lineTo(17,10);g.draw(p);}
            case "close"->{line(g,6,6,18,18);line(g,18,6,6,18);}
            case "minimize"->line(g,5,12,19,12);
            case "maximize"->g.draw(new RoundRectangle2D.Double(5,5,14,14,1,1));
            case "restore"->{Path2D path=new Path2D.Double();path.moveTo(9,7);path.lineTo(9,4);path.lineTo(20,4);path.lineTo(20,15);path.lineTo(17,15);g.draw(path);g.draw(new RoundRectangle2D.Double(4,9,12,12,1,1));}
            case "plus"->{line(g,12,5,12,19);line(g,5,12,19,12);}
            case "trash"->{line(g,4,6,20,6);Path2D p=new Path2D.Double();p.moveTo(9,6);p.lineTo(9,3);p.lineTo(15,3);p.lineTo(15,6);p.moveTo(6,6);p.lineTo(7,21);p.lineTo(17,21);p.lineTo(18,6);g.draw(p);line(g,10,10,10,17);line(g,14,10,14,17);}
            case "refresh"->{g.draw(new Arc2D.Double(4,4,16,16,40,285,Arc2D.OPEN));Path2D p=new Path2D.Double();p.moveTo(20,4);p.lineTo(20,10);p.lineTo(14,10);g.draw(p);}
            case "folder"->{Path2D p=new Path2D.Double();p.moveTo(3,7);p.lineTo(3,20);p.lineTo(21,20);p.lineTo(21,7);p.lineTo(12,7);p.lineTo(9,4);p.lineTo(3,4);p.closePath();g.draw(p);}
            case "power"->{g.draw(new Arc2D.Double(4,4,16,16,130,280,Arc2D.OPEN));line(g,12,2,12,12);}
            default->{g.draw(new RoundRectangle2D.Double(4,4,16,16,4,4));}
        }g.dispose();
    }
    private static void line(Graphics2D g,double x1,double y1,double x2,double y2){g.draw(new Line2D.Double(x1,y1,x2,y2));}
}
