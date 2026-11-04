package io.quarkiverse.roq.migration;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Reads YAML front matter the way Roq does, without depending on Roq's build-time modules.
 */
public final class FrontMatter {

    /**
     * Same as Roq's {@code RoqFrontMatterConstants.FRONTMATTER_PATTERN}.
     */
    private static final Pattern PATTERN = Pattern.compile("^---\\v.*?---(?:\\v|$)", Pattern.DOTALL);

    private FrontMatter() {
    }

    public static boolean isPresent(String content) {
        return PATTERN.matcher(content).find();
    }

    /**
     * @return the content after the front matter
     */
    public static String strip(String content) {
        return PATTERN.matcher(content).replaceFirst("");
    }

    /**
     * @return the front matter at the start of the content, delimiters included, so that
     *         {@code block(content) + strip(content)} is the content; empty if there is none
     */
    public static String block(String content) {
        Matcher matcher = PATTERN.matcher(content);
        return matcher.find() ? matcher.group() : "";
    }

    /**
     * @return the front matter fields, in order; empty if there are none
     */
    public static ObjectNode read(YAMLMapper mapper, String content) throws JsonProcessingException {
        int end = content.indexOf("---", 3);
        String yaml = end == -1 ? "" : content.substring(3, end).trim();
        if (yaml.isBlank()) {
            return mapper.createObjectNode();
        }
        JsonNode node = mapper.readTree(yaml);
        return node instanceof ObjectNode object ? object : mapper.createObjectNode();
    }
}
