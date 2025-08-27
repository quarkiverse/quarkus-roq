package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class TemplateDebugPrinter {

    private TemplateDebugPrinter() {
    }

    public static String buildTreeString(List<RoqFrontMatterRawTemplateBuildItem> templates) {
        TreeNode root = buildTree(templates);
        return buildString(root, "", "");
    }

    private static TreeNode buildTree(List<RoqFrontMatterRawTemplateBuildItem> templates) {
        Map<String, TreeNode> children = new TreeMap<>();

        for (RoqFrontMatterRawTemplateBuildItem item : templates) {
            String[] segments = item.templateSource().path().split("/");
            String type = item.type().name();
            children = insert(children, segments, 0, type, item.attachments());
        }

        return new TreeNode("", null, Collections.unmodifiableMap(children));
    }

    private static Map<String, TreeNode> insert(Map<String, TreeNode> nodes, String[] segments, int index,
            String type, List<RoqFrontMatterRawTemplateBuildItem.Attachment> attachments) {
        Map<String, TreeNode> updated = new TreeMap<>(nodes);
        String name = segments[index];
        boolean isLeaf = index == segments.length - 1;

        if (isLeaf) {
            Map<String, TreeNode> attMap = new TreeMap<>();
            if (attachments != null) {
                for (RoqFrontMatterRawTemplateBuildItem.Attachment att : attachments) {
                    attMap.put(att.name(),
                            new TreeNode(att.name(), "ATTACHMENT", Collections.emptyMap()));
                }
            }
            updated.put(name, new TreeNode(name, type, Collections.unmodifiableMap(attMap)));
        } else {
            TreeNode child = nodes.getOrDefault(name, new TreeNode(name, null, new TreeMap<>()));
            Map<String, TreeNode> newChildren = insert(child.children(), segments, index + 1, type, attachments);
            updated.put(name, new TreeNode(name, null, newChildren));
        }

        return Collections.unmodifiableMap(updated);
    }

    private static final int NAME_COLUMN_WIDTH = 70; // adjust as needed

    private static String buildString(TreeNode node, String indent, String prefix) {
        StringBuilder sb = new StringBuilder();

        if (!node.name().isEmpty()) {
            String typeLabel = node.type() != null ? node.type() : "";
            String namePart = indent + prefix + node.name();
            // pad spaces to reach NAME_COLUMN_WIDTH
            int spaces = Math.max(1, NAME_COLUMN_WIDTH - namePart.length());
            sb.append(namePart)
                    .append(" ".repeat(spaces))
                    .append(typeLabel)
                    .append("\n");

            indent += "    ";
        }

        // Sort children: folders first, then index/layout/theme_layout/pages/attachments
        List<TreeNode> sortedChildren = new ArrayList<>(node.children().values());
        sortedChildren.sort(Comparator
                .comparingInt(TemplateDebugPrinter::priority)
                .thenComparing(TreeNode::name, String.CASE_INSENSITIVE_ORDER));

        int size = sortedChildren.size();
        for (int i = 0; i < size; i++) {
            TreeNode child = sortedChildren.get(i);
            String childPrefix = (i == size - 1) ? "└── " : "├── ";
            sb.append(buildString(child, indent, childPrefix));
        }

        return sb.toString();
    }

    private static int priority(TreeNode node) {
        if ("layouts".equals(node.name) || "theme-layouts".equals(node.name)) {
            return 5;
        }

        if (node.type == null)
            return 3; // dir
        if (node.name().startsWith("index.")) {
            return 0;
        }
        return switch (node.type) {
            case "DOCUMENT_PAGE" -> 2;
            case "NORMAL_PAGE" -> 1;
            default -> 4;
        };
    }

    public record TreeNode(
            String name,
            String type,
            Map<String, TreeNode> children) {
    }
}
