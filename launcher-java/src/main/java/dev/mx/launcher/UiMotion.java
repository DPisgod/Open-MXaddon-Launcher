package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.image.BufferedImage;

/** Short software-painted transitions. Native windows remain opaque and keep fixed bounds. */
final class UiMotion {
    private UiMotion(){}
    /** Pair the Windows timer-resolution request with active animations, never idle windows. */
    static final class FrameTimer extends Timer {
        private boolean clock;
        FrameTimer(int delay,java.awt.event.ActionListener listener){super(delay,listener);setCoalesce(true);}
        @Override public void start(){if(!clock){FineClock.acquire();clock=true;}super.start();}
        @Override public void stop(){super.stop();if(clock){clock=false;FineClock.release();}}
    }
    private static final class FineClock {
        private static int users;private static boolean requested;
        private interface WinMM extends com.sun.jna.win32.StdCallLibrary {int timeBeginPeriod(int period);int timeEndPeriod(int period);}
        private static WinMM api;
        static synchronized void acquire(){if(users++!=0||!System.getProperty("os.name","").startsWith("Windows"))return;try{if(api==null)api=com.sun.jna.Native.load("winmm",WinMM.class);requested=api.timeBeginPeriod(1)==0;}catch(Throwable ignored){requested=false;}}
        static synchronized void release(){if(users<=0)return;if(--users==0&&requested){try{api.timeEndPeriod(1);}finally{requested=false;}}}
    }
    static boolean visible(Component component){Window window=SwingUtilities.getWindowAncestor(component);return component.isShowing()&&!(window instanceof Frame frame&&(frame.getExtendedState()&Frame.ICONIFIED)!=0);}
    static double ease(double value){value=Math.max(0,Math.min(1,value));return 1-Math.pow(1-value,3);}

    static final class Reveal {
        private final JComponent owner;private final Timer timer;private long started;private double value=1;
        Reveal(JComponent owner){this.owner=owner;timer=new FrameTimer(16,e->tick());owner.addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0){if(owner.isShowing())start();else finish();}});}
        private void start(){started=System.nanoTime();value=0;timer.start();owner.repaint();}
        private void tick(){if(!visible(owner)){finish();return;}value=ease((System.nanoTime()-started)/190_000_000.0);if(value>=1)timer.stop();owner.repaint();}
        private void finish(){timer.stop();value=1;owner.repaint();}
        double value(){return value;}
        Graphics2D graphics(Graphics original){Graphics2D g=(Graphics2D)original.create();if(value<1){g.setComposite(AlphaComposite.SrcOver.derive((float)(.25+.75*value)));g.translate(0,(1-value)*10);}return g;}
    }
    static final class Entrance extends JPanel {
        private final Reveal reveal=new Reveal(this);
        Entrance(Container content){super(new BorderLayout());setOpaque(true);add(content,BorderLayout.CENTER);}
        @Override protected void paintComponent(Graphics g){g.setColor(UiTheme.p().background());g.fillRect(0,0,getWidth(),getHeight());}
        @Override protected void paintChildren(Graphics original){Graphics2D g=reveal.graphics(original);super.paintChildren(g);g.dispose();}
    }
    static Container entrance(Container content){return content instanceof Entrance?content:new Entrance(content);}

    static final class Pages extends JPanel {
        private BufferedImage outgoing;private long started;private double amount=1;private final Timer timer=new FrameTimer(16,e->tick());
        Pages(CardLayout layout){super(layout);setOpaque(false);timer.setCoalesce(true);addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0&&!isShowing())finish();});}
        void transition(Runnable change){transition(change,false);}
        void transition(Runnable change,boolean contentChanged){
            Component previous=selected();BufferedImage before=null;
            if(visible(this)&&getWidth()>0&&getHeight()>0){before=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_ARGB_PRE);before.setAccelerationPriority(0);Graphics2D g=before.createGraphics();UiTheme.quality(g);paint(g);g.dispose();}
            change.run();revalidate();doLayout();
            if(before==null||previous==selected()&&!contentChanged){if(before!=null)before.flush();return;}
            finish();outgoing=before;amount=0;started=System.nanoTime();timer.start();repaint();
        }
        private Component selected(){for(Component child:getComponents())if(child.isVisible())return child;return null;}
        private void tick(){if(!visible(this)){finish();return;}amount=ease((System.nanoTime()-started)/220_000_000.0);if(amount>=1)finish();else repaint();}
        private void finish(){timer.stop();amount=1;if(outgoing!=null){outgoing.flush();outgoing=null;}repaint();}
        boolean animating(){return outgoing!=null;}
        @Override public void removeNotify(){finish();super.removeNotify();}
        @Override protected void paintChildren(Graphics original){
            if(outgoing==null){super.paintChildren(original);return;}
            Graphics2D g=(Graphics2D)original.create();UiTheme.quality(g);g.setComposite(AlphaComposite.SrcOver.derive((float)(1-amount)));g.drawImage(outgoing,(int)Math.round(-10*amount),0,null);g.dispose();
            g=(Graphics2D)original.create();g.setComposite(AlphaComposite.SrcOver.derive((float)amount));g.translate((1-amount)*14,0);super.paintChildren(g);g.dispose();
        }
    }
}
