/*******************************************************************************
 * Copyhacked (H) 2012-2016.
 * This program and the accompanying materials
 * are made available under no term at all, use it like
 * you want, but share and discuss about it
 * every time possible with every body.
 * 
 * Contributors:
 *      ron190 at ymail dot com - initial implementation
 ******************************************************************************/
package com.jsql.view.swing.tab;

import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.jsql.view.swing.HelperUi;
import com.jsql.view.swing.MediatorGui;
import com.jsql.view.swing.action.ActionCloseTabResult;

/**
 * Panel displayed as a header for tabs.
 */
@SuppressWarnings("serial")
public class TabHeader extends JPanel implements MouseListener {
    
    private JLabel tabTitleLabel = new JLabel();
	
    /**
     * Tab header with default tab icon.
     */
    public TabHeader() {
        this(HelperUi.ICON_TABLE);
    }

    /**
     * Tab header with a custom icon.
     */
    public TabHeader(Icon imageIcon) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));

        this.setOpaque(false);

        // Set the text of tab
        this.tabTitleLabel = new JLabel();
        this.tabTitleLabel.setIcon(imageIcon);
        this.add(this.tabTitleLabel);

        JButton tabCloseButton = new ButtonClose();
        tabCloseButton.addMouseListener(this);

        this.add(tabCloseButton);
    }

    public TabHeader(String label, Icon imageIcon) {
        this(imageIcon);
        this.tabTitleLabel.setText(label);
    }
    
    public TabHeader(String label) {
        this();
        this.tabTitleLabel.setText(label);
    }

    /**
     * Action for close button: remove tab.
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
            return;
        }
        int closeTabNumber = MediatorGui.tabResults().indexOfTabComponent(TabHeader.this);
        
        ActionCloseTabResult.perform(closeTabNumber);
    }

    @Override public void mouseEntered(MouseEvent e) {
        // Do nothing
    }
    
    @Override public void mouseExited(MouseEvent e) {
        // Do nothing
    }
    
    @Override public void mousePressed(MouseEvent e) {
        // Do nothing
    }
    
    @Override public void mouseReleased(MouseEvent e) {
        // Do nothing
    }
    
}
