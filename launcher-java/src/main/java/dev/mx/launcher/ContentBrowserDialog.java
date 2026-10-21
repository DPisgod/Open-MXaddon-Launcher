package dev.mx.launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.function.Consumer;

final class ContentBrowserDialog extends JDialog {
    private final ContentBrowserPanel browser;
    ContentBrowserDialog(Window owner,Path root,String selected,Consumer<String> installed){this(owner,root,selected,installed,true);}
    ContentBrowserDialog(Window owner,Path root,String selected,Consumer<String> installed,boolean network){
        super(owner,"资源下载",ModalityType.APPLICATION_MODAL);
        browser=new ContentBrowserPanel(this,root,selected,installed,network);browser.onClose(this::dispose);
        setContentPane(browser);setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){browser.leave();}});
        WindowChrome.dialog(this);setSize(1080,780);setMinimumSize(new Dimension(960,700));setLocationRelativeTo(owner);
    }
    @Override public void dispose(){if(browser!=null)browser.close();super.dispose();}
}