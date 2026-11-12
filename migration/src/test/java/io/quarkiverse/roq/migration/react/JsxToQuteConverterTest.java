package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class JsxToQuteConverterTest {

    /**
     * Local components convert to partials/<name>, except ./broken; images are under src/images; allMarkdownRemark
     * lists the Markdown pages, served at fields.slug.
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

    @Test
    void keepsTheLookOfGatsbyImages() throws Exception {
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
                "const C = () => { const x = 1\n return <p>{x}</p> }\nexport default C",
                "it runs code before rendering (const x = 1)",
                "const C = () => <p>{Math.max(1, 2)}</p>\nexport default C",
                "it uses the expression Math.max(1, 2)",
                "const C = () => <p>{other}</p>\nexport default C",
                "it uses other, which is not a prop or siteMetadata",
                "import Nav from './broken'\nconst C = () => <Nav/>\nexport default C",
                "it uses <Nav> from ./broken, which could not be converted",
                "import {Thing} from 'library'\nconst C = () => <Thing/>\nexport default C",
                "it uses <Thing> from library",
                "const C = ({html}) => <div dangerouslySetInnerHTML={{__html: html}}/>\nexport default C",
                "it sets raw HTML with dangerouslySetInnerHTML",
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
