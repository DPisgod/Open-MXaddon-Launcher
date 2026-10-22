package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;

public final class SmoothScrollTest {
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static void edt(Runnable work)throws Exception{SwingUtilities.invokeAndWait(work);}
    static void wheel(JScrollPane pane,double rotation,int modifiers){pane.dispatchEvent(new MouseWheelEvent(pane,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),modifiers,50,50,50,50,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,(int)Math.signum(rotation),rotation));}
    static int value(JScrollPane pane)throws Exception{int[] result={0};edt(()->result[0]=pane.getVerticalScrollBar().getValue());return result[0];}
    static boolean running(JScrollPane pane)throws Exception{Object scroll=pane.getClientProperty("oml.smoothScroll");Field field=scroll.getClass().getDeclaredField("timer");field.setAccessible(true);return ((Timer)field.get(scroll)).isRunning();}
    public static void main(String[] args)throws Exception{
        JFrame[] frame={null};JScrollPane[] pane={null};
        try{
            edt(()->{JPanel view=new JPanel();view.setPreferredSize(new Dimension(1400,4000));pane[0]=new JScrollPane(view);UiTheme.scroll(pane[0]);int count=pane[0].getMouseWheelListeners().length;UiTheme.scroll(pane[0]);check(count==pane[0].getMouseWheelListeners().length,"Duplicate wheel listener");frame[0]=new JFrame();frame[0].setContentPane(pane[0]);frame[0].setSize(400,300);frame[0].setVisible(true);pane[0].getVerticalScrollBar().setUnitIncrement(24);});
            edt(()->{wheel(pane[0],2,0);check(pane[0].getVerticalScrollBar().getValue()==0,"Wheel jumped without easing");});
            Thread.sleep(45);int intermediate=value(pane[0]);check(intermediate>0&&intermediate<144,"Missing intermediate scroll position: "+intermediate);
            edt(()->wheel(pane[0],1,0));Thread.sleep(400);check(value(pane[0])==216,"Repeated wheel movement lost");
            edt(()->{wheel(pane[0],3,0);wheel(pane[0],-4,0);});Thread.sleep(400);check(value(pane[0])==144,"Direction reversal lost");
            edt(()->{wheel(pane[0],4,0);pane[0].getVerticalScrollBar().setValue(20);});Thread.sleep(150);check(value(pane[0])==20,"Animation fought external scrollbar change");
            edt(()->wheel(pane[0],.5,0));Thread.sleep(400);check(value(pane[0])==56,"Precise fractional wheel ignored");
            edt(()->wheel(pane[0],1000,0));Thread.sleep(600);int max=pane[0].getVerticalScrollBar().getMaximum()-pane[0].getVerticalScrollBar().getVisibleAmount();check(value(pane[0])==max,"Bottom boundary overscrolled");
            edt(()->wheel(pane[0],-1000,0));Thread.sleep(600);check(value(pane[0])==0,"Top boundary overscrolled");
            edt(()->{pane[0].getHorizontalScrollBar().setUnitIncrement(20);wheel(pane[0],2,InputEvent.SHIFT_DOWN_MASK);});Thread.sleep(400);edt(()->check(pane[0].getHorizontalScrollBar().getValue()==120,"Shift horizontal scrolling failed"));
            edt(()->{wheel(pane[0],5,0);frame[0].setVisible(false);});Thread.sleep(100);check(!running(pane[0]),"Hidden pane retained animation timer");
            System.out.println("SMOOTH_SCROLL_INTERMEDIATE_CONTINUOUS_REVERSE_EXTERNAL_PRECISE_BOUNDARIES_HORIZONTAL_HIDDEN_NO_DUPLICATES_OK");
        }finally{edt(()->{if(frame[0]!=null)frame[0].dispose();});}
    }
}
