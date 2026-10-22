package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Wheel easing shared by page, list and dialog scroll panes. Thumb/keyboard changes stay immediate. */
final class SmoothScroll {
    private static final String KEY="oml.smoothScroll";
    private final JScrollPane pane;
    private final javax.swing.Timer timer=new UiMotion.FrameTimer(16,e->tick());
    private JScrollBar active;
    private double position,target;
    private long last;
    private boolean writing;
    static void install(JScrollPane pane){if(pane.getClientProperty(KEY)==null)pane.putClientProperty(KEY,new SmoothScroll(pane));}
    private SmoothScroll(JScrollPane pane){
        this.pane=pane;pane.setWheelScrollingEnabled(false);pane.addMouseWheelListener(this::wheel);
        AdjustmentListener external=e->{if(!writing&&e.getSource()==active)stop();};
        pane.getVerticalScrollBar().addAdjustmentListener(external);pane.getHorizontalScrollBar().addAdjustmentListener(external);
        pane.addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0&&!pane.isShowing())stop();});
    }
    private void wheel(MouseWheelEvent e){
        if(e.isConsumed()||!pane.isEnabled()||!pane.isShowing()||e.getPreciseWheelRotation()==0)return;
        JScrollBar bar=e.isShiftDown()?pane.getHorizontalScrollBar():pane.getVerticalScrollBar();
        if(!bar.isVisible()||bar.getMaximum()-bar.getVisibleAmount()<=bar.getMinimum())bar=pane.getHorizontalScrollBar();
        int direction=e.getPreciseWheelRotation()>0?1:-1;
        double base=timer.isRunning()&&active==bar?target:bar.getValue();
        int increment=e.getScrollType()==MouseWheelEvent.WHEEL_BLOCK_SCROLL?bar.getBlockIncrement(direction):bar.getUnitIncrement(direction);
        // Plain JPanel views default to a one-pixel increment; give those pages a readable step.
        if(e.getScrollType()==MouseWheelEvent.WHEEL_UNIT_SCROLL&&increment==1&&!(pane.getViewport().getView() instanceof Scrollable))increment=24;
        double next=clamp(bar,base+e.getPreciseWheelRotation()*Math.max(1,increment)*(e.getScrollType()==MouseWheelEvent.WHEEL_BLOCK_SCROLL?1:e.getScrollAmount()));
        if(next==base){forward(e);return;}
        if(!timer.isRunning()||active!=bar){stop();active=bar;position=bar.getValue();last=System.nanoTime();}
        target=next;timer.start();e.consume();
    }
    private void tick(){
        if(!UiMotion.visible(pane)||active==null){stop();return;}
        long now=System.nanoTime();double dt=Math.max(0,(now-last)/1_000_000.0);last=now;
        target=clamp(active,target);position+=(target-position)*(1-Math.exp(-dt/45.0));
        boolean done=Math.abs(target-position)<.35;if(done)position=target;
        writing=true;try{active.setValue((int)Math.round(position));}finally{writing=false;}
        if(done)stop();
    }
    private static double clamp(JScrollBar bar,double value){return Math.max(bar.getMinimum(),Math.min(bar.getMaximum()-bar.getVisibleAmount(),value));}
    private void stop(){timer.stop();active=null;}
    private void forward(MouseWheelEvent e){
        Container parent=SwingUtilities.getAncestorOfClass(JScrollPane.class,pane.getParent());
        if(!(parent instanceof JScrollPane outer))return;
        Point p=SwingUtilities.convertPoint(pane,e.getPoint(),outer);
        MouseWheelEvent forwarded=new MouseWheelEvent(outer,e.getID(),e.getWhen(),e.getModifiersEx(),p.x,p.y,e.getXOnScreen(),e.getYOnScreen(),e.getClickCount(),e.isPopupTrigger(),e.getScrollType(),e.getScrollAmount(),e.getWheelRotation(),e.getPreciseWheelRotation());
        outer.dispatchEvent(forwarded);if(forwarded.isConsumed())e.consume();
    }
}
