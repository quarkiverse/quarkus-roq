package io.quarkiverse.roq.cli;

import java.io.InputStream;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.quarkus.cli.common.OutputOptionMixin;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "blog", mixinStandardHelpOptions = true, description = "List blog posts from a Roq site RSS feed")
public class BlogCommand implements Callable<Integer> {

    private static final String DEFAULT_RSS_URL = "https://iamroq.dev/rss.xml";
    private static final DateTimeFormatter RSS_DATE_FORMAT = DateTimeFormatter.ofPattern(
            "EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
    private static final int PAGE_SIZE = 5;

    @CommandLine.Mixin(name = "output")
    OutputOptionMixin output;

    @Option(names = { "-u", "--url" }, defaultValue = DEFAULT_RSS_URL, description = "RSS feed URL")
    private String url;

    @Option(names = { "-n", "--limit" }, defaultValue = "0", description = "Max number of posts to show (0 = all)")
    private int limit;

    @Option(names = { "-a", "--all" }, defaultValue = "false", description = "Show all posts without paging")
    private boolean showAll;

    @Override
    public Integer call() {
        try {
            List<Post> posts = fetchPosts();

            if (posts.isEmpty()) {
                output.info("No posts found.");
                return CommandLine.ExitCode.OK;
            }

            if (limit > 0 && posts.size() > limit) {
                posts = posts.subList(0, limit);
            }

            int maxTitleLen = 0;
            for (Post p : posts) {
                String t = p.title;
                if (!TerminalUtils.supportsOsc8()) {
                    t = stripEmoji(t);
                }
                if (t.length() > maxTitleLen) {
                    maxTitleLen = t.length();
                }
            }
            maxTitleLen = Math.min(maxTitleLen, 60);

            if (showAll) {
                printPosts(posts, 0, posts.size(), maxTitleLen);
            } else {
                int offset = 0;
                while (offset < posts.size()) {
                    int end = Math.min(offset + PAGE_SIZE, posts.size());
                    printPosts(posts, offset, end, maxTitleLen);
                    offset = end;

                    if (offset < posts.size()) {
                        printPager();
                        if (!waitForSpace()) {
                            TerminalUtils.clearLine();
                            break;
                        }
                        TerminalUtils.clearLine();
                    }
                }
            }

            printSummary(posts.size());

            return CommandLine.ExitCode.OK;
        } catch (Exception e) {
            return output.handleCommandException(e, "Unable to fetch blog posts: " + e.getMessage());
        }
    }

    private void printPosts(List<Post> posts, int from, int to, int maxTitleLen) {
        for (int i = from; i < to; i++) {
            Post p = posts.get(i);
            String title = p.title.length() > 60 ? p.title.substring(0, 57) + "..." : p.title;
            String padding = " ".repeat(Math.max(0, maxTitleLen - title.length()));

            String line;
            if (TerminalUtils.supportsOsc8()) {
                // Underline only the title text, padding goes outside the link
                line = "  " + TerminalUtils.faint(p.date) + "  "
                        + TerminalUtils.link(TerminalUtils.bold(title), p.link)
                        + padding;
            } else {
                // Strip emojis for alignment in fallback mode
                String cleanTitle = stripEmoji(title);
                cleanTitle = cleanTitle.length() > 60 ? cleanTitle.substring(0, 57) + "..." : cleanTitle;
                String fallbackPadding = " ".repeat(Math.max(0, maxTitleLen - cleanTitle.length()));
                line = "  " + TerminalUtils.faint(p.date) + "  "
                        + TerminalUtils.bold(cleanTitle) + fallbackPadding + "  "
                        + TerminalUtils.link(p.link);
            }

            System.out.println(line);
        }
        System.out.flush();
    }

    private void printPager() {
        System.out.print(TerminalUtils.yellow("-- Press SPACE for more, q to quit --"));
        System.out.flush();
    }

    private void printSummary(int count) {
        System.out.println();
        System.out.println(TerminalUtils.faint(count + " posts from " + url));
        System.out.flush();
    }

    private boolean waitForSpace() {
        boolean rawMode = TerminalUtils.enterRawMode();
        try {
            if (!rawMode) {
                // Windows fallback: read a line
                System.out.print(" ");
                int b = System.in.read();
                return b != 'q' && b != 'Q' && b != -1;
            }
            while (true) {
                int key = TerminalUtils.readKey();
                if (key == ' ' || key == '\n' || key == '\r') {
                    return true;
                }
                if (key == 'q' || key == 'Q' || key == 3 || key == -1) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            TerminalUtils.restoreTerminal();
        }
    }

    private List<Post> fetchPosts() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc;
        try (InputStream in = URI.create(url).toURL().openStream()) {
            doc = builder.parse(in);
        }

        NodeList items = doc.getElementsByTagName("item");
        List<Post> posts = new ArrayList<>();

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String title = getTextContent(item, "title");
            String link = getTextContent(item, "link");
            String pubDate = getTextContent(item, "pubDate");

            String formattedDate = pubDate;
            try {
                ZonedDateTime zdt = ZonedDateTime.parse(pubDate, RSS_DATE_FORMAT);
                formattedDate = zdt.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {
            }

            posts.add(new Post(formattedDate, title, link));
        }

        return posts;
    }

    private static String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return "";
    }

    private static String stripEmoji(String text) {
        return text.replaceAll("[\\p{So}\\p{Cn}\\x{FE0F}\\x{200D}]", "")
                .replaceAll("\\s{2,}", " ").trim();
    }

    private record Post(String date, String title, String link) {
    }
}