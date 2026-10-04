package pl.formularz.template;

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
}
