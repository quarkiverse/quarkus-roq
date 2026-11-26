package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
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
        assertThat(selection.hasArguments()).isFalse();
    }

    @Test
    void tellsWhenAFieldHasArguments() throws Exception {
        assertThat(GraphQlSelection.parse("{ allMarkdownRemark(sort: {frontmatter: {date: DESC}}) { nodes { id } } }")
                .hasArguments()).isTrue();
        assertThat(GraphQlSelection.parse("query($id: String!) { markdownRemark(id: { eq: $id }) { html } }")
                .hasArguments()).isTrue();
    }

    @Test
    void keepsTheArgumentsOfEachField() throws Exception {
        GraphQlSelection selection = GraphQlSelection.parse("""
                query Home { markdownRemark(fields: {slug: {eq: "home"}}) { html } }
                """);

        assertThat(selection.get("markdownRemark").arguments()).isEqualTo("fields: {slug: {eq: \"home\"}}");
        assertThat(selection.get("markdownRemark").get("html").arguments()).isNull();
    }

    @Test
    void readsFiltersThatTestFieldsForEquality() {
        assertThat(GraphQlSelection.equalities("fields: {slug: {eq: \"home\"}}"))
                .containsExactly(Map.entry("fields.slug", "home"));
        assertThat(GraphQlSelection.equalities("frontmatter: {lang: {eq: \"en\"}, kind: {eq: \"home\"}}"))
                .containsExactly(Map.entry("frontmatter.lang", "en"), Map.entry("frontmatter.kind", "home"));
        // Variables, other operators and sorting are not equalities with strings
        assertThat(GraphQlSelection.equalities("id: {eq: $id}")).isNull();
        assertThat(GraphQlSelection.equalities("fields: {slug: {ne: \"home\"}}")).isNull();
        assertThat(GraphQlSelection.equalities("sort: {frontmatter: {date: DESC}}")).isNull();
        assertThat(GraphQlSelection.equalities(null)).isNull();
    }

    @Test
    void refusesWhatItDoesNotRead() {
        assertThatThrownBy(() -> GraphQlSelection.parse("{ posts: allMarkdownRemark { nodes { id } } }"))
                .hasMessage("aliases are not read");
        assertThatThrownBy(() -> GraphQlSelection.parse("{ site { ...Metadata } }")).hasMessage("unexpected '.'");
        assertThatThrownBy(() -> GraphQlSelection.parse("{ site { siteMetadata ")).hasMessage("unclosed {");
    }
}
