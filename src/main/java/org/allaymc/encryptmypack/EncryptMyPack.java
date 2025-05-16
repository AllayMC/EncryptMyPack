package org.allaymc.encryptmypack;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;

import javax.swing.*;

/**
 * @author daoge_cmd
 */
public class EncryptMyPack {
    public static void main(String[] args) {
        FlatMacDarkLaf.setup();
        SwingUtilities.invokeLater(GUI::new);
    }
}
