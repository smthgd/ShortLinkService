package org.example.infra;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import org.example.core.BrowserOpener;

public final class DesktopBrowser implements BrowserOpener {
    @Override
    public void open(URI destination) throws IOException {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("Открытие браузера недоступно.");
        }
        Desktop.getDesktop().browse(destination);
    }
}
