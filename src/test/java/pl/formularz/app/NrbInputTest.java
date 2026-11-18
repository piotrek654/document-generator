package pl.formularz.app;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class NrbInputTest {

    @BeforeAll
    static void startJavaFx() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "JavaFX wymaga środowiska graficznego");
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX was already started.
        } catch (UnsupportedOperationException e) {
            assumeFalse(true, "Nie można uruchomić JavaFX: " + e.getMessage());
        }
    }

    @Test
    void formatsPastedAndTypedNumbersAndSupportsEditing() throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                TextField input = new TextField();
                input.setTextFormatter(new TextFormatter<String>(FormApp::formatNrbChange));

                input.setText("61109010140000071219812874");
                assertThat(input.getText()).isEqualTo("61 1090 1014 0000 0712 1981 2874");
                input.positionCaret(3);
                input.deletePreviousChar();
                assertThat(input.getText()).isEqualTo("61 0901 0140 0000 7121 9812 874");
                assertThat(input.getCaretPosition()).isEqualTo(1);

                input.setText("611090");
                input.positionCaret(2);
                input.deleteNextChar();
                assertThat(input.getText()).isEqualTo("61 090");
                input.positionCaret(3);
                input.insertText(3, "1");
                assertThat(input.getText()).isEqualTo("61 1090");
                assertThat(input.getCaretPosition()).isEqualTo(4);

                input.selectRange(2, 3);
                input.deletePreviousChar();
                assertThat(input.getText()).isEqualTo("61 1090");

                input.clear();
                input.appendText("6");
                input.appendText("1");
                input.appendText("1");
                assertThat(input.getText()).isEqualTo("61 1");

                input.setText("PL611090");
                assertThat(input.getText()).isEqualTo("PL611090");
                result.complete(null);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        result.get(10, TimeUnit.SECONDS);
    }
}