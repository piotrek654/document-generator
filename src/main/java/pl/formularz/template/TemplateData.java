package pl.formularz.template;

import java.util.Locale;
import java.util.Map;

/**
 * Values for a template: {@code texts} replace {@code {name}} placeholders,
 * {@code flags} drive {@code {#skresl:name}...{/skresl}} blocks (false = strike through).
 */
public record TemplateData(Map<String, String> texts, Map<String, Boolean> flags) {

    public TemplateData {
        texts = Map.copyOf(texts);
        flags = Map.copyOf(flags);
    }

    public String resolveText(String name) {
        String val = texts.get(name);
        if (val != null) {
            return val;
        }
        String normalized = normalizeKey(name);
        val = texts.get(normalized);
        if (val != null) {
            return val;
        }
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            if (normalizeKey(entry.getKey()).equalsIgnoreCase(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Boolean resolveFlag(String name) {
        Boolean val = flags.get(name);
        if (val != null) {
            return val;
        }
        String normalized = normalizeKey(name);
        val = flags.get(normalized);
        if (val != null) {
            return val;
        }
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (normalizeKey(entry.getKey()).equalsIgnoreCase(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalizeKey(String key) {
        return key.replace('ą', 'a')
                  .replace('ć', 'c')
                  .replace('ę', 'e')
                  .replace('ł', 'l')
                  .replace('ń', 'n')
                  .replace('ó', 'o')
                  .replace('ś', 's')
                  .replace('ź', 'z')
                  .replace('ż', 'z')
                  .replace('Ą', 'a')
                  .replace('Ć', 'c')
                  .replace('Ę', 'e')
                  .replace('Ł', 'l')
                  .replace('Ń', 'n')
                  .replace('Ó', 'o')
                  .replace('Ś', 's')
                  .replace('Ź', 'z')
                  .replace('Ż', 'z')
                  .toLowerCase(Locale.ROOT);
    }
}
