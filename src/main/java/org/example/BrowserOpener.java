package org.example;

import java.io.IOException;
import java.net.URI;

@FunctionalInterface
public interface BrowserOpener {
    void open(URI destination) throws IOException;
}
