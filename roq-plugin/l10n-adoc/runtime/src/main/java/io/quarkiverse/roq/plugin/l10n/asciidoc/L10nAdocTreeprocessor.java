package io.quarkiverse.roq.plugin.l10n.asciidoc;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.asciidoctor.ast.*;
import org.asciidoctor.extension.Treeprocessor;
import org.jboss.logging.Logger;

class L10nAdocTreeprocessor extends Treeprocessor {

    private static final Logger LOG = Logger.getLogger(L10nAdocTreeprocessor.class);

    private final Path poBaseDir;
    private final boolean extractOnBuild;
    private final Map<Path, L10nAdocPoFile> poFileCache = new ConcurrentHashMap<>();

    L10nAdocTreeprocessor(Path poBaseDir, boolean extractOnBuild) {
        this.poBaseDir = poBaseDir;
        this.extractOnBuild = extractOnBuild;
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

        Path resolvedPoPath;
        if (poFilePath.isPresent()) {
            resolvedPoPath = poFilePath.get();
        } else if (extractOnBuild) {
            resolvedPoPath = poBaseDir.resolve(
                    Paths.get(rootDir).relativize(Paths.get(baseDir).resolve(docNameObj + ".adoc")) + ".po");
        } else {
            LOG.debugf("No PO file found for %s — skipping L10N", docNameObj);
            return document;
        }

        List<Path> touchedPoPaths = new ArrayList<>();
        touchedPoPaths.add(resolvedPoPath);

        L10nAdocPoFile poFile = loadOrCreatePoFile(resolvedPoPath);
        if (poFile == null) {
            return document;
        }

        translateTitle(document, poFile);
        processNodes(document.getBlocks(), poFile, rootDir, touchedPoPaths);

        if (extractOnBuild) {
            flushTouchedPoFiles(touchedPoPaths);
        }

        return document;
    }

    private void flushTouchedPoFiles(List<Path> paths) {
        for (Path path : paths) {
            L10nAdocPoFile pf = poFileCache.get(path);
            if (pf != null && pf.hasChanges()) {
                try {
                    pf.writeTo(path);
                    LOG.infof("Updated PO file: %s", path);
                } catch (IOException e) {
                    LOG.warnf(e, "Failed to write PO file: %s", path);
                }
            }
        }
    }

    private L10nAdocPoFile loadOrCreatePoFile(Path path) {
        return poFileCache.computeIfAbsent(path, p -> {
            if (java.nio.file.Files.exists(p)) {
                try {
                    return new L10nAdocPoFile(p);
                } catch (IOException e) {
                    LOG.warnf(e, "Failed to parse PO file: %s", p);
                    return null;
                }
            } else if (extractOnBuild) {
                return new L10nAdocPoFile();
            }
            return null;
        });
    }

    private Path resolvePoPathFromSourceLocation(StructuralNode node, String rootDir) {
        Cursor location = node.getSourceLocation();
        if (location == null || location.getFile() == null || rootDir == null) {
            return null;
        }
        Path rootPath = Paths.get(rootDir);
        Path sourceFile = Paths.get(location.getDir()).resolve(location.getFile()).normalize();
        if (!sourceFile.startsWith(rootPath)) {
            return null;
        }
        Path relativeSource = rootPath.relativize(sourceFile);
        return poBaseDir.resolve(relativeSource + ".po");
    }

    private L10nAdocPoFile resolvePoFileFromSourceLocation(StructuralNode node, String rootDir) {
        Path poPath = resolvePoPathFromSourceLocation(node, rootDir);
        if (poPath == null) {
            return null;
        }
        if (!java.nio.file.Files.exists(poPath) && !extractOnBuild) {
            LOG.debugf("No PO file for included source: %s", poPath);
            return null;
        }
        return loadOrCreatePoFile(poPath);
    }

    private String translateAndExtract(L10nAdocPoFile poFile, String msgid) {
        if (msgid == null) {
            return null;
        }
        if (extractOnBuild) {
            poFile.addEntry(msgid);
        }
        return poFile.translate(msgid);
    }

    private void translateTitle(Document document, L10nAdocPoFile poFile) {
        String title = document.getDoctitle();
        String translated = translateAndExtract(poFile, title);
        if (translated != null) {
            document.setAttribute("doctitle", translated, true);
        }
    }

    private void processNodes(List<StructuralNode> nodes, L10nAdocPoFile poFile, String rootDir,
            List<Path> touchedPoPaths) {
        for (StructuralNode node : nodes) {
            processNode(node, poFile, rootDir, touchedPoPaths);
        }
    }

    private void processNode(StructuralNode node, L10nAdocPoFile poFile, String rootDir,
            List<Path> touchedPoPaths) {
        if (node instanceof Section section) {
            translateSection(section, poFile, rootDir, touchedPoPaths);
        } else if (node instanceof Table table) {
            translateTable(table, poFile);
        } else if (node instanceof DescriptionList dlist) {
            translateDescriptionList(dlist, poFile, rootDir, touchedPoPaths);
        } else if (node instanceof org.asciidoctor.ast.List list) {
            translateList(list, poFile, rootDir, touchedPoPaths);
        } else if (node instanceof Block block) {
            translateBlock(block, poFile, rootDir, touchedPoPaths);
        }
    }

    private void translateSection(Section section, L10nAdocPoFile poFile, String rootDir,
            List<Path> touchedPoPaths) {
        L10nAdocPoFile resolved = resolvePoFileFromSourceLocation(section, rootDir);
        L10nAdocPoFile effectivePoFile = resolved != null ? resolved : poFile;
        if (resolved != null) {
            Path poPath = resolvePoPathFromSourceLocation(section, rootDir);
            if (poPath != null && !touchedPoPaths.contains(poPath)) {
                touchedPoPaths.add(poPath);
            }
        }

        String originalId = section.getId();
        String title = section.getTitle();
        if (title != null) {
            String translated = translateAndExtract(effectivePoFile, title);
            if (translated != null) {
                section.setTitle(translated);
                if (originalId != null) {
                    section.setId(originalId);
                }
            }
        }
        processNodes(section.getBlocks(), effectivePoFile, rootDir, touchedPoPaths);
    }

    private void translateBlock(Block block, L10nAdocPoFile poFile, String rootDir, List<Path> touchedPoPaths) {
        String context = block.getContext();
        if ("listing".equals(context) || "literal".equals(context)
                || "pass".equals(context) || "stem".equals(context)) {
            return;
        }

        translateBlockTitle(block, poFile);

        if ("paragraph".equals(context) || "quote".equals(context) || "verse".equals(context)) {
            String source = block.getSource();
            if (source != null) {
                String translated = translateAndExtract(poFile, source);
                if (translated != null) {
                    block.setSource(translated);
                }
            }
        }

        processNodes(block.getBlocks(), poFile, rootDir, touchedPoPaths);
    }

    private void translateList(org.asciidoctor.ast.List list, L10nAdocPoFile poFile, String rootDir,
            List<Path> touchedPoPaths) {
        translateBlockTitle(list, poFile);
        for (StructuralNode item : list.getItems()) {
            if (item instanceof ListItem listItem) {
                String source = listItem.getSource();
                if (source != null) {
                    String translated = translateAndExtract(poFile, source);
                    if (translated != null) {
                        listItem.setSource(translated);
                    }
                }
                processNodes(listItem.getBlocks(), poFile, rootDir, touchedPoPaths);
            }
        }
    }

    private void translateDescriptionList(DescriptionList dlist, L10nAdocPoFile poFile, String rootDir,
            List<Path> touchedPoPaths) {
        translateBlockTitle(dlist, poFile);
        for (DescriptionListEntry entry : dlist.getItems()) {
            for (ListItem term : entry.getTerms()) {
                String source = term.getSource();
                if (source != null) {
                    String translated = translateAndExtract(poFile, source);
                    if (translated != null) {
                        term.setSource(translated);
                    }
                }
            }
            ListItem description = entry.getDescription();
            if (description != null) {
                String source = description.getSource();
                if (source != null) {
                    String translated = translateAndExtract(poFile, source);
                    if (translated != null) {
                        description.setSource(translated);
                    }
                }
                processNodes(description.getBlocks(), poFile, rootDir, touchedPoPaths);
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
                    String translated = translateAndExtract(poFile, source);
                    if (translated != null) {
                        cell.setSource(translated);
                    }
                }
                Document innerDoc = cell.getInnerDocument();
                if (innerDoc != null) {
                    processNodes(innerDoc.getBlocks(), poFile, null, new ArrayList<>());
                }
            }
        }
    }

    private void translateBlockTitle(StructuralNode node, L10nAdocPoFile poFile) {
        String title = node.getTitle();
        if (title != null) {
            String translated = translateAndExtract(poFile, title);
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
