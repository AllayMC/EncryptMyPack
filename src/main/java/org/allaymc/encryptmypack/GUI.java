package org.allaymc.encryptmypack;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;
import jnafilechooser.api.JnaFileChooser;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author daoge_cmd
 */
public final class GUI {
    private JPanel rootPanel;
    private JScrollPane scrollPane;
    private ConsolePanel consolePanel;
    private JTextField keyTextField;
    private JButton generateKeyButton;
    private JTextField filePathTextField;
    private JButton chooseFileButton;
    private JButton encryptButton;
    private JButton decryptButton;

    public GUI() {
        $$$setupUI$$$();
        wrapSystemOutputStreams();
        JFrame frame = new JFrame("EncryptMyPack by @daoge_cmd");
        frame.setContentPane(rootPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 700);
        frame.setLocationRelativeTo(null);
        keyTextField.setText(PackEncryptor.generateRandomKey());

        // Set icon
        URL image = GUI.class.getClassLoader().getResource("icon.png");
        if (image != null) {
            frame.setIconImage(new ImageIcon(image).getImage());
        }

        // Add action listeners
        generateKeyButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                var newKey = PackEncryptor.generateRandomKey();
                keyTextField.setText(newKey);
            }
        });

        chooseFileButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                JnaFileChooser fc = new JnaFileChooser();
                fc.addFilter("All Files", "*");
                fc.addFilter("Resource Pack Files", "zip", "mcpack");
                if (fc.showOpenDialog(frame)) {
                    File f = fc.getSelectedFile();
                    filePathTextField.setText(f.toPath().toAbsolutePath().toString());
                }
            }
        });

        encryptButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                String key = keyTextField.getText();
                Path path = Path.of(filePathTextField.getText());
                PackEncryptor.encrypt(path, appendToFileName(path, "_encrypted"), key);
            }
        });

        decryptButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                String key = keyTextField.getText();
                Path path = Path.of(filePathTextField.getText());
                PackEncryptor.decrypt(path, appendToFileName(path, "_decrypted"), key);
            }
        });

        // Show the frame
        frame.setVisible(true);
    }

    private static Path appendToFileName(Path path, String suffix) {
        Path parent = path.getParent();
        String fileName = path.getFileName().toString();

        int dotIndex = fileName.lastIndexOf('.');
        String name;
        String extension;

        if (dotIndex == -1) {
            name = fileName;
            extension = "";
        } else {
            name = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        String newFileName = name + suffix + extension;
        return (parent != null) ? parent.resolve(newFileName) : Paths.get(newFileName);
    }

    private void wrapSystemOutputStreams() {
        var proxyOutputStream = createProxyOutputStream();
        // Override the system output streams
        System.setOut(new PrintStream(proxyOutputStream, true));
        System.setErr(new PrintStream(proxyOutputStream, true));
    }

    private OutputStream createProxyOutputStream() {
        var originalOutputStream = System.out;
        return new OutputStream() {
            @Override
            public void write(int i) {
                originalOutputStream.write(i);
                appendTextToConsole(String.valueOf((char) i));
            }

            @Override
            public void write(byte[] b) {
                write(b, 0, b.length);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                originalOutputStream.write(b, off, len);
                appendTextToConsole(new String(b, off, len));
            }
        };
    }

    public void appendTextToConsole(final String text) {
        SwingUtilities.invokeLater(() -> {
            consolePanel.appendANSI(text);
            Document doc = consolePanel.getDocument();
            consolePanel.setCaretPosition(doc.getLength());
        });
    }

    private void createUIComponents() {
        // Init the console
        consolePanel = new ConsolePanel();
        consolePanel.setBackground(new Color(0x131313));
        consolePanel.setEditable(false);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridBagLayout());
        rootPanel.setPreferredSize(new Dimension(700, 700));
        scrollPane = new JScrollPane();
        GridBagConstraints gbc;
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        rootPanel.add(scrollPane, gbc);
        scrollPane.setViewportView(consolePanel);
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        rootPanel.add(panel1, gbc);
        keyTextField = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel1.add(keyTextField, gbc);
        generateKeyButton = new JButton();
        generateKeyButton.setText("GenKey");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel1.add(generateKeyButton, gbc);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        rootPanel.add(panel2, gbc);
        filePathTextField = new JTextField();
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(filePathTextField, gbc);
        chooseFileButton = new JButton();
        chooseFileButton.setText("Choose");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel2.add(chooseFileButton, gbc);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.fill = GridBagConstraints.BOTH;
        rootPanel.add(panel3, gbc);
        encryptButton = new JButton();
        encryptButton.setText("Encrypt");
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(encryptButton, gbc);
        decryptButton = new JButton();
        decryptButton.setActionCommand("Button");
        decryptButton.setText("Decrypt");
        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel3.add(decryptButton, gbc);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }

}
