package io.quarkus.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Pattern;

/**
 * Converts Jekyll layout HTML to Roq layout HTML.
 * Primarily handles replacing /assets/css/ stylesheet links with {#bundle /} tag.
 */
public class JekyllLayoutConverter {

    private static final Pattern ASSETS_CSS_PATTERN = Pattern.compile(".*href=.*['\"].*?/assets/css/.*");

    /**
     * Replace the first /assets/css/ stylesheet link with {#bundle /} and remove
     * any additional /assets/css/ links.
     *
     * This positions the web-bundler tag where Jekyll's main CSS was located,
     * preserving the CSS load order while removing redundant references.
     *
     * Preserves:
     * - Non-assets CSS (e.g., /guides/stylesheet/config.css)
     * - External CDN CSS (e.g., https://use.fontawesome.com/...)
     * - All other HTML
     *
     * @param content HTML content to convert
     * @return Converted HTML with bundle tag replacing /assets/css/ links
     * @throws IOException if reading the content fails
     */
    public String replaceAssetsCssWithBundle(String content) throws IOException {
        StringWriter output = new StringWriter();
        boolean bundleTagInserted = false;

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (ASSETS_CSS_PATTERN.matcher(line).matches()) {
                    if (!bundleTagInserted) {
                        // Replace first /assets/css/ link with {#bundle /}
                        // Preserve indentation from the original line
                        String indentation = line.substring(0, line.indexOf('<'));
                        output.write(indentation + "{#bundle /}\n");
                        bundleTagInserted = true;
                    }
                    // Skip this line (first replacement already done, or additional link to remove)
                } else {
                    output.write(line);
                    output.write('\n');
                }
            }
        }

        return output.toString();
    }
}
