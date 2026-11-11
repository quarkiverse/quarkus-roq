package io.quarkiverse.roq.data.runtime.writer;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.Mark;
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
 * A YAML document which is updated by patching its text: only the parts of the file whose data changed are rewritten,
 * everything else (comments, blank lines, indentation, quoting, folded text...) is kept as-is.
 * <p>
 * Changed values are replaced in place, removed entries are deleted with their own comments (the comment lines right
 * above them), and new entries are written by SnakeYAML and indented like their siblings.
 */
final class YamlDocument implements DataDocument {

    private static final String LF = LineBreak.UNIX.getString();
    private static final String CRLF = LineBreak.WIN.getString();
    private static final String MERGE_KEY = "<<";
    private static final String FRAGMENT_KEY = "k";
    private static final Pattern KEY_LINE = Pattern.compile("^( *)[^-#\\s].*:\\s*(#.*)?$");
    private static final Pattern SEQUENCE_LINE = Pattern.compile("^( *)- ");

    private static final Resolver RESOLVER = new Resolver();

    private final JsonNodeFactory factory = JsonNodeFactory.instance;
    private final ObjectMapper mapper;
    // SnakeYAML constructors and representers are stateful, so they are per document
    private final ScalarConstructor constructor = new ScalarConstructor();
    private final Representer representer = new Representer(new DumperOptions());
    private final String content;
    private final int[] lineStarts;
    private final Node root;
    private final JsonNode data;
    private final Style style;

    private YamlDocument(ObjectMapper mapper, String content, Node root, Style style) {
        this.mapper = mapper;
        this.content = content;
        this.lineStarts = lineStarts(content);
        this.root = root;
        this.data = root == null ? null : toData(root);
        this.style = style;
    }

    static YamlDocument parse(ObjectMapper mapper, String content) {
        if (content == null || content.isBlank()) {
            return new YamlDocument(mapper, "", null, Style.DEFAULT);
        }
        final Node root = new Yaml(new SafeConstructor(new LoaderOptions())).compose(new StringReader(content));
        return new YamlDocument(mapper, content, root, Style.detect(content));
    }

    @Override
    public JsonNode data() {
        return data;
    }

    @Override
    public String write(JsonNode merged) {
        if (root != null) {
            try {
                final List<Edit> edits = new ArrayList<>();
                patch(root, data, merged, null, edits);
                return apply(edits);
            } catch (NotPatchable e) {
                // e.g. the root changed from a mapping to a sequence, write the whole document
            }
        }
        return lineBreaks(dump(create(merged, root)) + LF);
    }

    // ---- Patching ----

    private void patch(Node node, JsonNode original, JsonNode merged, Slot slot, List<Edit> edits) {
        if (TreeMerger.same(original, merged)) {
            return;
        }
        // an emptied collection is replaced (by {} or [])
        if (node instanceof MappingNode mapping && isBlock(mapping) && original.isObject() && merged.isObject()
                && !merged.isEmpty()) {
            patchMapping(mapping, original, merged, slot, edits);
        } else if (node instanceof SequenceNode sequence && isBlock(sequence) && original.isArray()
                && merged.isArray() && !merged.isEmpty()) {
            patchSequence(sequence, original, merged, edits);
        } else {
            replace(node, merged, slot, edits);
        }
    }

    private void patchMapping(MappingNode mapping, JsonNode original, JsonNode merged, Slot slot,
            List<Edit> edits) {
        // merged keeps the original keys in their order, with the new keys in between
        final List<NodeTuple> tuples = mapping.getValue();
        final Node firstKey = tuples.get(0).getKeyNode();
        if (!lineText(firstKey.getStartMark().getLine()).substring(0, firstKey.getStartMark().getColumn()).isBlank()
                && !scalarKey(firstKey).equals(merged.fieldNames().next())) {
            // the first key is on the line of a "- " indicator, it can't be removed or preceded by line edits
            replace(mapping, merged, slot, edits);
            return;
        }
        final int column = tuples.get(0).getKeyNode().getStartMark().getColumn();
        int cursor = start(entryRegion(tuples.get(0)));
        int next = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = merged.fields(); it.hasNext();) {
            final Map.Entry<String, JsonNode> entry = it.next();
            final String key = entry.getKey();
            if (!original.has(key)) {
                final String text = indent(dump(entry(key, create(entry.getValue(), null))), column);
                edits.add(insert(cursor, text));
                continue;
            }
            for (; next < tuples.size(); next++) {
                final NodeTuple tuple = tuples.get(next);
                final Region region = entryRegion(tuple);
                if (key.equals(scalarKey(tuple.getKeyNode()))) {
                    patch(tuple.getValueNode(), original.get(key), entry.getValue(), entrySlot(tuple), edits);
                    cursor = end(region);
                    next++;
                    break;
                }
                cursor = removeIfDropped(tuple, region, next == 0, merged, edits);
            }
        }
        for (; next < tuples.size(); next++) {
            removeIfDropped(tuples.get(next), entryRegion(tuples.get(next)), next == 0, merged, edits);
        }
    }

    private int removeIfDropped(NodeTuple tuple, Region region, boolean first, JsonNode merged, List<Edit> edits) {
        final String key = scalarKey(tuple.getKeyNode());
        if (key == null || MERGE_KEY.equals(key) || merged.has(key)) {
            // complex or merge keys are not handled, keep them
            return end(region);
        }
        return delete(region, first, edits);
    }

    private void patchSequence(SequenceNode sequence, JsonNode original, JsonNode merged, List<Edit> edits) {
        final List<Node> items = sequence.getValue();
        final int[] alignment = Alignment.align(original.size(), merged.size(),
                (i, j) -> TreeMerger.same(original.get(i), merged.get(j)),
                (i, j) -> TreeMerger.similarity(original.get(i), merged.get(j)));
        // new items are styled like their siblings
        final Node template = items.get(0);
        final int dashColumn = column(dash(template));
        final boolean separated = isBlankLineSeparated(items);
        int cursor = start(itemRegion(template));
        // whether the cursor is at the start of an item (blank line separator above), or at the end of one
        boolean atItemStart = true;
        int next = 0;
        for (int j = 0; j < merged.size(); j++) {
            final int i = alignment[j];
            if (i < 0) {
                String text = indent(itemText(create(merged.get(j), template)), dashColumn);
                if (separated) {
                    text = atItemStart ? text + LF : LF + text;
                }
                edits.add(insert(cursor, text));
                continue;
            }
            // original items skipped by the alignment are removed
            for (; next < i; next++) {
                cursor = delete(itemRegion(items.get(next)), next == 0, edits);
                atItemStart = true;
            }
            final Node item = items.get(i);
            patch(item, original.get(i), merged.get(j), itemSlot(item), edits);
            cursor = end(itemRegion(item));
            atItemStart = false;
            next = i + 1;
        }
        for (; next < items.size(); next++) {
            delete(itemRegion(items.get(next)), next == 0, edits);
        }
    }

    /**
     * Replace a value by a new one, styled like it.
     */
    private void replace(Node node, JsonNode merged, Slot slot, List<Edit> edits) {
        if (slot == null) {
            throw NotPatchable.INSTANCE;
        }
        final Node created = create(merged, node);
        final String text = slot.item() ? itemText(created).substring(1)
                : dump(entry(FRAGMENT_KEY, created)).substring(FRAGMENT_KEY.length() + 1);
        final String fragment = indentContinuation(text, slot.indent());
        if (isInline(node) && !fragment.contains(LF)) {
            // keep the spacing and comments around the value
            edits.add(new Edit(offset(node.getStartMark()), offset(node.getEndMark()), lineBreaks(fragment.substring(1))));
        } else {
            edits.add(new Edit(slot.afterIndicator(), contentEnd(lastContentLine(node)), lineBreaks(fragment)));
        }
    }

    /**
     * Delete an entry, and the blank lines after it when it's the first one or separated from the previous one (to keep
     * a single separator).
     *
     * @return the offset after the deleted text
     */
    private int delete(Region region, boolean first, List<Edit> edits) {
        int lastLine = region.lastLine();
        if (first || isBlank(region.firstLine() - 1)) {
            while (isBlank(lastLine + 1)) {
                lastLine++;
            }
        }
        final int end = end(new Region(region.firstLine(), lastLine));
        edits.add(new Edit(start(region), end, ""));
        return end;
    }

    private Edit insert(int offset, String text) {
        if (offset == content.length() && !content.endsWith(LF)) {
            // the file has no trailing line break
            text = LF + text.substring(0, text.length() - LF.length());
        }
        return new Edit(offset, offset, lineBreaks(text));
    }

    private String apply(List<Edit> edits) {
        // from the end of the file: deletions before insertions at the same offset, and insertions at the same offset
        // in reverse order, so that they end up in order
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < edits.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> edits.get(i).start()).reversed()
                .thenComparing(Comparator.comparingInt((Integer i) -> edits.get(i).end() - edits.get(i).start())
                        .reversed())
                .thenComparing(Comparator.reverseOrder()));
        final StringBuilder result = new StringBuilder(content);
        for (int i : order) {
            final Edit edit = edits.get(i);
            result.replace(edit.start(), edit.end(), edit.text());
        }
        return result.toString();
    }

    // ---- Regions and positions ----

    private Region entryRegion(NodeTuple tuple) {
        return withOwnComments(tuple.getKeyNode().getStartMark().getLine(), lastContentLine(tuple.getValueNode()));
    }

    private Region itemRegion(Node item) {
        return withOwnComments(line(dash(item)), lastContentLine(item));
    }

    /**
     * The comment lines right above an entry (no blank line in between) belong to it.
     */
    private Region withOwnComments(int firstLine, int lastLine) {
        int start = firstLine;
        while (start > 0 && isComment(start - 1)) {
            start--;
        }
        return new Region(start, lastLine);
    }

    private Slot entrySlot(NodeTuple tuple) {
        int i = offset(tuple.getKeyNode().getEndMark());
        while (i < content.length() && content.charAt(i) == ' ') {
            i++;
        }
        if (i >= content.length() || content.charAt(i) != ':') {
            throw NotPatchable.INSTANCE;
        }
        return new Slot(i + 1, tuple.getKeyNode().getStartMark().getColumn(), false);
    }

    private Slot itemSlot(Node item) {
        final int dash = dash(item);
        return new Slot(dash + 1, column(dash), true);
    }

    /**
     * @return the offset of the {@code -} indicator of a sequence item
     */
    private int dash(Node item) {
        int i = offset(item.getStartMark()) - 1;
        while (i >= 0 && Character.isWhitespace(content.charAt(i))) {
            i--;
        }
        if (i < 0 || content.charAt(i) != '-') {
            throw NotPatchable.INSTANCE;
        }
        return i;
    }

    /**
     * The end mark of a block node is the start of the next token, the last content line is before it (blank and
     * comment lines in between belong to what follows).
     */
    private int lastContentLine(Node node) {
        final Mark end = node.getEndMark();
        if (isInline(node)) {
            return end.getLine();
        }
        final boolean blockScalar = node instanceof ScalarNode;
        final int startLine = node.getStartMark().getLine();
        for (int line = Math.min(end.getLine(), lineStarts.length - 1); line > startLine; line--) {
            String text = lineText(line);
            if (line == end.getLine()) {
                text = text.substring(0, Math.min(text.length(), offset(end) - lineStarts[line]));
            }
            if (!text.isBlank() && (blockScalar || !text.strip().startsWith("#"))) {
                return line;
            }
        }
        return startLine;
    }

    private static boolean isInline(Node node) {
        if (node instanceof ScalarNode scalar) {
            return scalar.getScalarStyle() != DumperOptions.ScalarStyle.LITERAL
                    && scalar.getScalarStyle() != DumperOptions.ScalarStyle.FOLDED;
        }
        return !isBlock(node);
    }

    private static boolean isBlock(Node node) {
        return node instanceof CollectionNode<?> collection
                && collection.getFlowStyle() == DumperOptions.FlowStyle.BLOCK;
    }

    /**
     * Whether all items but the first are preceded by a blank line.
     */
    private boolean isBlankLineSeparated(List<Node> items) {
        if (items.size() < 2) {
            return false;
        }
        for (int i = 1; i < items.size(); i++) {
            final int firstLine = itemRegion(items.get(i)).firstLine();
            if (firstLine == 0 || !isBlank(firstLine - 1)) {
                return false;
            }
        }
        return true;
    }

    private static int[] lineStarts(String content) {
        final List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = content.indexOf(LF); i >= 0 && i + 1 < content.length(); i = content.indexOf(LF, i + 1)) {
            starts.add(i + 1);
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private int offset(Mark mark) {
        if (mark.getLine() >= lineStarts.length) {
            return content.length();
        }
        // SnakeYAML columns count code points
        final int lineStart = lineStarts[mark.getLine()];
        return content.offsetByCodePoints(lineStart,
                Math.min(mark.getColumn(), content.codePointCount(lineStart, lineEnd(mark.getLine()))));
    }

    private int line(int offset) {
        int line = 0;
        while (line + 1 < lineStarts.length && lineStarts[line + 1] <= offset) {
            line++;
        }
        return line;
    }

    private int column(int offset) {
        return offset - lineStarts[line(offset)];
    }

    private int lineEnd(int line) {
        return line + 1 < lineStarts.length ? lineStarts[line + 1] : content.length();
    }

    /**
     * @return the end of the line content, before its line break
     */
    private int contentEnd(int line) {
        final int end = lineEnd(line);
        if (end - lineStarts[line] >= CRLF.length() && content.startsWith(CRLF, end - CRLF.length())) {
            return end - CRLF.length();
        }
        if (end > lineStarts[line] && content.startsWith(LF, end - LF.length())) {
            return end - LF.length();
        }
        return end;
    }

    private String lineText(int line) {
        return content.substring(lineStarts[line], contentEnd(line));
    }

    private boolean isBlank(int line) {
        return line >= 0 && line < lineStarts.length && lineText(line).isBlank();
    }

    private boolean isComment(int line) {
        return lineText(line).strip().startsWith("#");
    }

    private int start(Region region) {
        return lineStarts[region.firstLine()];
    }

    private int end(Region region) {
        return lineEnd(region.lastLine());
    }

    /**
     * Lines of the document, both inclusive.
     */
    private record Region(int firstLine, int lastLine) {
    }

    /**
     * Where the value of an entry or of an item is: after the {@code :} or {@code -} indicator, with continuation lines
     * indented from the column of the key or of the indicator.
     */
    private record Slot(int afterIndicator, int indent, boolean item) {
    }

    private record Edit(int start, int end, String text) {
    }

    private static final class NotPatchable extends RuntimeException {
        static final NotPatchable INSTANCE = new NotPatchable();

        private NotPatchable() {
            super(null, null, false, false);
        }
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

    private static MappingNode entry(String key, Node value) {
        return new MappingNode(Tag.MAP, List.of(new NodeTuple(keyNode(key), value)), DumperOptions.FlowStyle.BLOCK);
    }

    /**
     * @return the text of a sequence item, starting with its {@code -} indicator, written in the document style
     */
    private String itemText(Node item) {
        final String text = dump(entry(FRAGMENT_KEY,
                new SequenceNode(Tag.SEQ, List.of(item), DumperOptions.FlowStyle.BLOCK)));
        final String lines = text.substring(text.indexOf(LF) + LF.length());
        final int indent = lines.length() - lines.stripLeading().length();
        return lines.lines().map(line -> line.length() >= indent ? line.substring(indent) : line.strip())
                .collect(Collectors.joining(LF));
    }

    /**
     * @return the node written in the document style, without trailing line break
     */
    private String dump(Node node) {
        final DumperOptions options = new DumperOptions();
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
     * Indent all the lines, and add a trailing line break.
     */
    private static String indent(String text, int indent) {
        final String prefix = " ".repeat(indent);
        return text.lines().map(line -> line.isEmpty() ? line : prefix + line)
                .collect(Collectors.joining(LF, "", LF));
    }

    private static String indentContinuation(String text, int indent) {
        final String prefix = " ".repeat(indent);
        final String[] lines = text.split(LF, -1);
        final StringBuilder result = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            result.append(LF).append(lines[i].isEmpty() ? "" : prefix + lines[i]);
        }
        return result.toString();
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
