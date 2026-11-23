package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsxToQuteConverterTest {

    /**
     * Local components convert to partials/<name>, except ./broken; images are under src/images, and only wide.png has a
     * known size; allMarkdownRemark lists the Markdown pages, served at fields.slug.
     */
    static final JsxToQuteConverter.Context CONTEXT = new JsxToQuteConverter.Context() {
        @Override
        public String templateFor(ReactComponent component, ReactComponent.Import imported) {
            return imported.from().equals("./broken") ? null : "partials/" + imported.from().replace("./", "");
        }

        @Override
        public String imageUrl(ReactComponent component, String src) {
            return src.startsWith("../images/") ? "site.url('images/" + src.substring(10) + "')" : null;
        }

        @Override
        public ImageSize imageSize(ReactComponent component, String src) {
            return src.equals("../images/wide.png") ? new ImageSize(200, 100) : null;
        }

        @Override
        public JsxToQuteConverter.PageList pageList(String query) {
            return query.equals("allMarkdownRemark") ? new JsxToQuteConverter.PageList("site.pages", "%s.markdown") : null;
        }

        @Override
        public String pageField(ReactComponent component, String page, String field) {
            return switch (field) {
                case "frontmatter.title" -> page + ".title";
                case "fields.slug" -> page + ".slug";
                default -> null;
            };
        }

        @Override
        public boolean isPagePath(String field) {
            return field.equals("fields.slug");
        }

        @Override
        public String queriedPage(ReactComponent component, String query, Map<String, String> equalities) {
            return query.equals("markdownRemark") && equalities.equals(Map.of("fields.slug", "home"))
                    ? "site.page('Home.md')"
                    : null;
        }

        @Override
        public boolean isPageHtml(String field) {
            return field.equals("html");
        }

        @Override
        public String layoutFor(ReactComponent component, ReactComponent.Import imported) {
            return imported.from().equals("../components/layout") ? "main" : null;
        }
    };

    static String body(String component) throws JsxToQuteConverter.Unsupported {
        String template = JsxToQuteConverter.convert(ReactComponent.read("src/components/c.js", component), CONTEXT).template();
        return template.substring(template.indexOf("!}\n") + 3).strip();
    }

    static String unsupported(String component) {
        try {
            body(component);
            throw new AssertionError("converted");
        } catch (JsxToQuteConverter.Unsupported e) {
            return e.getMessage();
        }
    }

    @Test
    void convertsElementsAttributesAndText() throws Exception {
        assertThat(body("""
                const C = () => (
                  <div className="box" htmlFor="x" key="k" data-x="1" hidden={true}>
                    <img src="/a.png" alt="A"/>
                    It&#39;s <b>bold</b>
                  </div>
                )
                export default C
                """)).isEqualTo("<div class=\"box\" for=\"x\" data-x=\"1\" hidden><img src=\"/a.png\" alt=\"A\">It&#39;s "
                + "<b>bold</b></div>");
    }

    @Test
    void convertsTheConstantsAComponentDeclaresBeforeRendering() throws Exception {
        assertThat(body("""
                const C = ({user}) => {
                  const {name, role: job} = user
                  const label = name || 'Anonymous';
                  return <p title={job}>{label}</p>
                }
                export default C
                """)).isEqualTo("<p title=\"{=user.or('').role??}\">{=user.or('').name ?: 'Anonymous'}</p>");
    }

    static final String HOME_PAGE = """
            import Layout from "../components/layout"
            const HomePage = ({data}) => {
                const {markdownRemark} = data
                const {fields, html} = markdownRemark
                return (
                    <Layout pageName={fields.pageName} kind="home" wide={true}>
                        <div className="home" dangerouslySetInnerHTML={{__html: html}}/>
                        <p>{fields.slug}</p>
                    </Layout>
                )
            }
            export const query = graphql`query Home { markdownRemark(fields: {slug: {eq: "home"}}) { html fields { slug } } }`
            export const Head = () => <Seo title="Home" description="The hub"/>
            export default HomePage
            """;

    @Test
    void convertsPagesThatRenderInTheLayoutToRoqPages() throws Exception {
        JsxToQuteConverter.ConvertedPage page = JsxToQuteConverter.convertPage(
                ReactComponent.read("src/pages/index.jsx", HOME_PAGE), CONTEXT);

        // The page query's page, the literal props it gives the layout, and its Head's title
        assertThat(page.frontMatter()).containsExactly(Map.entry("layout", "main"), Map.entry("kind", "home"),
                Map.entry("wide", "true"), Map.entry("title", "Home"), Map.entry("description", "The hub"));
        assertThat(page.omitted()).containsExactly("pageName");
        assertThat(page.body()).isEqualTo("""
                <div class="home">{=site.pageContent(site.page('Home.md')).raw}</div>
                <p>{=site.page('Home.md').slug}</p>
                """);
    }

    @Test
    void saysWhyItCannotConvertAPage() {
        Map<String, String> reasons = Map.of(
                HOME_PAGE.replace("eq: \"home\"", "eq: \"away\""),
                "its page query asks for markdownRemark(fields: {slug: {eq: \"away\"}}), which is not one of the "
                        + "site's pages",
                HOME_PAGE.replace("../components/layout", "../components/other"),
                "it does not render its content in the site's layout",
                HOME_PAGE.replace("<p>{fields.slug}</p>", "<p>{html}</p>"),
                "it renders the HTML of the page it queries as text",
                HOME_PAGE.replace("{{__html: html}}", "{{__html: fields.slug}}"),
                "it sets raw HTML with dangerouslySetInnerHTML, from fields.slug, which is not the HTML of a page");
        reasons.forEach((page, reason) -> {
            try {
                JsxToQuteConverter.convertPage(ReactComponent.read("src/pages/index.jsx", page), CONTEXT);
                throw new AssertionError("converted");
            } catch (JsxToQuteConverter.Unsupported e) {
                assertThat(e.getMessage()).as(reason).isEqualTo(reason);
            }
        });
    }

    @Test
    void readsTheRootLayoutsPropsFromThePagesFrontMatter() throws Exception {
        String template = JsxToQuteConverter.convertLayout(ReactComponent.read("src/components/layout.js",
                "const L = ({children, pageName}) => <main title={pageName}>{children}</main>\nexport default L"),
                CONTEXT).template();

        assertThat(template).contains("<main title=\"{=page.data.pageName??}\">{#insert /}</main>");
    }

    @Test
    void convertsInlineStyles() throws Exception {
        assertThat(body("""
                const C = () => <div style={{margin: `0 auto`, columnGap: '40px', padding: 8, flex: 1, 'z-index': 0}}/>
                export default C
                """)).isEqualTo("<div style=\"margin: 0 auto; column-gap: 40px; padding: 8px; flex: 1; z-index: 0\"></div>");
    }

    @Test
    void convertsPropsWithoutBreakingWhenTheyAreNotPassed() throws Exception {
        assertThat(body("""
                const C = ({title, user}) => <h1 title={user.name}>{title} {user?.role && <i>{user.role}</i>}</h1>
                export default C
                """)).isEqualTo("<h1 title=\"{=user.or('').name??}\">{=title??} "
                + "{#if user.or('').role??}<i>{=user.or('').role??}</i>{/if}</h1>");
    }

    @Test
    void convertsFallbacksForValuesInsideProps() throws Exception {
        assertThat(body("""
                const C = ({user}) => <p>{user.name || 'Anonymous'}</p>
                export default C
                """)).isEqualTo("<p>{=user.or('').name ?: 'Anonymous'}</p>");
    }

    @Test
    void convertsChildrenToInsert() throws Exception {
        assertThat(body("""
                const C = ({children}) => <main>{children}</main>
                export default C
                """)).isEqualTo("<main>{#insert /}</main>");
    }

    @Test
    void convertsConditionsAndTernaries() throws Exception {
        assertThat(body("""
                const C = ({a, b}) => (
                  <p>
                    {a && <b>A</b>}
                    {!b ? (<i>not B</i>) : null}
                    {a ? <span>yes</span> : <span>no</span>}
                  </p>
                )
                export default C
                """)).isEqualTo("<p>{#if a??}<b>A</b>{/if}{#if !b??}<i>not B</i>{/if}"
                + "{#if a??}<span>yes</span>{#else}<span>no</span>{/if}</p>");
    }

    @Test
    void convertsConditionsWithComparisonsAndSeveralOperands() throws Exception {
        assertThat(body("""
                const C = ({kind, count, user}) => (
                  <p>
                    {kind === "home" || count > 3 || <i>other</i>}
                    {kind !== 'away' && count <= 10 && <b>near</b>}
                    {!(kind == "a" || user.admin) ? <u>neither</u> : null}
                  </p>
                )
                export default C
                """)).isEqualTo("<p>{#if ! ((kind?? == 'home') || (count?? > 3))}<i>other</i>{/if}"
                + "{#if (kind?? != 'away') && (count?? <= 10)}<b>near</b>{/if}"
                + "{#if ! ((kind?? == 'a') || user.or('').admin??)}<u>neither</u>{/if}</p>");
    }

    @Test
    void convertsTemplateLiteralsAndFallbacks() throws Exception {
        assertThat(body("""
                const C = ({page, title}) => <a href={`/edit/${page}.md`} title={title || 'Untitled'}>Edit</a>
                export default C
                """)).isEqualTo("<a href=\"/edit/{=page??}.md\" title=\"{=title ?: 'Untitled'}\">Edit</a>");
    }

    @Test
    void convertsSiteMetadataFromAStaticQuery() throws Exception {
        assertThat(body("""
                const C = () => {
                  const data = useStaticQuery(graphql`query { site { siteMetadata { title description } } }`)
                  return <p title={data.site.siteMetadata?.description}>{data.site.siteMetadata.title}</p>
                }
                export default C
                """)).isEqualTo("<p title=\"{=site.description}\">{=site.title}</p>");
    }

    @Test
    void convertsListsOfPagesFromAStaticQuery() throws Exception {
        assertThat(
                body("""
                        import {Link} from "gatsby"
                        const C = () => {
                          const data = useStaticQuery(graphql`{ allMarkdownRemark { edges { node { fields { slug } frontmatter { title } } } } }`)
                          return <ul>{data.allMarkdownRemark.edges.map((edge, i) => edge.node.fields.slug.startsWith("_") || (
                            <li key={i} data-i={i}><Link to={"/" + edge.node.fields.slug}>{edge.node.frontmatter.title}</Link></li>
                          ))}</ul>
                        }
                        export default C
                        """))
                .isEqualTo("""
                        <ul>
                          {#for edge in site.pages}
                            {#if edge.markdown}
                              {#if !edge.slug.startsWith('_')}
                                <li data-i="{=edge_index}"><a href="{=edge.url}">{=edge.title}</a></li>
                              {/if}
                            {/if}
                          {/for}
                        </ul>""");
    }

    @Test
    void convertsListsOfNodesAndRenamesItemsThatHideRoqNames() throws Exception {
        assertThat(
                body("""
                        import {Link} from "gatsby"
                        const C = () => {
                          const {allMarkdownRemark} = useStaticQuery(graphql`{ allMarkdownRemark { nodes { fields { slug } } } }`)
                          return <p>{allMarkdownRemark.nodes.map(page => <Link to={`/${page.fields.slug}/`}>{page.fields.slug.toUpperCase()}</Link>)}</p>
                        }
                        export default C
                        """))
                .isEqualTo("<p>{#for page_ in site.pages}{#if page_.markdown}<a href=\"{=page_.url}\">"
                        + "{=page_.slug.toUpperCase()}</a>{/if}{/for}</p>");
    }

    @Test
    void convertsConcatenations() throws Exception {
        assertThat(body("""
                const C = ({name}) => <a href={"/people/" + name + "/"} title={'Hi ' + name}>{"@" + name}</a>
                export default C
                """)).isEqualTo("<a href=\"/people/{=name??}/\" title=\"Hi {=name??}\">@{=name??}</a>");
    }

    @Test
    void convertsGatsbyComponents() throws Exception {
        assertThat(body("""
                import {Link, Script} from "gatsby"
                import {StaticImage} from "gatsby-plugin-image"
                const C = () => (
                  <>
                    <Link to="/about/" className="nav">About</Link>
                    <StaticImage src="../images/logo.png" alt="Logo" width={36} placeholder="none" layout="fixed"/>
                    <Script defer src="/a.js"/>
                  </>
                )
                export default C
                """)).isEqualTo("<a href=\"/about/\" class=\"nav\">About</a>"
                + "<img src=\"{=site.url('images/logo.png')}\" alt=\"Logo\" width=\"36\" style=\"margin: 0; object-fit: cover\">"
                + "<script defer src=\"/a.js\"></script>");
    }

    /**
     * The invisible image that gives a constrained Gatsby image its size.
     */
    static String sizer(int height, int width) {
        return "<img alt=\"\" role=\"presentation\" aria-hidden=\"true\" src=\"data:image/svg+xml;charset=utf-8,%3Csvg%20"
                + "height='" + height + "'%20width='" + width + "'%20xmlns='http://www.w3.org/2000/svg'%20version='1.1'"
                + "%3E%3C/svg%3E\" style=\"max-width: 100%; display: block; position: static\">";
    }

    @Test
    void convertsGatsbyImagesAsGatsbyRendersThem() throws Exception {
        JsxToQuteConverter.Converted converted = JsxToQuteConverter.convert(ReactComponent.read("src/components/c.js", """
                import {StaticImage} from "gatsby-plugin-image"
                const C = () => <StaticImage src="../images/wide.png" alt="Wide" height={36} backgroundColor="white"/>
                export default C
                """), CONTEXT);

        // Constrained: at most as wide as the image at that height, which an invisible SVG of that size sets
        assertThat(converted.template()).contains("<div data-gatsby-image-wrapper class=\"gatsby-image-wrapper "
                + "gatsby-image-wrapper-constrained\" style=\"background-color: white\">"
                + "<div style=\"max-width: 72px; display: block\">" + sizer(36, 72) + "</div>"
                + "<img src=\"{=site.url('images/wide.png')}\" alt=\"Wide\" style=\"object-fit: cover; background-color: white\" "
                + "decoding=\"async\" loading=\"lazy\"></div>");
        assertThat(converted.images()).isTrue();
    }

    @Test
    void sizesGatsbyImagesForTheirLayout() throws Exception {
        // The image's own width, and its aspect ratio
        assertThat(body("""
                import {StaticImage} from "gatsby-plugin-image"
                const C = () => <StaticImage src="../images/wide.png" alt="" layout="fixed"/>
                export default C
                """)).startsWith("<div data-gatsby-image-wrapper class=\"gatsby-image-wrapper\" "
                + "style=\"width: 200px; height: 100px\"><img");
        // The aspect ratio given; a full width image is a block
        assertThat(body("""
                import {StaticImage} from "gatsby-plugin-image"
                const C = () => <StaticImage src="../images/wide.png" alt="" layout="fullWidth" aspectRatio={16 / 9}/>
                export default C
                """)).isEqualTo("""
                <div data-gatsby-image-wrapper class="gatsby-image-wrapper">
                  <div aria-hidden="true" style="padding-top: 56.25%"></div>
                  <img src="{=site.url('images/wide.png')}" alt="" style="object-fit: cover" decoding="async" loading="lazy">
                </div>""");
        // Both dimensions given, whatever the image's size
        assertThat(body("""
                import {StaticImage} from "gatsby-plugin-image"
                const C = () => <StaticImage src="../images/wide.png" alt="" width={50} height={50}/>
                export default C
                """)).contains("<div style=\"max-width: 50px; display: block\">" + sizer(50, 50) + "</div>");
    }

    @Test
    void putsTheClassAndStyleOfGatsbyImagesOnTheWrapperAndTheImgOnesOnTheImage() throws Exception {
        assertThat(body("""
                import {StaticImage} from "gatsby-plugin-image"
                const Framed = styled(StaticImage)`border: 1px solid;`
                const C = () => <Framed src="../images/wide.png" alt="" className="logo" style={{margin: 4}}
                    imgClassName="photo" imgStyle={{opacity: 0.5}} objectFit="contain" objectPosition="top" title="T"/>
                export default C
                """)).isEqualTo("<div data-gatsby-image-wrapper class=\"gatsby-image-wrapper gatsby-image-wrapper-constrained "
                + "c-framed logo\" style=\"margin: 4px\"><div style=\"max-width: 200px; display: block\">"
                + sizer(100, 200) + "</div><img src=\"{=site.url('images/wide.png')}\" alt=\"\" "
                + "class=\"photo\" title=\"T\" style=\"opacity: 0.5; object-fit: contain; object-position: top\" "
                + "decoding=\"async\" loading=\"lazy\"></div>");
    }

    @Test
    void givesGatsbyImagesOfUnknownSizeTheirLook() throws Exception {
        assertThat(body("""
                import {StaticImage} from "gatsby-plugin-image"
                const C = () => <StaticImage src="../images/logo.png" alt="Logo" style={{width: 36}} backgroundColor="white"
                    objectFit={"contain"} objectPosition="top"/>
                export default C
                """)).isEqualTo("<img src=\"{=site.url('images/logo.png')}\" alt=\"Logo\" "
                + "style=\"width: 36px; margin: 0; object-fit: contain; object-position: top; background-color: white\">");
    }

    @Test
    void convertsFontAwesomeIcons() throws Exception {
        JsxToQuteConverter.Converted converted = JsxToQuteConverter.convert(ReactComponent.read("c.js", """
                import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
                import {faGithub} from "@fortawesome/free-brands-svg-icons"
                const C = () => <FontAwesomeIcon icon={faGithub} className="big"/>
                export default C
                """), CONTEXT);

        assertThat(converted.template()).contains("<i class=\"fa-brands fa-github big\" aria-hidden=\"true\"></i>");
        assertThat(converted.icons()).isTrue();
    }

    @Test
    void convertsStyledComponentsToClasses() throws Exception {
        JsxToQuteConverter.Converted converted = JsxToQuteConverter.convert(
                ReactComponent.read("src/components/site-footer.js",
                        """
                                import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
                                import {faGithub} from "@fortawesome/free-brands-svg-icons"
                                const FooterBar = styled.footer`
                                  color: white;
                                `
                                const Logo = styled(props => <a {...props} />)`
                                  margin: 7px;
                                  &:visited {
                                    color: white;
                                  }
                                `
                                const Icon = styled(FontAwesomeIcon)`margin-left: 5px;`
                                const Footer = () => <FooterBar className="nav"><Logo href="/">Home<Icon icon={faGithub}/></Logo></FooterBar>
                                export default Footer
                                """),
                CONTEXT);

        assertThat(converted.template()).contains("<footer class=\"site-footer-footer-bar nav\"><a href=\"/\" "
                + "class=\"site-footer-logo\">Home<i class=\"fa-brands fa-github site-footer-icon\" aria-hidden=\"true\">"
                + "</i></a></footer>");
        assertThat(converted.css()).isEqualTo("""
                .site-footer-footer-bar {
                  color: white;
                }

                .site-footer-logo {
                  margin: 7px;
                  &:visited {
                    color: white;
                  }
                }

                .site-footer-icon {
                  margin-left: 5px;
                }
                """);
    }

    @Test
    void convertsLocalComponentsToIncludes() throws Exception {
        assertThat(body("""
                import Header from "./header"
                import Box from "./box"
                const C = ({title, children}) => (
                  <>
                    <Header siteTitle={title || `Title`} count={3} label="Hi"/>
                    <Box>{children}</Box>
                  </>
                )
                export default C
                """)).isEqualTo("{#include partials/header siteTitle=(title ?: 'Title') count=3 label='Hi' /}"
                + "{#include partials/box}{#insert /}{/include}");
    }

    @Test
    void makesIncludesForWhatALayoutRendersAroundItsContent() {
        ReactComponent layout = ReactComponent.read("layout.js", """
                import Header from "./header"
                import Footer from "./footer"
                import Nav from "./broken"
                const Layout = ({children, pageName}) => {
                  const data = useStaticQuery(graphql`query { site { siteMetadata { title } } }`)
                  return (
                    <>
                      <Header siteTitle={data.site.siteMetadata.title} pageName={pageName}/>
                      <div><Nav/><main>{children}</main></div>
                      <Footer/>
                    </>
                  )
                }
                export default Layout
                """);

        List<List<JsxToQuteConverter.Include>> around = JsxToQuteConverter.includesAround(layout, CONTEXT);

        assertThat(around.get(0)).containsExactly(
                new JsxToQuteConverter.Include("{#include partials/header siteTitle=site.title /}", List.of("pageName")));
        assertThat(around.get(1)).containsExactly(new JsxToQuteConverter.Include("{#include partials/footer /}", List.of()));
    }

    @Test
    void saysWhyItCannotConvert() {
        // Each component, then why it cannot be converted
        List<String> reasons = List.of(
                "const C = () => <ul>{items.map(i => <li>{i}</li>)}</ul>\nexport default C",
                "it renders a list of items with map()",
                "const C = () => <ul>{f().map(i => <li>{i}</li>)}</ul>\nexport default C",
                "it renders a list with map()",
                "const C = () => {\n const data = useStaticQuery(graphql`{ allMarkdownRemark { nodes { id } } }`)\n"
                        + " return <ul>{data.allMarkdownRemark.nodes.map(({id}) => <li>{id}</li>)}</ul> }\nexport default C",
                "it renders a list with map() and destructures its items ({id})",
                "const C = () => {\n const data = useStaticQuery(graphql`{ allMarkdownRemark { nodes { id } } }`)\n"
                        + " return <ul>{data.allMarkdownRemark.nodes.map(n => <li>{n.id}</li>)}</ul> }\nexport default C",
                "it uses id of the pages it lists, which Roq has no equivalent for",
                "const C = () => {\n const data = useStaticQuery(graphql`{ allMarkdownRemark(limit: 5) { nodes { id } } }`)\n"
                        + " return <p/> }\nexport default C",
                "it filters or sorts what it queries with useStaticQuery",
                "const C = () => { console.log(1)\n return <p/> }\nexport default C",
                "it runs code before rendering (console.log(1))",
                "const C = () => <p>{Math.max(1, 2)}</p>\nexport default C",
                "it uses the expression Math.max(1, 2)",
                "const C = () => <p>{other}</p>\nexport default C",
                "it uses other, which is not a prop or siteMetadata",
                "import Nav from './broken'\nconst C = () => <Nav/>\nexport default C",
                "it uses <Nav> from ./broken, which could not be converted",
                "import {Thing} from 'library'\nconst C = () => <Thing/>\nexport default C",
                "it uses <Thing> from library",
                "const C = ({html}) => <div dangerouslySetInnerHTML={{__html: html}}/>\nexport default C",
                "it sets raw HTML with dangerouslySetInnerHTML, from html, which is not the HTML of a page",
                "const C = () => {\n const data = useStaticQuery(graphql`{ allMarkdownRemark { totalCount } }`)\n"
                        + " return <p/> }\nexport default C",
                "it queries allMarkdownRemark with useStaticQuery");
        for (int i = 0; i < reasons.size(); i += 2) {
            assertThat(unsupported(reasons.get(i))).as(reasons.get(i)).isEqualTo(reasons.get(i + 1));
        }
    }

    @Test
    void namesTheComponentItConvertedAndItsParameters() throws Exception {
        String template = JsxToQuteConverter.convert(ReactComponent.read("src/components/header.js",
                "const H = ({siteTitle, children}) => <h1>{siteTitle}</h1>\nexport default H"), CONTEXT).template();

        assertThat(template).startsWith("{! Converted from src/components/header.js by the migration to Roq; "
                + "parameters: siteTitle !}\n");
    }
}
