package pl.formularz.app;

import javafx.application.Application;

/**
 * Entry point of the packaged jar/exe. It must not extend {@link Application}: when the main class does,
 * the JVM refuses to start with JavaFX on the class path ("JavaFX runtime components are missing").
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(FormApp.class, args);
    }
}
