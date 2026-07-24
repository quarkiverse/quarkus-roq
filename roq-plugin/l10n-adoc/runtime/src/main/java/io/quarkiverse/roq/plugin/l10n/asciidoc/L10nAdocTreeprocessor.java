package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.asciidoctor.ast.*;
import org.asciidoctor.extension.Treeprocessor;
import org.jboss.logging.Logger;

class L10nAdocTreeprocessor extends Treeprocessor {

    private static final Logger LOG = Logger.getLogger(L10nAdocTreeprocessor.class);

    private final Path poBaseDir;

    L10nAdocTreeprocessor(Path poBaseDir) {
        this.poBaseDir = poBaseDir;
    }

    @Override
    public Document process(Document document) {
        if (poBaseDir == null) {
            return document;
        }

        String baseDir = optionAsString(document, "base_dir");
        String rootDir = optionAsString(document, "root_dir");
        Object docNameObj = document.getAttribute("docname");

        if (baseDir == null || rootDir == null || docNameObj == null) {
            LOG.debug("Missing base_dir, root_dir, or docname — skipping L10N");
            return document;
        }

        Optional<Path> poFilePath = L10nAdocPoFileResolver.resolve(
                poBaseDir, baseDir, rootDir, docNameObj.toString());

        if (poFilePath.isEmpty()) {
            LOG.debugf("No PO file found for %s — skipping L10N", docNameObj);
            return document;
        }

        L10nAdocPoFile poFile;
        try {
            poFile = new L10nAdocPoFile(poFilePath.get());
        } catch (IOException e) {
            LOG.warnf(e, "Failed to parse PO file: %s", poFilePath.get());
            return document;
        }

        translateTitle(document, poFile);
        processNodes(document.getBlocks(), poFile);

        return document;
    }

    private void translateTitle(Document document, L10nAdocPoFile poFile) {
        String title = document.getDoctitle();
        if (title != null) {
            String translated = poFile.translate(title);
            if (translated != null) {
                document.setAttribute("doctitle", translated, true);
            }
        }
    }

    private void processNodes(List<StructuralNode> nodes, L10nAdocPoFile poFile) {
        for (StructuralNode node : nodes) {
            processNode(node, poFile);
        }
    }

    private void processNode(StructuralNode node, L10nAdocPoFile poFile) {
        if (node instanceof Section section) {
            translateSection(section, poFile);
        } else if (node instanceof Table table) {
            translateTable(table, poFile);
        } else if (node instanceof DescriptionList dlist) {
            translateDescriptionList(dlist, poFile);
        } else if (node instanceof org.asciidoctor.ast.List list) {
            translateList(list, poFile);
        } else if (node instanceof Block block) {
            translateBlock(block, poFile);
        }
    }

    private void translateSection(Section section, L10nAdocPoFile poFile) {
        String originalId = section.getId();
        String title = section.getTitle();
        if (title != null) {
            String translated = poFile.translate(title);
            if (translated != null) {
                section.setTitle(translated);
                if (originalId != null) {
                    section.setId(originalId);
                }
            }
        }
        processNodes(section.getBlocks(), poFile);
    }

    private void translateBlock(Block block, L10nAdocPoFile poFile) {
        String context = block.getContext();
        if ("listing".equals(context) || "literal".equals(context)
                || "pass".equals(context) || "stem".equals(context)) {
            return;
        }

        translateBlockTitle(block, poFile);

        if ("paragraph".equals(context) || "quote".equals(context) || "verse".equals(context)) {
            String source = block.getSource();
            if (source != null) {
                String translated = poFile.translate(source);
                if (translated != null) {
                    block.setSource(translated);
                }
            }
        }

        processNodes(block.getBlocks(), poFile);
    }

    private void translateList(org.asciidoctor.ast.List list, L10nAdocPoFile poFile) {
        translateBlockTitle(list, poFile);
        for (StructuralNode item : list.getItems()) {
            if (item instanceof ListItem listItem) {
                String source = listItem.getSource();
                if (source != null) {
                    String translated = poFile.translate(source);
                    if (translated != null) {
                        listItem.setSource(translated);
                    }
                }
                processNodes(listItem.getBlocks(), poFile);
            }
        }
    }

    private void translateDescriptionList(DescriptionList dlist, L10nAdocPoFile poFile) {
        translateBlockTitle(dlist, poFile);
        for (DescriptionListEntry entry : dlist.getItems()) {
            for (ListItem term : entry.getTerms()) {
                String source = term.getSource();
                if (source != null) {
                    String translated = poFile.translate(source);
                    if (translated != null) {
                        term.setSource(translated);
                    }
                }
            }
            ListItem description = entry.getDescription();
            if (description != null) {
                String source = description.getSource();
                if (source != null) {
                    String translated = poFile.translate(source);
                    if (translated != null) {
                        description.setSource(translated);
                    }
                }
                processNodes(description.getBlocks(), poFile);
            }
        }
    }

    private void translateTable(Table table, L10nAdocPoFile poFile) {
        translateBlockTitle(table, poFile);
        translateTableRows(table.getHeader(), poFile);
        translateTableRows(table.getBody(), poFile);
        translateTableRows(table.getFooter(), poFile);
    }

    private void translateTableRows(java.util.List<Row> rows, L10nAdocPoFile poFile) {
        for (Row row : rows) {
            for (Cell cell : row.getCells()) {
                String source = cell.getSource();
                if (source != null) {
                    String translated = poFile.translate(source);
                    if (translated != null) {
                        cell.setSource(translated);
                    }
                }
                Document innerDoc = cell.getInnerDocument();
                if (innerDoc != null) {
                    processNodes(innerDoc.getBlocks(), poFile);
                }
            }
        }
    }

    private void translateBlockTitle(StructuralNode node, L10nAdocPoFile poFile) {
        String title = node.getTitle();
        if (title != null) {
            String translated = poFile.translate(title);
            if (translated != null) {
                node.setTitle(translated);
            }
        }
    }

    private static String optionAsString(Document document, String key) {
        Object value = document.getOptions().get(key);
        return value != null ? value.toString() : null;
    }

}
