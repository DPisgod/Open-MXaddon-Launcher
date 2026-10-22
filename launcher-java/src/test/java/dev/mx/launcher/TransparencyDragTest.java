package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.Arrays;

/** Exercise the real personalization slider before release, without touching user data. */
public final class TransparencyDragTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void edt(Runnable work)throws Exception{SwingUtilities.invokeAndWait(work);}
    private static void drag(JSlider slider,int x){slider.dispatchEvent(new MouseEvent(slider,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,x,slider.getHeight()/2,0,false,MouseEvent.NOBUTTON));}
    public static void main(String[] args)throws Exception{
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Path home=Files.createTempDirectory("oml-live-transparency-");LauncherPaths.override(home);
        Path preferences=LauncherPaths.dataFile(home,"appearance.properties");
        Appearance initial=new Appearance();initial.mask=0;initial.blur=7;initial.loop=false;initial.save(home);
        LauncherFrame[] frame={null};
        try{
            edt(()->{frame[0]=new LauncherFrame(false);frame[0].setVisible(true);frame[0].showStyles();});
            Thread.sleep(300);
            edt(()->{try{
                for(int preset=0;preset<3;preset++){
                    ((JComboBox<?>)AccountUiBehaviorTest.findNamed(frame[0],"appearanceStyle")).setSelectedIndex(preset);
                    JSlider slider=(JSlider)AccountUiBehaviorTest.findNamed(frame[0],"uiTransparency");slider.setValue(0);
                    byte[] before=Files.readAllBytes(preferences);
                    UiTheme.Surface header=(UiTheme.Surface)AccountUiBehaviorTest.findNamed(frame[0],"mainHeader");
                    int solid=AppearanceControlsTest.render(frame[0].getContentPane()).getRGB(230,8);
                    int x=(int)(slider.getWidth()*.25);MotionAndControlsTest.press(slider,x);
                    for(double fraction:new double[]{.35,.60,.95}){
                        x=(int)(slider.getWidth()*fraction);drag(slider,x);
                        check(slider.getValueIsAdjusting(),"Fixture is not dragging");
                        check(header.transparency==slider.getValue(),"Header opacity does not follow the pointer before release");
                        check(Arrays.equals(before,Files.readAllBytes(preferences)),"Dragging wrote preferences before release");
                    }
                    int preview=AppearanceControlsTest.render(frame[0].getContentPane()).getRGB(230,8);
                    check(AppearanceControlsTest.distance(solid,preview)>20,"Live preview did not change painted pixels");
                    int selected=slider.getValue();MotionAndControlsTest.release(slider,x);
                    Appearance saved=Appearance.load(home);
                    check(saved.uiTransparency==selected,"Release did not persist the final value");
                    check(saved.mask==0&&saved.blur==7&&!saved.loop,"Dragging changed wallpaper settings");
                    frame[0].showStyles();frame[0].validate();
                    check(((JSlider)AccountUiBehaviorTest.findNamed(frame[0],"uiTransparency")).getValue()==selected,"Reopening lost the final value");
                }
                int committed=Appearance.load(home).uiTransparency;
                // Replacing the preferences file with a directory forces a real atomic-save failure.
                Files.delete(preferences);Files.createDirectory(preferences);
                JSlider slider=(JSlider)AccountUiBehaviorTest.findNamed(frame[0],"uiTransparency");
                slider.setValueIsAdjusting(true);slider.setValue(15);
                check(((UiTheme.Surface)AccountUiBehaviorTest.findNamed(frame[0],"mainHeader")).transparency==15,"Failure fixture lacks live preview");
                slider.setValueIsAdjusting(false);
                check(slider.getValue()==committed,"Save failure did not restore slider");
                check(((UiTheme.Surface)AccountUiBehaviorTest.findNamed(frame[0],"mainHeader")).transparency==committed,"Save failure left uncommitted opacity visible");
                Files.delete(preferences);
            }catch(Exception e){throw new RuntimeException(e);}});
            System.out.println("LIVE_POINTER_TRANSPARENCY_THREE_PALETTES_NO_DRAG_WRITES_RELEASE_SAVE_REOPEN_WALLPAPER_INDEPENDENCE_FAILURE_ROLLBACK_OK");
        }finally{edt(()->{if(frame[0]!=null)frame[0].dispose();});}
        System.exit(0);
    }
}
