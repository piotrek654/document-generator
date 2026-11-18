package pl.formularz.app;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SicknessApplicationCheckboxTest {

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
    void addsUncheckedCheckboxAfterFormFields() throws Exception {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                GridPane grid = new GridPane();
                Label firstField = new Label("Pierwsze pole");
                Label lastField = new Label("Ostatnie pole");
                grid.add(firstField, 0, 0);
                grid.add(lastField, 0, 1);

                new FormApp().addSicknessApplicationCheckbox(grid, 2);

                Label label = (Label) grid.getChildren().get(2);
                CheckBox checkbox = (CheckBox) grid.getChildren().get(3);
                assertThat(GridPane.getRowIndex(label)).isEqualTo(2);
                assertThat(GridPane.getColumnIndex(label)).isZero();
                assertThat(label.getText()).isEqualTo("Dołącz wniosek o dobrowolne chorobowe");
                assertThat(label.isWrapText()).isTrue();
                assertThat(label.getMaxWidth()).isEqualTo(Double.MAX_VALUE);
                assertThat(label.getLabelFor()).isSameAs(checkbox);
                assertThat(GridPane.getRowIndex(checkbox)).isEqualTo(2);
                assertThat(GridPane.getColumnIndex(checkbox)).isEqualTo(1);
                assertThat(checkbox.getText()).isEmpty();
                assertThat(checkbox.isSelected()).isFalse();
                checkbox.setSelected(true);
                assertThat(checkbox.isSelected()).isTrue();
                result.complete(null);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        result.get(10, TimeUnit.SECONDS);
    }
}