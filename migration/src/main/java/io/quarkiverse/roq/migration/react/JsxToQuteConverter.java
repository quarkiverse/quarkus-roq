package io.quarkiverse.roq.migration.react;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the JSX of a static React component to a Qute template, for Roq sites that use alternative expression
 * syntax ({@code {=expr}}).
 * <p>
 * It handles HTML elements and fragments, {@code className} and inline {@code style} objects, props,
 * {@code {children}}, {@code cond && <jsx>} and ternaries, {@code siteMetadata} from {@code useStaticQuery},
 * styled-components without interpolations, Font Awesome icons, Gatsby's {@code Link}, {@code StaticImage} and
 * {@code Script}, and other local components, which become includes. Anything else throws {@link Unsupported} with
 * the reason, so a component is converted completely or not at all.
 */
public final class JsxToQuteConverter {

    /**
     * What the conversion needs from the migration.
     */
    public interface Context {

        /**
         * @return the template to include for a local component, e.g. {@code partials/header}; null if that component
         *         is not converted
         */
        String templateFor(ReactComponent component, ReactComponent.Import imported);

        /**
         * @param src the {@code src} of a {@code StaticImage}, relative to the component's file
         * @return a Qute expression for the image's URL in the Roq site; null if it is not one of the site's images
         */
        String imageUrl(ReactComponent component, String src);

        /**
         * @param src the {@code src} of a {@code StaticImage}, relative to the component's file
         * @return the size of the image; null if it cannot be read
         */
        default ImageSize imageSize(ReactComponent component, String src) {
            return null;
        }

        /**
         * @param query a field of a static query that lists pages, e.g. {@code allMarkdownRemark}
         * @return how to list those pages in Qute; null if Roq has no equivalent
         */
        default PageList pageList(String query) {
            return null;
        }

        /**
         * @param component the component that uses the field
         * @param page the Qute variable of a page of a {@link PageList}
         * @param field a field of the query's nodes, e.g. {@code frontmatter.title}
         * @return the Qute expression of that field of the page; null if Roq has no equivalent
         */
        default String pageField(ReactComponent component, String page, String field) {
            return null;
        }

        /**
         * @return true if a field of the query's nodes is the page's path without its leading slash, as in
         *         {@code "/" + fields.slug}, which is then the page's URL
         */
        default boolean isPagePath(String field) {
            return false;
        }
    }

    /**
     * @param pages the Qute expression of the pages, e.g. {@code site.pages}
     * @param filter the condition a page must meet, with {@code %s} for the page's variable; null if none
     */
    public record PageList(String pages, String filter) {
    }

    /**
     * @param template the Qute template
     * @param css the CSS of the component's styled-components; empty if none
     * @param icons true if it uses Font Awesome icons, which need Font Awesome's CSS
     * @param images true if it has Gatsby images, which need {@link #GATSBY_IMAGE_CSS}
     */
    public record Converted(String template, String css, boolean icons, boolean images) {
    }

    /**
     * An include of a converted component, made from the JSX that renders it.
     *
     * @param include the Qute {@code {#include}}
     * @param omitted the props that could not be passed, because they come from where the JSX was
     */
    public record Include(String include, List<String> omitted) {
    }

    public static class Unsupported extends Exception {
        public Unsupported(String reason) {
            super(reason);
        }
    }

    static final Set<String> VOID_ELEMENTS = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input", "link",
            "meta", "source", "track", "wbr");
    /**
     * StaticImage props for Gatsby's image processing, which Roq does not do.
     */
    static final Set<String> GATSBY_IMAGE_PROPS = Set.of("placeholder", "layout", "formats", "backgroundColor", "quality",
            "transformOptions", "breakpoints", "aspectRatio", "objectFit", "objectPosition", "sizes", "as");
    static final Pattern NUMBER_OR_RATIO = Pattern.compile("(\\d+(?:\\.\\d+)?)(?:\\s*/\\s*(\\d+(?:\\.\\d+)?))?");
    /**
     * The CSS Gatsby adds to pages with images, without what fades them in once loaded, which needs its JavaScript.
     */
    public static final String GATSBY_IMAGE_CSS = """
            /* How gatsby-plugin-image lays out images */
            .gatsby-image-wrapper {
              position: relative;
              overflow: hidden;
            }

            .gatsby-image-wrapper img {
              bottom: 0;
              height: 100%;
              left: 0;
              margin: 0;
              max-width: none;
              padding: 0;
              position: absolute;
              right: 0;
              top: 0;
              width: 100%;
              object-fit: cover;
            }

            .gatsby-image-wrapper-constrained {
              display: inline-block;
              vertical-align: top;
            }
            """;
    static final Map<String, String> SITE_METADATA = Map.of("title", "site.title", "description", "site.description",
            "siteUrl", "site.url");
    static final Map<String, String> DOM_ATTRIBUTES = Map.of("className", "class", "htmlFor", "for", "tabIndex",
            "tabindex", "readOnly", "readonly", "maxLength", "maxlength", "autoComplete", "autocomplete", "crossOrigin",
            "crossorigin", "srcSet", "srcset");
    static final Set<String> UNITLESS_STYLES = Set.of("flex", "flexGrow", "flexShrink", "fontWeight", "lineHeight",
            "opacity", "order", "zIndex", "zoom");
    /**
     * JavaScript string methods, and their Java equivalents, which Qute calls.
     */
    static final Map<String, String> STRING_METHODS = Map.of("startsWith", "startsWith", "endsWith", "endsWith",
            "includes", "contains", "toLowerCase", "toLowerCase", "toUpperCase", "toUpperCase", "trim", "strip");
    /**
     * Names Roq templates already use, which a list variable must not hide.
     */
    static final Set<String> RESERVED = Set.of("page", "site", "config", "inject", "cdi");
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
    static final Pattern ICON_PACKAGE = Pattern.compile("@fortawesome/free-(solid|brands|regular)-svg-icons");

    private final ReactComponent component;
    private final Context context;
    private final StringBuilder css = new StringBuilder();
    private final Set<String> cssClasses = new LinkedHashSet<>();
    private boolean icons;
    private boolean images;
    /**
     * The variables of the lists being rendered with map(), by their JavaScript name.
     */
    private final Map<String, Loop> loops = new HashMap<>();

    /**
     * @param variable the Qute variable of the page
     * @param edges true if the items are a connection's edges, with the page in {@code node}
     * @param index the JavaScript name of the item's index; null if there is none
     */
    private record Loop(String variable, boolean edges, String index) {
    }

    private JsxToQuteConverter(ReactComponent component, Context context) {
        this.component = component;
        this.context = context;
    }

    public static Converted convert(ReactComponent component, Context context) throws Unsupported {
        return new JsxToQuteConverter(component, context).convert();
    }

    /**
     * Makes includes of the converted components that a component renders at the top level of its JSX, before and
     * after its {@code {children}}, so that a layout can render them around its content. Props that come from that
     * component's own props cannot be passed, and are reported as omitted.
     *
     * @return the includes before the children, and those after; empty if there are none
     */
    public static List<List<Include>> includesAround(ReactComponent layout, Context context) {
        List<Include> before = new ArrayList<>();
        List<Include> after = new ArrayList<>();
        if (layout.jsx() == null) {
            return List.of(before, after);
        }
        JsxToQuteConverter converter = new JsxToQuteConverter(layout, context);
        try {
            Jsx.Element root = Jsx.parse(layout.jsx(), 0).element();
            List<Jsx.Node> top = root.isFragment() ? root.children() : List.of(root);
            boolean seenChildren = false;
            for (Jsx.Node node : top) {
                if (rendersChildren(node)) {
                    seenChildren = true;
                } else if (node instanceof Jsx.Element element && layout.imports().containsKey(element.name())) {
                    ReactComponent.Import imported = layout.imports().get(element.name());
                    String template = imported.isLocal() ? context.templateFor(layout, imported) : null;
                    if (template != null) {
                        (seenChildren ? after : before).add(converter.include(element, template, true));
                    }
                }
            }
        } catch (Unsupported | Jsx.JsxException e) {
            // Nothing to wire
        }
        return List.of(before, after);
    }

    private Converted convert() throws Unsupported {
        if (component.jsx() == null) {
            throw new Unsupported("its JSX could not be found");
        }
        if (!component.prelude().isEmpty()) {
            throw new Unsupported("it runs code before rendering ("
                    + component.prelude().lines().findFirst().orElse("").strip() + ")");
        }
        for (String query : component.queries().values()) {
            checkQuery(query);
        }
        Jsx.Parsed parsed;
        try {
            parsed = Jsx.parse(component.jsx(), 0);
        } catch (Jsx.JsxException e) {
            throw new Unsupported("its JSX could not be read: " + e.getMessage());
        }
        StringBuilder template = new StringBuilder();
        template.append("{! Converted from ").append(component.file()).append(" by the migration to Roq");
        if (!component.props().isEmpty()) {
            List<String> params = component.props().stream().filter(p -> !p.equals("children")).toList();
            if (!params.isEmpty()) {
                template.append("; parameters: ").append(String.join(", ", params));
            }
        }
        template.append(" !}\n");
        StringBuilder body = new StringBuilder();
        render(parsed.element(), body);
        template.append(QuteFormatter.format(body.toString())).append("\n");
        return new Converted(template.toString(), css.toString(), icons, images);
    }

    /**
     * Checks that a static query only asks for siteMetadata, and for lists of pages Roq has.
     */
    private void checkQuery(String query) throws Unsupported {
        GraphQlSelection selection;
        try {
            selection = GraphQlSelection.parse(query);
        } catch (GraphQlSelection.InvalidQuery e) {
            throw new Unsupported("its static query could not be read: " + e.getMessage());
        }
        if (selection.arguments()) {
            throw new Unsupported("it filters or sorts what it queries with useStaticQuery");
        }
        for (Map.Entry<String, GraphQlSelection> field : selection.fields().entrySet()) {
            GraphQlSelection value = field.getValue();
            if (field.getKey().equals("site")) {
                if (value.fields().keySet().equals(Set.of("siteMetadata"))) {
                    continue;
                }
                throw new Unsupported("it queries more than siteMetadata with useStaticQuery");
            }
            boolean pages = context.pageList(field.getKey()) != null && !value.fields().isEmpty()
                    && (value.fields().keySet().equals(Set.of("nodes"))
                            || value.fields().keySet().equals(Set.of("edges"))
                                    && value.get("edges").fields().keySet().equals(Set.of("node")));
            if (!pages) {
                throw new Unsupported("it queries " + field.getKey() + " with useStaticQuery");
            }
        }
    }

    private void render(Jsx.Node node, StringBuilder out) throws Unsupported {
        switch (node) {
            case Jsx.Text text -> out.append(text.text());
            case Jsx.Expression expression -> renderExpression(expression.code(), out);
            case Jsx.Element element -> renderElement(element, out, null);
        }
    }

    private void renderChildren(List<Jsx.Node> children, StringBuilder out) throws Unsupported {
        for (Jsx.Node child : children) {
            render(child, out);
        }
    }

    private void renderElement(Jsx.Element element, StringBuilder out, String extraClass) throws Unsupported {
        if (element.isFragment()) {
            renderChildren(element.children(), out);
            return;
        }
        String name = element.name();
        ReactComponent.Styled styled = component.styled().get(name);
        if (styled != null) {
            renderStyled(element, name, styled, out, extraClass);
            return;
        }
        if (!element.isComponent()) {
            renderTag(name, element, out, extraClass);
            return;
        }
        ReactComponent.Import imported = component.imports().get(name);
        String from = imported == null ? null : imported.from();
        switch (name) {
            case "Link" -> {
                if ("gatsby".equals(from)) {
                    renderTag("a", rename(element, "to", "href"), out, extraClass);
                    return;
                }
            }
            case "Script" -> {
                if ("gatsby".equals(from)) {
                    renderTag("script", element, out, extraClass);
                    return;
                }
            }
            case "StaticImage" -> {
                if ("gatsby-plugin-image".equals(from)) {
                    renderImage(element, out, extraClass);
                    return;
                }
            }
            case "FontAwesomeIcon" -> {
                if ("@fortawesome/react-fontawesome".equals(from)) {
                    renderIcon(element, out, extraClass);
                    return;
                }
            }
            default -> {
            }
        }
        if (imported != null && imported.isLocal()) {
            String template = context.templateFor(component, imported);
            if (template == null) {
                throw new Unsupported("it uses <" + name + "> from " + from + ", which could not be converted");
            }
            out.append(include(element, template, false).include());
            return;
        }
        throw new Unsupported("it uses <" + name + ">" + (from == null ? "" : " from " + from));
    }

    private void renderTag(String tag, Jsx.Element element, StringBuilder out, String extraClass) throws Unsupported {
        out.append('<').append(tag).append(attributes(element, extraClass)).append('>');
        if (VOID_ELEMENTS.contains(tag)) {
            if (!element.children().isEmpty()) {
                throw new Unsupported("<" + tag + "> has content");
            }
            return;
        }
        renderChildren(element.children(), out);
        out.append("</").append(tag).append('>');
    }

    private void renderStyled(Jsx.Element element, String name, ReactComponent.Styled styled, StringBuilder out,
            String extraClass) throws Unsupported {
        if (styled.css() == null) {
            throw new Unsupported("the styled-component " + name + " has interpolations (${...})");
        }
        String cssClass = cssClass(name);
        if (cssClasses.add(cssClass)) {
            if (!css.isEmpty()) {
                css.append('\n');
            }
            css.append('.').append(cssClass).append(" {\n").append(indented(styled.css())).append("}\n");
        }
        String classes = extraClass == null ? cssClass : cssClass + " " + extraClass;
        String base = styled.base();
        if (Character.isLowerCase(base.charAt(0))) {
            renderTag(base, element, out, classes);
        } else {
            // A styled component, e.g. styled(FontAwesomeIcon): render that with the extra class
            renderElement(new Jsx.Element(base, element.attributes(), element.children()), out, classes);
        }
    }

    /**
     * Renders a {@code StaticImage} as Gatsby does without its lazy loading: a wrapper with the image's class, style
     * and background colour, sized for its layout, around the image. {@link #GATSBY_IMAGE_CSS} lays them out.
     */
    private void renderImage(Jsx.Element element, StringBuilder out, String extraClass) throws Unsupported {
        Jsx.Attribute src = element.attribute("src");
        if (src == null || src.string() == null) {
            throw new Unsupported("a StaticImage has no literal src");
        }
        String url = context.imageUrl(component, src.string());
        if (url == null) {
            throw new Unsupported("the StaticImage " + src.string() + " is not one of the site's images");
        }
        String layout = Objects.requireNonNullElse(literal(element.attribute("layout")), "constrained");
        if (!List.of("constrained", "fixed", "fullWidth").contains(layout)) {
            throw new Unsupported("a StaticImage has the layout " + layout);
        }
        Double width = number(element, "width");
        Double height = number(element, "height");
        Double aspectRatio = number(element, "aspectRatio");
        ImageSize natural = context.imageSize(component, src.string());
        if (aspectRatio == null && width != null && height != null) {
            aspectRatio = width / height;
        } else if (aspectRatio == null && natural != null) {
            aspectRatio = natural.aspectRatio();
        }
        boolean fullWidth = layout.equals("fullWidth");
        if (aspectRatio == null || !fullWidth && width == null && height == null && natural == null) {
            renderPlainImage(element, url, out, extraClass);
            return;
        }
        // As Gatsby sizes it: the dimensions given, the other one from the aspect ratio, or the image's own width
        if (width == null && !fullWidth) {
            width = height != null ? height * aspectRatio : natural.width();
        }
        if (height == null && !fullWidth) {
            height = width / aspectRatio;
        }
        long w = fullWidth ? 0 : Math.round(width);
        long h = fullWidth ? 0 : Math.round(height);

        String background = literal(element.attribute("backgroundColor"));
        List<String> wrapperStyle = new ArrayList<>();
        List<String> imageStyle = new ArrayList<>();
        List<Jsx.Attribute> imageAttributes = new ArrayList<>(List.of(new Jsx.Attribute("src", null, null)));
        Jsx.Attribute className = null;
        for (Jsx.Attribute attribute : element.attributes()) {
            String name = attribute.name();
            if (name == null) {
                throw new Unsupported("a StaticImage is given {..." + attribute.code() + "}");
            }
            switch (name) {
                case "className" -> className = attribute;
                case "imgClassName" ->
                    imageAttributes.add(new Jsx.Attribute("className", attribute.string(), attribute.code()));
                case "style" -> wrapperStyle.add(attribute.code() == null ? attribute.string() : style(attribute.code()));
                case "imgStyle" -> imageStyle.add(attribute.code() == null ? attribute.string() : style(attribute.code()));
                case "src", "width", "height" -> {
                }
                default -> {
                    if (!GATSBY_IMAGE_PROPS.contains(name)) {
                        imageAttributes.add(attribute);
                    }
                }
            }
        }
        if (layout.equals("fixed")) {
            wrapperStyle.add("width: " + w + "px");
            wrapperStyle.add("height: " + h + "px");
        }
        if (background != null) {
            wrapperStyle.add("background-color: " + background);
        }
        imageStyle.add("object-fit: " + Objects.requireNonNullElse(literal(element.attribute("objectFit")), "cover"));
        String position = literal(element.attribute("objectPosition"));
        if (position != null) {
            imageStyle.add("object-position: " + position);
        }
        if (background != null) {
            imageStyle.add("background-color: " + background);
        }
        imageAttributes.add(new Jsx.Attribute("style", String.join("; ", imageStyle), null));
        if (element.attribute("decoding") == null) {
            imageAttributes.add(new Jsx.Attribute("decoding", "async", null));
        }
        if (element.attribute("loading") == null) {
            imageAttributes.add(new Jsx.Attribute("loading", "lazy", null));
        }

        List<Jsx.Attribute> wrapperAttributes = new ArrayList<>(
                List.of(new Jsx.Attribute("data-gatsby-image-wrapper", null, null)));
        // Bare, it is only the wrapper's own classes
        wrapperAttributes.add(className != null ? className : new Jsx.Attribute("className", null, null));
        if (!wrapperStyle.isEmpty()) {
            wrapperAttributes.add(new Jsx.Attribute("style", String.join("; ", wrapperStyle), null));
        }
        String classes = "gatsby-image-wrapper" + (layout.equals("constrained") ? " gatsby-image-wrapper-constrained" : "")
                + (extraClass == null ? "" : " " + extraClass);
        out.append("<div").append(attributes(new Jsx.Element("div", wrapperAttributes, List.of()), classes)).append('>');
        switch (layout) {
            case "constrained" -> out.append("<div style=\"max-width: ").append(w).append("px; display: block\">")
                    .append("<img alt=\"\" role=\"presentation\" aria-hidden=\"true\" ")
                    .append("src=\"data:image/svg+xml;charset=utf-8,%3Csvg%20height='").append(h).append("'%20width='")
                    .append(w).append("'%20xmlns='http://www.w3.org/2000/svg'%20version='1.1'%3E%3C/svg%3E\" ")
                    .append("style=\"max-width: 100%; display: block; position: static\"></div>");
            case "fullWidth" -> out.append("<div aria-hidden=\"true\" style=\"padding-top: ")
                    .append(BigDecimal.valueOf(100 / aspectRatio).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
                            .toPlainString())
                    .append("%\"></div>");
            default -> {
            }
        }
        String rendered = attributes(new Jsx.Element("img", imageAttributes, List.of()), null)
                .replaceFirst(" src", Matcher.quoteReplacement(" src=\"{=" + url + "}\""));
        out.append("<img").append(rendered).append("></div>");
        images = true;
    }

    /**
     * Renders a {@code StaticImage} whose size is not known as an image with the look Gatsby gives it, without the
     * wrapper that would need the size.
     */
    private void renderPlainImage(Jsx.Element element, String url, StringBuilder out, String extraClass)
            throws Unsupported {
        List<Jsx.Attribute> attributes = new ArrayList<>(List.of(new Jsx.Attribute("src", null, null)));
        List<String> look = new ArrayList<>(List.of("margin: 0",
                "object-fit: " + Objects.requireNonNullElse(literal(element.attribute("objectFit")), "cover")));
        String position = literal(element.attribute("objectPosition"));
        if (position != null) {
            look.add("object-position: " + position);
        }
        String background = literal(element.attribute("backgroundColor"));
        if (background != null) {
            look.add("background-color: " + background);
        }
        for (Jsx.Attribute attribute : element.attributes()) {
            if (("style".equals(attribute.name()) || "imgStyle".equals(attribute.name())) && attribute.code() != null) {
                look.add(0, style(attribute.code()));
            } else if (!"src".equals(attribute.name()) && !"imgClassName".equals(attribute.name())
                    && !GATSBY_IMAGE_PROPS.contains(attribute.name())) {
                attributes.add(attribute);
            }
        }
        attributes.add(new Jsx.Attribute("style", String.join("; ", look), null));
        String rendered = attributes(new Jsx.Element("img", attributes, List.of()), extraClass)
                .replaceFirst(" src", Matcher.quoteReplacement(" src=\"{=" + url + "}\""));
        out.append("<img").append(rendered).append('>');
    }

    /**
     * @return the number an attribute is, such as {@code {36}} or {@code {16 / 9}}; null if there is no such attribute
     */
    private static Double number(Jsx.Element element, String name) throws Unsupported {
        Jsx.Attribute attribute = element.attribute(name);
        if (attribute == null) {
            return null;
        }
        String value = attribute.string() != null ? attribute.string() : JsSource.unwrap(attribute.code());
        Matcher number = NUMBER_OR_RATIO.matcher(value);
        if (!number.matches()) {
            throw new Unsupported("a StaticImage has a computed " + name + " (" + value + ")");
        }
        double n = Double.parseDouble(number.group(1));
        return number.group(2) == null ? n : n / Double.parseDouble(number.group(2));
    }

    /**
     * @return the value of an attribute that is a string, such as {@code a="b"} or {@code a={'b'}}; null if it is not
     */
    private static String literal(Jsx.Attribute attribute) {
        if (attribute == null) {
            return null;
        }
        if (attribute.string() != null) {
            return attribute.string();
        }
        String c = attribute.code() == null ? "" : JsSource.unwrap(attribute.code());
        return isStringLiteral(c) ? c.substring(1, c.length() - 1) : null;
    }

    private void renderIcon(Jsx.Element element, StringBuilder out, String extraClass) throws Unsupported {
        Jsx.Attribute icon = element.attribute("icon");
        if (icon == null || icon.code() == null || !IDENTIFIER.matcher(icon.code()).matches()) {
            throw new Unsupported("a FontAwesomeIcon has an icon that is not an imported icon");
        }
        ReactComponent.Import imported = component.imports().get(icon.code());
        Matcher iconPackage = imported == null ? null : ICON_PACKAGE.matcher(imported.from());
        if (iconPackage == null || !iconPackage.matches()) {
            throw new Unsupported("the icon " + icon.code() + " is not from a free Font Awesome package");
        }
        icons = true;
        String iconName = kebab(imported.imported().replaceFirst("^fa", ""));
        String classes = "fa-" + iconPackage.group(1) + " fa-" + iconName + (extraClass == null ? "" : " " + extraClass);
        Jsx.Attribute className = element.attribute("className");
        if (className != null && className.string() != null) {
            classes += " " + className.string();
        }
        out.append("<i class=\"").append(classes).append("\" aria-hidden=\"true\"></i>");
    }

    private Include include(Jsx.Element element, String template, boolean outsideComponent) throws Unsupported {
        StringBuilder include = new StringBuilder("{#include ").append(template);
        List<String> omitted = new ArrayList<>();
        for (Jsx.Attribute attribute : element.attributes()) {
            if (attribute.isSpread()) {
                throw new Unsupported("<" + element.name() + "> is given {..." + attribute.code() + "}");
            }
            String value;
            if (attribute.string() != null) {
                value = quoteString(attribute.string());
            } else if (attribute.code() == null) {
                value = "true";
            } else {
                try {
                    value = qute(attribute.code(), true, outsideComponent);
                } catch (Unsupported e) {
                    if (!outsideComponent) {
                        throw e;
                    }
                    omitted.add(attribute.name());
                    continue;
                }
                if (value.contains(" ")) {
                    value = "(" + value + ")";
                }
            }
            include.append(' ').append(attribute.name()).append('=').append(value);
        }
        if (element.children().isEmpty()) {
            include.append(" /}");
        } else {
            include.append('}');
            renderChildren(element.children(), include);
            include.append("{/include}");
        }
        return new Include(include.toString(), omitted);
    }

    private String attributes(Jsx.Element element, String extraClass) throws Unsupported {
        StringBuilder out = new StringBuilder();
        boolean classDone = false;
        for (Jsx.Attribute attribute : element.attributes()) {
            if (attribute.isSpread()) {
                throw new Unsupported("<" + element.name() + "> is given {..." + attribute.code() + "}");
            }
            String name = attribute.name();
            if (name.equals("key") || name.equals("ref")) {
                continue;
            }
            if (name.equals("dangerouslySetInnerHTML")) {
                throw new Unsupported("it sets raw HTML with dangerouslySetInnerHTML");
            }
            String htmlName = DOM_ATTRIBUTES.getOrDefault(name, name);
            out.append(' ').append(htmlName);
            if (attribute.string() == null && attribute.code() == null) {
                if (htmlName.equals("class") && extraClass != null) {
                    out.append("=\"").append(extraClass).append('"');
                    classDone = true;
                }
                continue;
            }
            String value;
            if (name.equals("style") && attribute.code() != null) {
                value = style(attribute.code());
            } else if (attribute.string() != null) {
                value = attribute.string().replace("\"", "&quot;");
            } else {
                value = attributeValue(attribute.code());
                if (value == null) {
                    // A boolean attribute set to true
                    continue;
                }
            }
            if (htmlName.equals("class") && extraClass != null) {
                value = extraClass + " " + value;
                classDone = true;
            }
            out.append("=\"").append(value).append('"');
        }
        if (extraClass != null && !classDone) {
            out.append(" class=\"").append(extraClass).append('"');
        }
        return out.toString();
    }

    /**
     * @return the attribute value as text with Qute expressions; null for {@code {true}}
     */
    private String attributeValue(String code) throws Unsupported {
        String c = JsSource.unwrap(code);
        if (c.equals("true")) {
            return null;
        }
        if (c.matches("-?\\d+(\\.\\d+)?")) {
            return c;
        }
        if (isStringLiteral(c)) {
            return c.substring(1, c.length() - 1).replace("\"", "&quot;");
        }
        if (c.startsWith("`")) {
            return templateLiteral(c);
        }
        List<Part> concatenation = concatenation(c);
        if (concatenation != null) {
            return join(concatenation);
        }
        return output(c);
    }

    /**
     * @return the expression to render where it appears in the output
     */
    private String output(String code) throws Unsupported {
        return "{=" + qute(code, true, false) + "}";
    }

    private void renderExpression(String code, StringBuilder out) throws Unsupported {
        String c = JsSource.unwrap(code);
        if (c.equals("children") && component.props().contains("children")) {
            out.append("{#insert /}");
            return;
        }
        int map = mapCall(c);
        if (map > 0) {
            renderMap(c.substring(0, map), c.substring(map + ".map(".length(), c.length() - 1), out);
            return;
        }
        if (c.contains(".map(")) {
            throw new Unsupported("it renders a list with map()");
        }
        int or = JsSource.indexAtTopLevel(c, "||");
        if (or > 0) {
            String rendered = JsSource.unwrap(c.substring(or + 2));
            if (rendered.startsWith("<")) {
                // JSX renders false as nothing, so a || <jsx> renders the JSX when a is false
                out.append("{#if ").append(negate(condition(c.substring(0, or)))).append('}');
                renderJsx(rendered, out);
                out.append("{/if}");
                return;
            }
        }
        int and = JsSource.indexAtTopLevel(c, "&&");
        if (and > 0) {
            String rendered = JsSource.unwrap(c.substring(and + 2));
            if (rendered.startsWith("<")) {
                out.append("{#if ").append(condition(c.substring(0, and))).append('}');
                renderJsx(rendered, out);
                out.append("{/if}");
                return;
            }
        }
        int question = ternaryQuestion(c);
        if (question > 0) {
            String rest = JsSource.unwrap(c.substring(question + 1));
            int colon = rest.startsWith("<") ? endOfJsx(rest) : JsSource.indexAtTopLevel(rest, ":");
            if (colon > 0) {
                colon = rest.indexOf(':', colon);
                String whenTrue = rest.substring(0, colon).strip();
                String whenFalse = JsSource.unwrap(rest.substring(colon + 1));
                out.append("{#if ").append(condition(c.substring(0, question))).append('}');
                renderBranch(whenTrue, out);
                if (!whenFalse.equals("null") && !whenFalse.equals("undefined") && !whenFalse.isEmpty()) {
                    out.append("{#else}");
                    renderBranch(whenFalse, out);
                }
                out.append("{/if}");
                return;
            }
        }
        if (isStringLiteral(c)) {
            out.append(c, 1, c.length() - 1);
            return;
        }
        if (c.startsWith("`")) {
            out.append(templateLiteral(c));
            return;
        }
        List<Part> concatenation = concatenation(c);
        if (concatenation != null) {
            out.append(join(concatenation));
            return;
        }
        out.append(output(c));
    }

    /**
     * @return the index of {@code .map(} in {@code list.map(...)}, where the list is a path; -1 if it is not that
     */
    private static int mapCall(String c) {
        int map = c.indexOf(".map(");
        if (map <= 0 || JsSource.matching(c, map + 4) != c.length() - 1) {
            return -1;
        }
        String list = c.substring(0, map).replace("?.", ".");
        for (String segment : list.split("\\.")) {
            if (!IDENTIFIER.matcher(segment).matches()) {
                return -1;
            }
        }
        return map;
    }

    /**
     * Renders {@code list.map((item, i) => <jsx>)} as a {@code {#for}} over the pages a static query lists.
     */
    private void renderMap(String list, String function, StringBuilder out) throws Unsupported {
        int arrow = JsSource.indexAtTopLevel(function, "=>");
        if (arrow < 0) {
            throw new Unsupported("it renders a list with map() and a function that is not an arrow function");
        }
        String params = JsSource.unwrap(function.substring(0, arrow));
        String body = function.substring(arrow + 2).strip();
        if (body.startsWith("{")) {
            throw new Unsupported("it renders a list with map() and a function body");
        }
        List<String> names = List.of(params.split(",")).stream().map(String::strip).toList();
        for (String name : names) {
            if (!IDENTIFIER.matcher(name).matches()) {
                throw new Unsupported("it renders a list with map() and destructures its items (" + params + ")");
            }
        }
        if (names.size() > 2) {
            throw new Unsupported("it renders a list with map() and uses the whole list in its function");
        }
        String[] path = list.replace("?.", ".").split("\\.");
        String queryPath = queryPath(path[0], path);
        String[] fields = queryPath == null ? new String[0] : queryPath.split("\\.");
        PageList pages = fields.length == 2 ? context.pageList(fields[0]) : null;
        if (pages == null || !fields[1].equals("edges") && !fields[1].equals("nodes")) {
            throw new Unsupported("it renders a list of " + list + " with map()");
        }
        String item = names.get(0);
        String variable = RESERVED.contains(item) ? item + "_" : item;
        if (loops.containsKey(item)) {
            throw new Unsupported("it nests lists that use the same name, " + item);
        }
        out.append("{#for ").append(variable).append(" in ").append(pages.pages()).append('}');
        if (pages.filter() != null) {
            out.append("{#if ").append(pages.filter().formatted(variable)).append('}');
        }
        loops.put(item, new Loop(variable, fields[1].equals("edges"), names.size() > 1 ? names.get(1) : null));
        try {
            renderBranch(body, out);
        } finally {
            loops.remove(item);
        }
        if (pages.filter() != null) {
            out.append("{/if}");
        }
        out.append("{/for}");
    }

    private void renderBranch(String branch, StringBuilder out) throws Unsupported {
        String b = JsSource.unwrap(branch);
        if (b.startsWith("<")) {
            renderJsx(b, out);
        } else {
            renderExpression(b, out);
        }
    }

    private void renderJsx(String jsx, StringBuilder out) throws Unsupported {
        try {
            render(Jsx.parse(jsx, 0).element(), out);
        } catch (Jsx.JsxException e) {
            throw new Unsupported("its JSX could not be read: " + e.getMessage());
        }
    }

    private static int endOfJsx(String code) {
        try {
            return Jsx.parse(code, 0).end();
        } catch (Jsx.JsxException e) {
            return -1;
        }
    }

    /**
     * @return the index of the {@code ?} of a ternary, not of {@code ?.} or {@code ??}; -1 if there is none
     */
    private static int ternaryQuestion(String c) {
        int from = 0;
        while (true) {
            int q = JsSource.indexAtTopLevel(c.substring(from), "?");
            if (q < 0) {
                return -1;
            }
            int i = from + q;
            boolean operator = i + 1 < c.length() && (c.charAt(i + 1) == '.' || c.charAt(i + 1) == '?')
                    || i > 0 && c.charAt(i - 1) == '?';
            if (!operator) {
                return i;
            }
            from = i + 2;
        }
    }

    private String condition(String code) throws Unsupported {
        String c = JsSource.unwrap(code);
        boolean not = c.startsWith("!");
        String tested = not ? c.substring(1) : c;
        String quteCondition = qute(tested, true, false);
        return not ? negate(quteCondition) : quteCondition;
    }

    private static String negate(String condition) {
        if (condition.startsWith("!") && !condition.contains(" ")) {
            return condition.substring(1);
        }
        return condition.contains(" ") ? "!(" + condition + ")" : "!" + condition;
    }

    /**
     * Translates a JavaScript value expression to Qute.
     *
     * @param optional whether a missing prop should render as nothing, rather than be left to a fallback
     * @param outsideComponent true if the expression is not evaluated inside the component, so its props are not
     *        available
     */
    private String qute(String code, boolean optional, boolean outsideComponent) throws Unsupported {
        String c = JsSource.unwrap(code);
        for (String fallback : List.of("||", "??")) {
            int at = JsSource.indexAtTopLevel(c, fallback);
            if (at > 0 && !c.startsWith("?", at + 2)) {
                String left = qute(c.substring(0, at), false, outsideComponent);
                String right = qute(c.substring(at + 2), false, outsideComponent);
                return left + " ?: " + right;
            }
        }
        if (isStringLiteral(c)) {
            return quoteString(c.substring(1, c.length() - 1));
        }
        if (c.startsWith("`") && !c.contains("${")) {
            return quoteString(c.substring(1, c.length() - 1));
        }
        if (c.matches("-?\\d+(\\.\\d+)?|true|false")) {
            return c;
        }
        String call = stringMethod(c, outsideComponent);
        if (call != null) {
            return call;
        }
        String[] path = c.replace("?.", ".").split("\\.");
        for (String segment : path) {
            if (!IDENTIFIER.matcher(segment).matches()) {
                throw new Unsupported("it uses the expression " + c);
            }
        }
        String root = path[0];
        Loop loop = loopOf(root);
        if (loop != null) {
            if (root.equals(loop.index())) {
                if (path.length > 1) {
                    throw new Unsupported("it uses the expression " + c);
                }
                return loop.variable() + "_index";
            }
            String field = pageFieldPath(path, loop, c);
            String mapped = context.pageField(component, loop.variable(), field);
            if (mapped == null) {
                throw new Unsupported("it uses " + field + " of the pages it lists, which Roq has no equivalent for");
            }
            return mapped;
        }
        String queryPath = queryPath(root, path);
        if (queryPath != null) {
            if (!queryPath.startsWith("site.siteMetadata.")) {
                throw new Unsupported("it uses " + c + " other than to render a list with map()");
            }
            String key = queryPath.substring("site.siteMetadata.".length());
            String mapped = SITE_METADATA.get(key);
            if (mapped == null) {
                throw new Unsupported("it uses siteMetadata." + key + ", which Roq has no place for");
            }
            return mapped;
        }
        if (component.props().contains(root) && !outsideComponent) {
            if (path.length > 1) {
                // Qute renders a path on a missing value as the value's name; .or('') makes the path not found instead
                return root + ".or('')." + String.join(".", List.of(path).subList(1, path.length))
                        + (optional ? "??" : "");
            }
            return root + (optional ? "??" : "");
        }
        throw new Unsupported("it uses " + c + ", which is not a prop or siteMetadata");
    }

    /**
     * @return the loop a JavaScript name is the item or the index of; null if none
     */
    private Loop loopOf(String name) {
        Loop loop = loops.get(name);
        if (loop != null) {
            return loop;
        }
        return loops.values().stream().filter(l -> name.equals(l.index())).findFirst().orElse(null);
    }

    /**
     * @return the field of the page an item's path is about, e.g. {@code fields.slug} for
     *         {@code edge.node.fields.slug}
     */
    private static String pageFieldPath(String[] path, Loop loop, String code) throws Unsupported {
        int start = loop.edges() ? 2 : 1;
        if (path.length <= start || loop.edges() && !path[1].equals("node")) {
            throw new Unsupported("it uses " + code + ", which is not a field of the pages it lists");
        }
        return String.join(".", List.of(path).subList(start, path.length));
    }

    /**
     * @return a call of a JavaScript string method in Qute, e.g. {@code s.startsWith("_")}; null if the code is not one
     */
    private String stringMethod(String c, boolean outsideComponent) throws Unsupported {
        if (!c.endsWith(")")) {
            return null;
        }
        int open = -1;
        for (int i = c.length() - 1; i >= 0; i--) {
            if (c.charAt(i) == '(' && JsSource.matching(c, i) == c.length() - 1) {
                open = i;
                break;
            }
        }
        Matcher method = open < 0 ? null : Pattern.compile("(.+)\\.(\\w+)$").matcher(c.substring(0, open));
        if (method == null || !method.matches() || !STRING_METHODS.containsKey(method.group(2))) {
            return null;
        }
        String target = qute(method.group(1), false, outsideComponent);
        if (target.contains(" ")) {
            throw new Unsupported("it uses the expression " + c);
        }
        List<String> arguments = new ArrayList<>();
        String args = c.substring(open + 1, c.length() - 1).strip();
        if (!args.isEmpty()) {
            for (String argument : args.split(",")) {
                String value = qute(argument, false, outsideComponent);
                if (!value.matches("'[^']*'|\"[^\"]*\"|-?\\d+")) {
                    throw new Unsupported("it uses the expression " + c);
                }
                arguments.add(value);
            }
        }
        return target + "." + STRING_METHODS.get(method.group(2)) + "(" + String.join(", ", arguments) + ")";
    }

    /**
     * Text, or a JavaScript expression to render.
     */
    private record Part(String text, String code) {
    }

    /**
     * @return the operands of a concatenation with at least one string, e.g. {@code "/" + slug}; null if the code is
     *         not one
     */
    private static List<Part> concatenation(String c) {
        List<Part> parts = new ArrayList<>();
        String rest = c;
        boolean string = false;
        while (true) {
            int plus = JsSource.indexAtTopLevel(rest, "+");
            String operand = JsSource.unwrap(plus < 0 ? rest : rest.substring(0, plus));
            if (operand.isEmpty()) {
                return null;
            }
            if (isStringLiteral(operand)) {
                parts.add(new Part(operand.substring(1, operand.length() - 1), null));
                string = true;
            } else {
                parts.add(new Part(null, operand));
            }
            if (plus < 0) {
                break;
            }
            rest = rest.substring(plus + 1);
        }
        return string && parts.size() > 1 ? parts : null;
    }

    /**
     * @return the parts as text with Qute expressions; a page path after a lone {@code /} is the page's URL
     */
    private String join(List<Part> parts) throws Unsupported {
        StringBuilder out = new StringBuilder();
        boolean afterUrl = false;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.code() == null) {
                String text = afterUrl && part.text().startsWith("/") ? part.text().substring(1) : part.text();
                afterUrl = false;
                if (text.equals("/") && out.isEmpty() && i + 1 < parts.size() && pageUrl(parts.get(i + 1)) != null) {
                    continue;
                }
                out.append(text.replace("\"", "&quot;"));
            } else {
                String url = out.isEmpty() && i > 0 && "/".equals(parts.get(i - 1).text()) ? pageUrl(part) : null;
                if (url != null) {
                    // The URL ends with a slash
                    out.append("{=").append(url).append('}');
                    afterUrl = true;
                } else {
                    out.append(output(part.code()));
                }
            }
        }
        return out.toString();
    }

    /**
     * @return the URL of the page whose path the part is, e.g. {@code edge.url} for {@code edge.node.fields.slug};
     *         null if it is not a page path
     */
    private String pageUrl(Part part) throws Unsupported {
        if (part.code() == null) {
            return null;
        }
        String[] path = part.code().replace("?.", ".").split("\\.");
        Loop loop = loops.get(path[0]);
        if (loop == null) {
            return null;
        }
        return context.isPagePath(pageFieldPath(path, loop, part.code())) ? loop.variable() + ".url" : null;
    }

    /**
     * @return the path in the {@code useStaticQuery} result, e.g. {@code site.siteMetadata.title}; null if the
     *         expression is not about a query result
     */
    private String queryPath(String root, String[] path) {
        for (String variable : component.queries().keySet()) {
            if (variable.equals(root)) {
                return path.length > 1 ? String.join(".", List.of(path).subList(1, path.length)) : null;
            }
            if (variable.startsWith("{") && List.of(variable.substring(1, variable.length() - 1).split(","))
                    .contains(root)) {
                return String.join(".", path);
            }
        }
        return null;
    }

    private String templateLiteral(String literal) throws Unsupported {
        String body = literal.substring(1, literal.length() - 1);
        List<Part> parts = new ArrayList<>();
        int i = 0;
        while (i < body.length()) {
            int start = body.indexOf("${", i);
            if (start < 0) {
                parts.add(new Part(body.substring(i), null));
                break;
            }
            if (start > i) {
                parts.add(new Part(body.substring(i, start), null));
            }
            int end = JsSource.matching(body, start + 1);
            parts.add(new Part(null, body.substring(start + 2, end).strip()));
            i = end + 1;
        }
        return join(parts);
    }

    /**
     * @return CSS for a JSX style object, e.g. {@code {{ display: 'flex', columnGap: '40px' }}}
     */
    private static String style(String code) throws Unsupported {
        String object = JsSource.unwrap(code);
        if (!object.startsWith("{") || !object.endsWith("}")) {
            throw new Unsupported("it has a style that is not an object literal");
        }
        List<String> declarations = new ArrayList<>();
        String body = object.substring(1, object.length() - 1);
        int i = 0;
        while (i < body.length()) {
            int comma = JsSource.indexAtTopLevel(body.substring(i), ",");
            String entry = comma < 0 ? body.substring(i) : body.substring(i, i + comma);
            i = comma < 0 ? body.length() : i + comma + 1;
            if (entry.isBlank()) {
                continue;
            }
            int colon = entry.indexOf(':');
            if (colon < 0) {
                throw new Unsupported("it has a style entry that is not key: value (" + entry.strip() + ")");
            }
            String key = entry.substring(0, colon).strip().replaceAll("^[\"']|[\"']$", "");
            String value = entry.substring(colon + 1).strip();
            if (isStringLiteral(value) || value.startsWith("`") && !value.contains("${")) {
                value = value.substring(1, value.length() - 1);
            } else if (value.matches("-?\\d+(\\.\\d+)?")) {
                value = UNITLESS_STYLES.contains(key) || value.equals("0") ? value : value + "px";
            } else {
                throw new Unsupported("it has a computed style (" + key + ": " + value + ")");
            }
            declarations.add(kebab(key) + ": " + value);
        }
        return String.join("; ", declarations);
    }

    /**
     * @return the CSS indented by two spaces, keeping the indentation of nested rules
     */
    private static String indented(String css) {
        List<String> lines = css.lines().toList();
        int common = lines.stream().filter(l -> !l.isBlank()).mapToInt(l -> l.length() - l.stripLeading().length())
                .min().orElse(0);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line.isBlank() ? "" : "  " + line.substring(Math.min(common, line.length())).stripTrailing())
                    .append('\n');
        }
        return out.toString();
    }

    private String cssClass(String styledName) {
        String file = component.file().replaceAll(".*/", "").replaceAll("\\.[^.]+$", "");
        return kebab(file) + "-" + kebab(styledName);
    }

    private static Jsx.Element rename(Jsx.Element element, String from, String to) {
        List<Jsx.Attribute> attributes = element.attributes().stream()
                .map(a -> from.equals(a.name()) ? new Jsx.Attribute(to, a.string(), a.code()) : a).toList();
        return new Jsx.Element(element.name(), attributes, element.children());
    }

    private static boolean rendersChildren(Jsx.Node node) {
        return switch (node) {
            case Jsx.Expression expression -> JsSource.unwrap(expression.code()).equals("children");
            case Jsx.Element element -> element.children().stream().anyMatch(JsxToQuteConverter::rendersChildren);
            case Jsx.Text text -> false;
        };
    }

    private static boolean isStringLiteral(String c) {
        return c.length() >= 2 && (c.startsWith("'") && c.endsWith("'") || c.startsWith("\"") && c.endsWith("\""))
                && JsSource.skipString(c, 0) == c.length();
    }

    private static String quoteString(String value) {
        return value.contains("'") ? "\"" + value.replace("\"", "\\\"") + "\"" : "'" + value + "'";
    }

    static String kebab(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").replaceAll("([A-Z])([A-Z][a-z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
    }
}
