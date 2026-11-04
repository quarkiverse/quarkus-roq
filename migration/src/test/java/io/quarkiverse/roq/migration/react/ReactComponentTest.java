package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.roq.migration.react.ReactComponent.Kind;

class ReactComponentTest {

    @Test
    void readsThePageQueryAndTheHeadOfAPage() {
        ReactComponent page = ReactComponent.read("src/pages/404.js", """
                const NotFound = ({data}) => <Layout><h1>{data.site.siteMetadata.title}</h1></Layout>
                export const query = graphql`query { site { siteMetadata { title } } }`
                export const Head = () => <Seo title="404: Not Found" />
                export default NotFound
                """);

        assertThat(page.pageQuery()).isEqualTo("query { site { siteMetadata { title } } }");
        assertThat(page.head()).startsWith("<Seo title=\"404: Not Found\" />");
        assertThat(page.jsx()).startsWith("<Layout>");
        assertThat(ReactComponent.read("c.js", "const C = () => <p/>\nexport default C").head()).isNull();
    }

    @Test
    void readsAStaticComponent() {
        ReactComponent header = ReactComponent.read("src/components/header.js", """
                import * as React from "react"
                import {Link} from "gatsby"
                import Logo, {Small as Tiny} from "./logo"

                const Header = ({siteTitle, pageName = "home"}) => (
                  <header><Link to="/">{siteTitle}</Link></header>
                )

                export default Header
                """);

        assertThat(header.name()).isEqualTo("Header");
        assertThat(header.kind()).isEqualTo(Kind.STATIC);
        assertThat(header.props()).containsExactly("siteTitle", "pageName");
        assertThat(header.prelude()).isEmpty();
        assertThat(header.jsx()).isEqualTo("<header><Link to=\"/\">{siteTitle}</Link></header>");
        assertThat(header.imports()).containsEntry("Link", new ReactComponent.Import("gatsby", "Link"))
                .containsEntry("Logo", new ReactComponent.Import("./logo", "default"))
                .containsEntry("Tiny", new ReactComponent.Import("./logo", "Small"))
                .containsEntry("React", new ReactComponent.Import("react", "*"));
    }

    @Test
    void readsAFunctionWithABlockBodyAndAQuery() {
        ReactComponent layout = ReactComponent.read("layout.js", """
                export default function Layout({children}) {
                  const data = useStaticQuery(graphql`query { site { siteMetadata { title } } }`)
                  const title = data.site.siteMetadata.title
                  return (
                    <main title={title}>{children}</main>
                  );
                }
                """);

        assertThat(layout.queries()).containsEntry("data", "query { site { siteMetadata { title } } }");
        assertThat(layout.prelude()).isEqualTo("const title = data.site.siteMetadata.title");
        assertThat(layout.jsx()).isEqualTo("<main title={title}>{children}</main>");
    }

    @Test
    void readsStyledComponents() {
        ReactComponent footer = ReactComponent.read("footer.js", """
                const Bar = styled.footer`
                  color: white;
                `
                const Logo = styled(props => <a {...props} />)`margin: 0;`
                const Icon = styled(FontAwesomeIcon)`margin-left: ${gap}px;`
                const Footer = () => <Bar/>
                export default Footer
                """);

        assertThat(footer.styled()).containsEntry("Bar", new ReactComponent.Styled("footer", "  color: white;"))
                .containsEntry("Logo", new ReactComponent.Styled("a", "margin: 0;"))
                .containsEntry("Icon", new ReactComponent.Styled("FontAwesomeIcon", null));
    }

    @Test
    void classifiesInteractiveComponents() {
        ReactComponent search = ReactComponent.read("search.js", """
                export default function Search() {
                  const [q, setQ] = useState("")
                  return <input value={q} onChange={e => setQ(e.target.value)}/>
                }
                """);

        assertThat(search.kind()).isEqualTo(Kind.INTERACTIVE);
        assertThat(search.reasons()).containsExactly("event handlers (onChange)", "state (useState)");
    }

    @Test
    void classifiesComponentsThatComputeInTheBrowser() {
        ReactComponent stars = ReactComponent.read("stars.js", """
                export default function Stars() {
                  const [n, setN] = useState(0)
                  useEffect(() => { fetch("/api").then(r => r.json()).then(setN) }, [])
                  return <span>{n}</span>
                }
                """);

        assertThat(stars.kind()).isEqualTo(Kind.COMPUTED_IN_BROWSER);
        assertThat(stars.reasons()).containsExactly("state set from useEffect, fetch");
    }

    @Test
    void classifiesComponentsWithSideEffectsOnly() {
        ReactComponent analytics = ReactComponent.read("analytics.js", """
                export default function Analytics() {
                  useEffect(() => { window.dataLayer = [] }, [])
                  return <Script src="https://example.org/a.js"/>
                }
                """);

        assertThat(analytics.kind()).isEqualTo(Kind.SIDE_EFFECTS);
        assertThat(analytics.reasons()).containsExactly(
                "scripts that do not change what it renders (useEffect, window, <Script>)");
    }

    @Test
    void ignoresCommentsWhenClassifying() {
        ReactComponent note = ReactComponent.read("note.js", """
                // Was <button onClick={toggle}> with useState, before
                const Note = () => <p>Note</p>
                export default Note
                """);

        assertThat(note.kind()).isEqualTo(Kind.STATIC);
    }
}
