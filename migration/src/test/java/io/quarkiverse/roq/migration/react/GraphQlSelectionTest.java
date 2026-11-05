package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class GraphQlSelectionTest {

    @Test
    void readsTheSelectedFields() throws Exception {
        GraphQlSelection selection = GraphQlSelection.parse("""
                query ContentQuery {
                  site { siteMetadata { title, description } }
                  allMarkdownRemark { edges { node { fields { slug title } } } }
                }
                """);

        assertThat(selection.fields().keySet()).containsExactly("site", "allMarkdownRemark");
        assertThat(selection.get("site").get("siteMetadata").fields().keySet()).containsExactly("title", "description");
        assertThat(selection.get("allMarkdownRemark").get("edges").get("node").get("fields").fields().keySet())
                .isEqualTo(Set.of("slug", "title"));
        assertThat(selection.get("site").get("siteMetadata").get("title").isLeaf()).isTrue();
        assertThat(selection.arguments()).isFalse();
    }

    @Test
    void tellsWhenAFieldHasArguments() throws Exception {
        assertThat(GraphQlSelection.parse("{ allMarkdownRemark(sort: {frontmatter: {date: DESC}}) { nodes { id } } }")
                .arguments()).isTrue();
        assertThat(GraphQlSelection.parse("query($id: String!) { markdownRemark(id: { eq: $id }) { html } }")
                .arguments()).isTrue();
    }

    @Test
    void refusesWhatItDoesNotRead() {
        assertThatThrownBy(() -> GraphQlSelection.parse("{ posts: allMarkdownRemark { nodes { id } } }"))
                .hasMessage("aliases are not read");
        assertThatThrownBy(() -> GraphQlSelection.parse("{ site { ...Metadata } }")).hasMessage("unexpected '.'");
        assertThatThrownBy(() -> GraphQlSelection.parse("{ site { siteMetadata ")).hasMessage("unclosed {");
    }
}
