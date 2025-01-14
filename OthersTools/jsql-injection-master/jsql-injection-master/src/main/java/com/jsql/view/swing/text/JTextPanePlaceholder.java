package com.jsql.view.swing.text;

import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ConcurrentModificationException;

import javax.swing.JTextPane;
import javax.swing.text.DefaultCaret;
import javax.swing.text.DefaultEditorKit;

import org.apache.log4j.Logger;
import org.jsoup.Jsoup;

import com.jsql.view.swing.HelperUi;
import com.jsql.view.swing.text.action.DeletePrevCharAction;

/**
 * Textfield with information text displayed when empty.
 */
@SuppressWarnings("serial")
public class JTextPanePlaceholder extends JTextPane implements InterfaceTextPlaceholder {
	
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = Logger.getRootLogger();
    
    /**
     * Text to display when empty.
     */
    private String placeholderText = "";
    
    /**
     * Create a textfield with hint and default value.
     * @param placeholder Text displayed when empty
     * @param value Default value
     */
    public JTextPanePlaceholder(String placeholder, String value) {
        this(placeholder);
        this.setText(value);
    }
    
    /**
     * Create a textfield with hint.
     * @param placeholder Text displayed when empty
     */
    public JTextPanePlaceholder(String placeholder) {
        this.placeholderText = placeholder;
        
        this.setCaret(new DefaultCaret() {
            @Override
            public void setSelectionVisible(boolean visible) {
                super.setSelectionVisible(true);
            }
        });
        
        this.addFocusListener(new FocusListener() {
            
            @Override
            public void focusLost(FocusEvent e) {
                JTextPanePlaceholder.this.setSelectionColor(HelperUi.COLOR_FOCUS_LOST);
                JTextPanePlaceholder.this.revalidate();
                JTextPanePlaceholder.this.repaint();
            }
            
            @Override
            public void focusGained(FocusEvent e) {
                JTextPanePlaceholder.this.setSelectionColor(HelperUi.COLOR_FOCUS_GAINED);
                JTextPanePlaceholder.this.revalidate();
                JTextPanePlaceholder.this.repaint();
            }
            
        });
        
        this.getActionMap().put(DefaultEditorKit.deletePrevCharAction, new DeletePrevCharAction());
        this.getActionMap().put(DefaultEditorKit.deleteNextCharAction, new DeletePrevCharAction());
    }

    @Override
    public void paint(Graphics g) {
        // Fix #4012: ArrayIndexOutOfBoundsException on paint()
        // Fix #38546: ConcurrentModificationException on getText()
        // Fix #37872: IndexOutOfBoundsException on getText()
        try {
            super.paint(g);
            if ("".equals(Jsoup.parse(this.getText()).text().trim())) {
                this.drawPlaceholder(this, g, this.placeholderText);
            }
        } catch (ConcurrentModificationException | IndexOutOfBoundsException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
    
}