package pl.formularz.app;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javafx.application.Application;
import javafx.beans.binding.BooleanBinding;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import pl.formularz.amount.Amount;
import pl.formularz.form.ConditionEvaluator;
import pl.formularz.form.FieldCatalog;
import pl.formularz.form.FieldDefinition;
import pl.formularz.form.FormException;
import pl.formularz.form.FormValues;
import pl.formularz.pdf.LibreOfficePdfConverter;
import pl.formularz.pdf.PdfBoxPdfMerger;
import pl.formularz.template.DocxTemplateRenderer;
import pl.formularz.template.TemplateData;

/**
 * PoC form generated from config/fields.yaml; fills every .docx in data/ and merges them into output/dokumenty.pdf.
 */
public class FormApp extends Application {

    private static final Path TEMPLATES_DIR = Path.of("data");
    private static final Path OUTPUT_PDF = Path.of("output/dokumenty.pdf");
    private static final double GAP = 8;
    private static final double PADDING = 16;
    private static final double LABEL_WIDTH = 300;
    private static final double INPUT_WIDTH = 560;
    private static final double MAX_SCREEN_HEIGHT_SHARE = 0.85;

    private final Map<String, Supplier<Object>> readers = new LinkedHashMap<>();
    private final Map<String, TextField> amountInputs = new LinkedHashMap<>();
    private final Map<String, Node> fieldControls = new LinkedHashMap<>();
    private final Map<String, Label> fieldLabels = new LinkedHashMap<>();
    private final Map<String, CheckBox> checkBoxes = new LinkedHashMap<>();
    private FieldCatalog catalog;
    private LibreOfficePdfConverter converter;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        catalog = GenerateCli.loadCatalog();
        GridPane grid = new GridPane();
        grid.setHgap(GAP);
        grid.setVgap(GAP);
        grid.setPadding(new Insets(PADDING));
        ColumnConstraints labelColumn = new ColumnConstraints(LABEL_WIDTH, LABEL_WIDTH, LABEL_WIDTH);
        labelColumn.setHgrow(Priority.NEVER);
        ColumnConstraints inputColumn = new ColumnConstraints(INPUT_WIDTH, INPUT_WIDTH, Double.MAX_VALUE);
        inputColumn.setHgrow(Priority.ALWAYS);
        inputColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, inputColumn);

        int row = 0;
        for (FieldDefinition field : catalog.fields()) {
            Label label = new Label(field.label());
            label.setWrapText(true);
            label.setMaxWidth(Double.MAX_VALUE);
            fieldLabels.put(field.name(), label);
            grid.addRow(row++, label, inputFor(field));
        }

        setupDependencies();

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        BorderPane root = new BorderPane(scroll);
        root.setBottom(generateBar());

        stage.setTitle("Generator dokumentów (PoC)");
        stage.setScene(new Scene(root));
        stage.show();
        fitToScreen(stage);
    }

    private void setupDependencies() {
        for (FieldDefinition field : catalog.fields()) {
            field.enabledIf().ifPresent(condition -> {
                Node control = fieldControls.get(field.name());
                Label label = fieldLabels.get(field.name());

                boolean negate = ConditionEvaluator.isNegated(condition);
                String sourceName = ConditionEvaluator.getSourceCheckbox(condition);
                CheckBox sourceCheckbox = checkBoxes.get(sourceName);
                if (sourceCheckbox != null && control != null) {
                    javafx.beans.value.ObservableValue<Boolean> disabledBinding = negate
                        ? sourceCheckbox.selectedProperty()
                        : sourceCheckbox.selectedProperty().not();

                    control.disableProperty().bind(disabledBinding);
                    if (label != null) {
                        label.disableProperty().bind(disabledBinding);
                    }

                    if (Boolean.TRUE.equals(disabledBinding.getValue())) {
                        applyDisabledValue(field, control);
                    }

                    disabledBinding.addListener((obs, wasDisabled, isDisabled) -> {
                        if (Boolean.TRUE.equals(isDisabled)) {
                            applyDisabledValue(field, control);
                            control.setStyle("");
                        } else {
                            applyEnabledValue(field, control);
                        }
                    });
                }
            });
        }
    }

    private void applyDisabledValue(FieldDefinition field, Node control) {
        Object def = field.disabledValue().orElse(null);
        switch (control) {
            case TextField tf -> tf.setText(def != null ? def.toString() : "");
            case CheckBox cb -> cb.setSelected(def instanceof Boolean b ? b : (def != null && Boolean.parseBoolean(def.toString())));
            case DatePicker dp -> {
                if (def != null && !def.toString().isBlank()) {
                    try {
                        dp.setValue(LocalDate.parse(def.toString()));
                    } catch (Exception e) {
                        dp.getEditor().setText(def.toString());
                    }
                } else {
                    dp.setValue(null);
                    dp.getEditor().setText("");
                }
            }
            case ComboBox<?> combo -> {
                @SuppressWarnings("unchecked")
                ComboBox<String> cb = (ComboBox<String>) combo;
                cb.setValue(def != null ? def.toString() : null);
            }
            default -> {}
        }
    }

    private void applyEnabledValue(FieldDefinition field, Node control) {
        field.defaultValue().ifPresent(def -> {
            switch (control) {
                case TextField tf -> {
                    if (tf.getText().isEmpty()) {
                        tf.setText(def.toString());
                    }
                }
                case CheckBox cb -> cb.setSelected(def instanceof Boolean b ? b : Boolean.parseBoolean(def.toString()));
                case ComboBox<?> combo -> {
                    @SuppressWarnings("unchecked")
                    ComboBox<String> cb = (ComboBox<String>) combo;
                    if (cb.getValue() == null) {
                        cb.setValue(def.toString());
                    }
                }
                default -> {}
            }
        });
    }

    /** Kept outside the scroll pane, so the button stays visible however long the form is. */
    private HBox generateBar() {
        Button generate = new Button("Generuj PDF");
        generate.setDefaultButton(true);
        generate.setOnAction(event -> generate(generate));
        HBox bar = new HBox(generate);
        bar.setAlignment(Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(PADDING));
        return bar;
    }

    private static void fitToScreen(Stage stage) {
        double maxHeight = Screen.getPrimary().getVisualBounds().getHeight() * MAX_SCREEN_HEIGHT_SHARE;
        if (stage.getHeight() > maxHeight) {
            stage.setHeight(maxHeight);
            stage.centerOnScreen();
        }
    }

    @Override
    public void stop() {
        if (converter != null) {
            converter.close();
        }
    }

    private Node inputFor(FieldDefinition field) {
        Node control = switch (field) {
            case FieldDefinition.Text text -> {
                TextField input = textInput(text.name());
                field.defaultValue().ifPresent(def -> input.setText(def.toString()));
                yield input;
            }
            case FieldDefinition.Date date -> {
                DatePicker input = dateInput(date.name(), field.defaultValue().orElse(null));
                yield input;
            }
            case FieldDefinition.Amount amount -> {
                TextField input = textInput(amount.name());
                field.defaultValue().ifPresent(def -> input.setText(def.toString()));
                amountInputs.put(amount.name(), input);
                yield input;
            }
            case FieldDefinition.Select select -> {
                ComboBox<String> input = new ComboBox<>();
                input.getItems().setAll(select.options());
                input.setMaxWidth(Double.MAX_VALUE);
                field.defaultValue().ifPresent(def -> input.setValue(def.toString()));
                input.valueProperty().addListener((obs, oldV, newV) -> input.setStyle(""));
                readers.put(select.name(), input::getValue);
                yield input;
            }
            case FieldDefinition.Checkbox checkbox -> {
                CheckBox input = new CheckBox();
                boolean defVal = field.defaultValue()
                    .map(v -> v instanceof Boolean b ? b : Boolean.parseBoolean(v.toString()))
                    .orElse(false);
                input.setSelected(defVal);
                checkBoxes.put(checkbox.name(), input);
                readers.put(checkbox.name(), input::isSelected);
                yield input;
            }
            case FieldDefinition.AmountInWords words -> amountInWordsPreview(words);
        };
        fieldControls.put(field.name(), control);
        return control;
    }

    private DatePicker dateInput(String name, Object defaultValue) {
        LocalDate initialDate = LocalDate.now();
        if (defaultValue != null) {
            if (defaultValue instanceof LocalDate ld) {
                initialDate = ld;
            } else {
                try {
                    initialDate = LocalDate.parse(defaultValue.toString());
                } catch (Exception ignored) {
                }
            }
        }
        DatePicker input = new DatePicker(initialDate);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        input.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? formatter.format(date) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                if (string != null && !string.isBlank()) {
                    try {
                        return LocalDate.parse(string.trim(), formatter);
                    } catch (DateTimeParseException e) {
                        return null;
                    }
                }
                return null;
            }
        });
        input.setPromptText("DD-MM-YYYY");
        input.setMaxWidth(Double.MAX_VALUE);
        input.valueProperty().addListener((obs, oldV, newV) -> input.setStyle(""));
        input.getEditor().textProperty().addListener((obs, oldV, newV) -> input.setStyle(""));
        readers.put(name, () -> {
            String editorText = input.getEditor().getText();
            if (editorText == null || editorText.isEmpty()) {
                return "";
            }
            LocalDate val = input.getValue();
            if (val != null && input.getConverter().toString(val).equals(editorText.trim())) {
                return val;
            }
            try {
                LocalDate parsed = input.getConverter().fromString(editorText);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
            return editorText;
        });
        return input;
    }

    private TextField textInput(String name) {
        TextField input = new TextField();
        input.textProperty().addListener((obs, oldV, newV) -> input.setStyle(""));
        readers.put(name, input::getText);
        return input;
    }

    /** Read-only preview refreshed while the source amount is typed; the value itself is computed by FormValues. */
    private TextField amountInWordsPreview(FieldDefinition.AmountInWords words) {
        TextField preview = new TextField();
        preview.setEditable(false);
        preview.setDisable(true);
        TextField source = amountInputs.get(words.source());
        if (source != null) {
            preview.setText(spell(source.getText()));
            source.textProperty().addListener((observable, previous, current) -> preview.setText(spell(current)));
        }
        return preview;
    }

    private static String spell(String amount) {
        if (amount == null || amount.isBlank()) {
            return "";
        }
        try {
            return Amount.parse(amount).inWords();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private void generate(Button button) {
        fieldControls.values().forEach(node -> node.setStyle(""));
        Map<String, Object> input = new LinkedHashMap<>();
        readers.forEach((name, reader) -> input.put(name, reader.get()));
        TemplateData data;
        try {
            data = FormValues.of(catalog, input).toTemplateData();
        } catch (FormException e) {
            for (String fieldName : e.fieldNames()) {
                Node node = fieldControls.get(fieldName);
                if (node != null) {
                    node.setStyle("-fx-border-color: #d32f2f; -fx-border-width: 1.5; -fx-border-radius: 3;");
                }
            }
            show(Alert.AlertType.WARNING, "Popraw formularz", e.getMessage());
            return;
        } catch (Throwable t) {
            show(Alert.AlertType.ERROR, "Nieoczekiwany błąd", t.getMessage() != null ? t.getMessage() : t.toString());
            return;
        }
        button.setDisable(true);
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() {
                if (converter == null) {
                    converter = LibreOfficePdfConverter.start();
                }
                return new DocumentGenerator(converter, new PdfBoxPdfMerger())
                    .generate(TEMPLATES_DIR, data, OUTPUT_PDF);
            }
        };
        task.setOnSucceeded(event -> {
            button.setDisable(false);
            getHostServices().showDocument(task.getValue().toAbsolutePath().toUri().toString());
        });
        task.setOnFailed(event -> {
            button.setDisable(false);
            Throwable ex = task.getException();
            String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : String.valueOf(ex);
            show(Alert.AlertType.ERROR, "Nie udało się wygenerować PDF", msg);
        });
        Thread.ofVirtual().start(task);
    }

    private static void show(Alert.AlertType type, String header, String content) {
        Alert alert = new Alert(type);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.showAndWait();
    }
}
