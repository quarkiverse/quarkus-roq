package io.quarkiverse.roq.data.runtime.writer;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.CollectionNode;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A YAML document which is updated through its SnakeYAML node tree: the merged data is applied on the parsed nodes,
 * which keep their comments and scalar styles, and the tree is dumped again.
 * <p>
 * Comments, key order and scalar styles are kept. The layout is normalized by SnakeYAML (indentation is detected from
 * the original content, but e.g. some blank lines or the position of comments around list items may move).
 */
final class YamlDocument {

    private static final String LF = LineBreak.UNIX.getString();
    private static final String CRLF = LineBreak.WIN.getString();
    private static final String MERGE_KEY = "<<";
    private static final Pattern KEY_LINE = Pattern.compile("^( *)[^-#\\s].*:\\s*(#.*)?$");
    private static final Pattern DASH_ONLY_LINE = Pattern.compile("^( *)- *(#.*)?$");
    private static final Pattern SEQUENCE_LINE = Pattern.compile("^( *)- ");

    private static final Resolver RESOLVER = new Resolver();

    private final JsonNodeFactory factory = JsonNodeFactory.instance;
    private final ObjectMapper mapper;
    // SnakeYAML constructors and representers are stateful, so they are per document
    private final ScalarConstructor constructor = new ScalarConstructor();
    private final Representer representer = new Representer(new DumperOptions());
    private final Node root;
    private final JsonNode data;
    private final Style style;

    private YamlDocument(ObjectMapper mapper, Node root, Style style) {
        this.mapper = mapper;
        this.root = root;
        this.data = root == null ? null : toData(root);
        this.style = style;
    }

    static YamlDocument parse(ObjectMapper mapper, String content) {
        if (content == null || content.isBlank()) {
            return new YamlDocument(mapper, null, Style.DEFAULT);
        }
        final LoaderOptions options = new LoaderOptions();
        options.setProcessComments(true);
        final Node root = new Yaml(new SafeConstructor(options)).compose(new StringReader(content));
        return new YamlDocument(mapper, root, Style.detect(content));
    }

    /**
     * @return the content as a data tree, {@code null} if the document is empty
     */
    JsonNode data() {
        return data;
    }

    /**
     * @return the content updated with the given (merged) data
     */
    String write(JsonNode merged) {
        final Node updated = root == null ? create(merged, null) : update(root, data, merged);
        return lineBreaks(commentsBeforeDash(dump(updated)) + LF);
    }

    // ---- Updating the nodes ----

    private Node update(Node node, JsonNode original, JsonNode merged) {
        if (TreeMerger.same(original, merged)) {
            return node;
        }
        // an emptied collection is replaced (by {} or [])
        if (node instanceof MappingNode mapping && original.isObject() && merged.isObject() && !merged.isEmpty()) {
            updateMapping(mapping, original, merged);
            return mapping;
        }
        if (node instanceof SequenceNode sequence && original.isArray() && merged.isArray() && !merged.isEmpty()) {
            updateSequence(sequence, original, merged);
            return sequence;
        }
        final Node created = create(merged, node);
        created.setBlockComments(node.getBlockComments());
        created.setInLineComments(node.getInLineComments());
        created.setEndComments(node.getEndComments());
        return created;
    }

    private void updateMapping(MappingNode mapping, JsonNode original, JsonNode merged) {
        final Map<String, NodeTuple> existing = new java.util.HashMap<>();
        final List<NodeTuple> result = new ArrayList<>();
        for (NodeTuple tuple : mapping.getValue()) {
            final String key = scalarKey(tuple.getKeyNode());
            if (key == null || MERGE_KEY.equals(key)) {
                // complex or merge keys are not handled, keep them
                result.add(tuple);
            } else {
                existing.put(key, tuple);
            }
        }
        // merged keeps the original keys in their order, with the new keys in between
        for (Iterator<Map.Entry<String, JsonNode>> it = merged.fields(); it.hasNext();) {
            final Map.Entry<String, JsonNode> entry = it.next();
            final NodeTuple tuple = existing.get(entry.getKey());
            result.add(tuple == null
                    ? new NodeTuple(keyNode(entry.getKey()), create(entry.getValue(), null))
                    : new NodeTuple(tuple.getKeyNode(),
                            update(tuple.getValueNode(), original.get(entry.getKey()), entry.getValue())));
        }
        // the comments above a removed first entry (e.g. the file header) move to the new first one
        final Node dropped = mapping.getValue().get(0).getKeyNode();
        final String droppedKey = scalarKey(dropped);
        if (droppedKey != null && !merged.has(droppedKey) && dropped.getBlockComments() != null
                && !result.isEmpty()) {
            final Node first = result.get(0).getKeyNode();
            final List<org.yaml.snakeyaml.comments.CommentLine> comments = new ArrayList<>(dropped.getBlockComments());
            if (first.getBlockComments() != null) {
                comments.addAll(first.getBlockComments());
            }
            first.setBlockComments(comments);
        }
        mapping.setValue(result);
    }

    private void updateSequence(SequenceNode sequence, JsonNode original, JsonNode merged) {
        final List<Node> items = sequence.getValue();
        final int[] alignment = Alignment.align(original.size(), merged.size(),
                (i, j) -> TreeMerger.same(original.get(i), merged.get(j)),
                (i, j) -> TreeMerger.similarity(original.get(i), merged.get(j)));
        // new items are styled like their siblings
        final Node template = items.get(0);
        final List<Node> result = new ArrayList<>();
        for (int j = 0; j < merged.size(); j++) {
            final int i = alignment[j];
            result.add(i < 0 ? create(merged.get(j), template)
                    : update(items.get(i), original.get(i), merged.get(j)));
        }
        items.clear();
        items.addAll(result);
    }

    // ---- Writing new values ----

    /**
     * @param template an existing node to copy the style from (the original value or a sibling list item), or
     *        {@code null}
     */
    private Node create(JsonNode value, Node template) {
        final boolean flow = template instanceof CollectionNode<?> collection
                && collection.getFlowStyle() == DumperOptions.FlowStyle.FLOW;
        if (value.isObject()) {
            final List<NodeTuple> tuples = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = value.fields(); it.hasNext();) {
                final Map.Entry<String, JsonNode> entry = it.next();
                tuples.add(new NodeTuple(keyNode(entry.getKey()),
                        create(entry.getValue(), templateValue(template, entry.getKey()))));
            }
            return new MappingNode(Tag.MAP, tuples,
                    flow || tuples.isEmpty() ? DumperOptions.FlowStyle.FLOW : DumperOptions.FlowStyle.BLOCK);
        }
        if (value.isArray()) {
            final List<Node> items = new ArrayList<>();
            final Node itemTemplate = template instanceof SequenceNode sequence && !sequence.getValue().isEmpty()
                    ? sequence.getValue().get(0)
                    : null;
            for (JsonNode item : value) {
                items.add(create(item, itemTemplate));
            }
            return new SequenceNode(Tag.SEQ, items,
                    flow || items.isEmpty() ? DumperOptions.FlowStyle.FLOW : DumperOptions.FlowStyle.BLOCK);
        }
        if (value.isNull() || value.isBoolean() || value.isNumber()) {
            return representer
                    .represent(value.isNull() ? null : value.isBoolean() ? value.booleanValue() : value.numberValue());
        }
        final String text = value.asText();
        if (template instanceof ScalarNode scalar && Tag.STR.equals(scalar.getTag())
                && scalar.getScalarStyle() != DumperOptions.ScalarStyle.PLAIN) {
            // keep the quoting or block style (| or >) of the original value or of a sibling
            return new ScalarNode(Tag.STR, text, null, null, scalar.getScalarStyle());
        }
        if (text.contains(LF)) {
            return new ScalarNode(Tag.STR, text, null, null, DumperOptions.ScalarStyle.LITERAL);
        }
        if (Tag.TIMESTAMP.equals(RESOLVER.resolve(NodeId.scalar, text, true))) {
            // Jackson reads a plain timestamp as its text, no need to quote it
            return scalar(Tag.TIMESTAMP, text);
        }
        return scalar(Tag.STR, text);
    }

    private static Node templateValue(Node template, String key) {
        if (template instanceof MappingNode mapping) {
            for (NodeTuple tuple : mapping.getValue()) {
                if (key.equals(scalarKey(tuple.getKeyNode()))) {
                    return tuple.getValueNode();
                }
            }
        }
        return null;
    }

    /**
     * @return the node written in the document style, without trailing line break
     */
    private String dump(Node node) {
        final DumperOptions options = new DumperOptions();
        options.setProcessComments(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(style.indent());
        if (style.indicatorIndent() > 0) {
            options.setIndicatorIndent(style.indicatorIndent());
            options.setIndentWithIndicator(true);
        }
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        options.setAllowUnicode(true);
        final StringWriter writer = new StringWriter();
        new Yaml(options).serialize(node, writer);
        return writer.toString().stripTrailing();
    }

    /**
     * SnakeYAML writes the comments of a list item after its {@code -} indicator, with the item on the following
     * lines. Parsers like YAML::Tiny (po4a) choke on that, so the comments are moved back above the indicator and the
     * first entry of the item goes back on the line of the indicator.
     */
    private static String commentsBeforeDash(String text) {
        final List<String> lines = text.lines().toList();
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final Matcher dash = DASH_ONLY_LINE.matcher(lines.get(i));
            final int indent = dash.matches() ? dash.group(1).length() : -1;
            int entry = i + 1;
            while (indent >= 0 && entry < lines.size() && (lines.get(entry).isBlank()
                    || lines.get(entry).stripLeading().startsWith("#"))) {
                entry++;
            }
            if (indent < 0 || entry >= lines.size()
                    || lines.get(entry).length() - lines.get(entry).stripLeading().length() <= indent) {
                result.add(lines.get(i));
                continue;
            }
            final String prefix = " ".repeat(indent);
            if (dash.group(2) != null) {
                result.add(prefix + dash.group(2));
            }
            for (int c = i + 1; c < entry; c++) {
                result.add(lines.get(c).isBlank() ? "" : prefix + lines.get(c).strip());
            }
            result.add(prefix + "- " + lines.get(entry).stripLeading());
            i = entry;
        }
        return String.join(LF, result);
    }

    private String lineBreaks(String text) {
        return style.lineBreak() == LineBreak.UNIX ? text : text.replace(LF, style.lineBreak().getString());
    }

    private static ScalarNode scalar(Tag tag, String value) {
        return new ScalarNode(tag, value, null, null, DumperOptions.ScalarStyle.PLAIN);
    }

    private static ScalarNode keyNode(String key) {
        return scalar(Tag.STR, key);
    }

    private static String scalarKey(Node node) {
        return node instanceof ScalarNode scalar ? scalar.getValue() : null;
    }

    // ---- Reading the data ----

    private JsonNode toData(Node node) {
        if (node instanceof MappingNode mapping) {
            final ObjectNode result = factory.objectNode();
            for (NodeTuple tuple : mapping.getValue()) {
                final String key = scalarKey(tuple.getKeyNode());
                if (key != null && !MERGE_KEY.equals(key)) {
                    result.set(key, toData(tuple.getValueNode()));
                }
            }
            return result;
        }
        if (node instanceof SequenceNode sequence) {
            final ArrayNode result = factory.arrayNode();
            for (Node item : sequence.getValue()) {
                result.add(toData(item));
            }
            return result;
        }
        final ScalarNode scalar = (ScalarNode) node;
        try {
            return mapper.valueToTree(constructor.construct(scalar));
        } catch (RuntimeException e) {
            // e.g. a custom tag
            return factory.textNode(scalar.getValue());
        }
    }

    /**
     * Constructs scalars like SnakeYAML does (YAML 1.1 booleans, hex/octal/sexagesimal ints, .inf, ...), except
     * timestamps which are kept as text, like Jackson reads them.
     */
    private static final class ScalarConstructor extends SafeConstructor {

        ScalarConstructor() {
            super(new LoaderOptions());
            this.yamlConstructors.put(Tag.TIMESTAMP, new ConstructYamlStr());
        }

        Object construct(ScalarNode node) {
            return constructObject(node);
        }
    }

    /**
     * The indentation used for new content, and the line break.
     */
    record Style(int indent, int indicatorIndent, LineBreak lineBreak) {

        static final Style DEFAULT = new Style(2, 0, LineBreak.UNIX);

        static Style detect(String content) {
            int indent = Integer.MAX_VALUE;
            int indicatorIndent = 0;
            String previousKeyIndent = null;
            for (String line : content.lines().toList()) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                final int leading = line.length() - line.stripLeading().length();
                if (leading > 0 && line.charAt(0) == ' ') {
                    indent = Math.min(indent, leading);
                }
                final Matcher sequence = SEQUENCE_LINE.matcher(line);
                if (sequence.find() && previousKeyIndent != null && indicatorIndent == 0) {
                    indicatorIndent = Math.max(0, sequence.group(1).length() - previousKeyIndent.length());
                }
                final Matcher key = KEY_LINE.matcher(line);
                previousKeyIndent = key.matches() ? key.group(1) : null;
            }
            if (indent == Integer.MAX_VALUE) {
                indent = DEFAULT.indent;
            }
            indent = Math.max(1, Math.min(10, indent));
            indicatorIndent = Math.min(indicatorIndent, indent);
            return new Style(indent, indicatorIndent, content.contains(CRLF) ? LineBreak.WIN : LineBreak.UNIX);
        }
    }
}
