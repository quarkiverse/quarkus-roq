package io.quarkiverse.roq.data.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.quarkiverse.roq.data.runtime.DataFormat;
import io.quarkiverse.roq.data.runtime.DataSerializer;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;

class RoqDataSerializerTest {

    private final DataSerializer serializer = new DataSerializer();

    public record Link(String name, String url) {
    }

    @DataMapping("site")
    public record Site(String title, int count, Double ratio, List<Link> links, Map<String, String> labels) {

        public String getComputed() {
            return "computed " + title;
        }
    }

    public record Person(String name, int age) {
    }

    @DataMapping(value = "people", type = DataMapping.Type.ARRAY_FILE)
    public record People(List<Person> list) {
    }

    @DataMapping(value = "dir", type = DataMapping.Type.ARRAY_DIR)
    public record Dir(List<Person> list) {
    }

    @DataMapping("settings")
    public static class Settings {
        private String theme;
        private List<String> tags;

        public String getTheme() {
            return theme;
        }

        public void setTheme(String theme) {
            this.theme = theme;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }

    private static final String SITE_YAML = """
            # Site configuration
            # Edited by hand

            title: My Site # the title
            count: 3
            ratio: 1.50

            # Not bound to the Site record
            extra: keep me

            links:
              # The main link
              - name: home
                url: /
              # Docs
              - name: docs
                url: /docs # documentation
            labels:
              a: "A"
              b: B
            """;

    @Test
    void yamlUnchangedIsIdentical() {
        Site site = new Site("My Site", 3, 1.5, List.of(new Link("home", "/"), new Link("docs", "/docs")),
                ordered("a", "A", "b", "B"));
        assertThat(serializer.write(site, SITE_YAML, DataFormat.YAML)).isEqualTo(SITE_YAML);
    }

    @Test
    void unchangedDataKeepsTheOriginalLayout() {
        String yaml = """
                description: >
                  folded text
                  on two lines
                nested:
                    deep: 1
                """;
        record Doc(String description, Map<String, Integer> nested) {
        }
        Doc doc = new Doc("folded text on two lines\n", Map.of("deep", 1));
        assertThat(serializer.write(doc, yaml, DataFormat.YAML)).isSameAs(yaml);
        String json = "{ \"name\" :   \"Alice\", \"age\": 30 }";
        assertThat(serializer.write(new Person("Alice", 30), json, DataFormat.JSON)).isSameAs(json);
    }

    @Test
    void yamlScalarsAreReadLikeSnakeYaml() {
        record Flags(int mask, boolean enabled, double size, String date) {
        }
        String yaml = """
                mask: 0x1F # hex
                enabled: yes
                size: 1.5e3
                date: 2026-11-11
                """;
        assertThat(serializer.write(new Flags(31, true, 1500, "2026-11-11"), yaml, DataFormat.YAML)).isEqualTo(yaml);
        assertThat(serializer.write(new Flags(31, true, 1500, "2026-11-12"), yaml, DataFormat.YAML)).isEqualTo("""
                mask: 0x1F # hex
                enabled: yes
                size: 1.5e3
                date: 2026-11-12
                """);
    }

    @Test
    void javaTimeValuesAreWrittenAsIsoText() {
        record Release(String version, LocalDate releaseDate, LocalDateTime cut, Instant published) {
        }
        String yaml = """
                version: "3.20"
                releaseDate: 2025-03-26 # community GA
                cut: 2025-03-19T08:09:29
                published: 2025-03-26T10:00:00Z
                """;
        Release release = new Release("3.20", LocalDate.of(2025, 3, 26), LocalDateTime.of(2025, 3, 19, 8, 9, 29),
                Instant.parse("2025-03-26T10:00:00Z"));
        assertThat(serializer.write(release, yaml, DataFormat.YAML)).isEqualTo(yaml);
        Release moved = new Release("3.20", LocalDate.of(2025, 3, 27), release.cut(), release.published());
        assertThat(serializer.write(moved, yaml, DataFormat.YAML)).isEqualTo("""
                version: "3.20"
                releaseDate: 2025-03-27 # community GA
                cut: 2025-03-19T08:09:29
                published: 2025-03-26T10:00:00Z
                """);
        String json = """
                {
                  "version": "3.20",
                  "releaseDate": "2025-03-26",
                  "cut": "2025-03-19T08:09:29",
                  "published": "2025-03-26T10:00:00Z"
                }
                """;
        assertThat(serializer.write(moved, json, DataFormat.JSON)).isEqualTo(json.replace("2025-03-26\"", "2025-03-27\""));
    }

    @Test
    void yamlPreservesComments() {
        Site site = new Site("New Title", 4, 1.5,
                List.of(new Link("home", "/"), new Link("blog", "/blog"), new Link("docs", "/documentation")),
                ordered("a", "A", "c", "true"));
        assertThat(serializer.write(site, SITE_YAML, DataFormat.YAML)).isEqualTo("""
                # Site configuration
                # Edited by hand

                title: New Title # the title
                count: 4
                ratio: 1.50

                # Not bound to the Site record
                extra: keep me

                links:
                  # The main link
                  - name: home
                    url: /
                  - name: blog
                    url: /blog
                  # Docs
                  - name: docs
                    url: /documentation # documentation
                labels:
                  a: "A"
                  c: 'true'
                """);
    }

    @Test
    void yamlNullRemovesKey() {
        Site site = new Site(null, 3, null, List.of(), ordered());
        assertThat(serializer.write(site, SITE_YAML, DataFormat.YAML)).isEqualTo("""
                # Site configuration
                # Edited by hand

                count: 3

                # Not bound to the Site record
                extra: keep me

                links: []
                labels: {}
                """);
    }

    @Test
    void yamlArrayFile() {
        String original = """
                # People
                - name: Alice # first
                  age: 30
                # Bob is here
                - name: Bob
                  age: 40
                - name: Carol
                  age: 50
                """;
        People people = new People(List.of(new Person("Alice", 31), new Person("Carol", 50), new Person("Dave", 20)));
        assertThat(serializer.write(people, original, DataFormat.YAML)).isEqualTo("""
                # People
                - name: Alice # first
                  age: 31
                - name: Carol
                  age: 50
                - name: Dave
                  age: 20
                """);
    }

    @Test
    void yamlSequenceCommentsWithBlankLines() {
        String original = """
                # People

                # Alice
                # is the first
                - name: Alice
                  age: 30

                # Section B
                - name: Bob # bob
                  age: 40
                """;
        People people = new People(List.of(new Person("Alice", 30), new Person("Bob", 41)));
        assertThat(serializer.write(people, original, DataFormat.YAML)).isEqualTo("""
                # People

                # Alice
                # is the first
                - name: Alice
                  age: 30

                # Section B
                - name: Bob # bob
                  age: 41
                """);
    }

    public record Entry(String version, String releaseDate, Boolean upcoming, String latestPatch) {
    }

    @DataMapping("releases")
    public record ReleaseList(List<Entry> releases) {
    }

    @Test
    void yamlChangedStringsKeepTheirStyle() {
        record Text(String title, String description) {
        }
        String original = """
                title: "Old"
                description: >
                  old text
                """;
        assertThat(serializer.write(new Text("New", "new text\n"), original, DataFormat.YAML)).isEqualTo("""
                title: "New"
                description: >
                  new text
                """);
    }

    @Test
    void yamlNewItemsLookLikeTheirSiblings() {
        String original = """
                # header
                releases:

                  - version: "3.40"
                    release_date: 2026-09-30
                    latest_patch: "3.40.1"

                  # old one
                  - version: "3.39"
                    release_date: 2026-08-26
                    latest_patch: "3.39.5"
                """;
        DataSerializer snake = new DataSerializer(PropertyNamingStrategies.SNAKE_CASE);
        ReleaseList releases = new ReleaseList(List.of(
                new Entry("4.0", null, true, null),
                new Entry("3.41", "2026-10-28", null, "3.41.0"),
                new Entry("3.40", "2026-09-30", null, "3.40.2"),
                new Entry("3.39", "2026-08-26", null, "3.39.5")));
        assertThat(snake.write(releases, original, DataFormat.YAML)).isEqualTo("""
                # header
                releases:

                  - version: "4.0"
                    upcoming: true

                  - version: "3.41"
                    release_date: 2026-10-28
                    latest_patch: "3.41.0"

                  - version: "3.40"
                    release_date: 2026-09-30
                    latest_patch: "3.40.2"

                  # old one
                  - version: "3.39"
                    release_date: 2026-08-26
                    latest_patch: "3.39.5"
                """);
    }

    @Test
    void yamlNewKeysFollowTheBeanOrder() {
        String original = """
                releases:
                  - version: "4.0"
                    planned_date: 2026-11-25
                    upcoming: true
                """;
        DataSerializer snake = new DataSerializer(PropertyNamingStrategies.SNAKE_CASE);
        ReleaseList releases = new ReleaseList(List.of(new Entry("4.0", "2026-11-26", null, "4.0.0")));
        // planned_date is not bound to Entry, so it is kept
        assertThat(snake.write(releases, original, DataFormat.YAML)).isEqualTo("""
                releases:
                  - version: "4.0"
                    release_date: 2026-11-26
                    latest_patch: 4.0.0
                    planned_date: 2026-11-25
                """);
    }

    public record Provider(String eolDate) {
    }

    public record Version(String version, String latestPatch, Provider rhbq) {
    }

    @DataMapping("versions")
    public record Versions(String description, List<Version> releases) {
    }

    @Test
    void yamlOnlyChangedPartsAreRewritten() {
        String original = """
                description: >
                  folded text
                  on two lines

                releases:

                  - version: "3.40"
                    latest_patch: "3.40.1"
                    rhbq:
                        eol_date: 2028-06-30

                  - version: "3.39"
                    latest_patch: "3.39.5"
                """;
        DataSerializer snake = new DataSerializer(PropertyNamingStrategies.SNAKE_CASE);
        Versions versions = new Versions("folded text on two lines\n", List.of(
                new Version("3.41", "3.41.0", null),
                new Version("3.40", "3.40.2", new Provider("2028-06-30")),
                new Version("3.39", "3.39.5", null)));
        assertThat(snake.write(versions, original, DataFormat.YAML)).isEqualTo("""
                description: >
                  folded text
                  on two lines

                releases:

                  - version: "3.41"
                    latest_patch: "3.41.0"

                  - version: "3.40"
                    latest_patch: "3.40.2"
                    rhbq:
                        eol_date: 2028-06-30

                  - version: "3.39"
                    latest_patch: "3.39.5"
                """);
    }

    @Test
    void yamlInsertBetweenSeparatedItems() {
        String original = """
                - name: Alice
                  age: 30

                - name: Carol
                  age: 50
                """;
        People people = new People(List.of(new Person("Alice", 30), new Person("Bob", 40), new Person("Carol", 50)));
        assertThat(serializer.write(people, original, DataFormat.YAML)).isEqualTo("""
                - name: Alice
                  age: 30

                - name: Bob
                  age: 40

                - name: Carol
                  age: 50
                """);
        // and removing it again
        People two = new People(List.of(new Person("Alice", 30), new Person("Carol", 50)));
        assertThat(serializer.write(two, serializer.write(people, original, DataFormat.YAML), DataFormat.YAML))
                .isEqualTo(original);
    }

    @Test
    void yamlRemoveTheFirstKeyOfAnItem() {
        record Items(List<Map<String, Integer>> items) {
        }
        String original = """
                items:
                  - a: 1 # one
                    b: 2
                  - c: 3
                """;
        assertThat(serializer.write(new Items(List.of(Map.of("b", 2), Map.of("c", 3))), original, DataFormat.YAML))
                .isEqualTo("""
                        items:
                          - b: 2
                          - c: 3
                        """);
    }

    @Test
    void yamlLineEndings() {
        String crlf = "# comment\r\nname: Alice\r\nage: 30\r\n";
        assertThat(serializer.write(new Person("Bob", 30), crlf, DataFormat.YAML))
                .isEqualTo("# comment\r\nname: Bob\r\nage: 30\r\n");
        String noTrailingLineBreak = "- name: Alice\n  age: 30";
        assertThat(serializer.write(new People(List.of(new Person("Alice", 30), new Person("Bob", 40))),
                noTrailingLineBreak, DataFormat.YAML))
                .isEqualTo("- name: Alice\n  age: 30\n- name: Bob\n  age: 40");
    }

    @Test
    void yamlClassBean() {
        Settings settings = new Settings();
        settings.setTheme("dark");
        settings.setTags(List.of("a", "b"));
        String original = """
                theme: light  # default theme
                tags: [a]
                """;
        assertThat(serializer.write(settings, original, DataFormat.YAML)).isEqualTo("""
                theme: dark  # default theme
                tags: [a, b]
                """);
    }

    @Test
    void yamlFromScratch() {
        Site site = new Site("Title", 1, 0.5, List.of(new Link("home", "/")), ordered("multi", "line1\nline2"));
        assertThat(serializer.write(site, DataFormat.YAML)).isEqualTo("""
                title: Title
                count: 1
                ratio: 0.5
                links:
                - name: home
                  url: /
                labels:
                  multi: |-
                    line1
                    line2
                """);
    }

    @Test
    void jsonPreservesOrderAndLayout() {
        String original = """
                {
                    "links": [
                        {
                            "url": "/",
                            "name": "home"
                        }
                    ],
                    "extra": "keep me",
                    "title": "My Site",
                    "count": 3,
                    "ratio": 1.50
                }
                """;
        Site site = new Site("New", 3, 1.5, List.of(new Link("home", "/"), new Link("docs", "/docs")),
                ordered("a", "A"));
        assertThat(serializer.write(site, original, DataFormat.JSON)).isEqualTo("""
                {
                    "links": [
                        {
                            "url": "/",
                            "name": "home"
                        },
                        {
                            "name": "docs",
                            "url": "/docs"
                        }
                    ],
                    "labels": {
                        "a": "A"
                    },
                    "extra": "keep me",
                    "title": "New",
                    "count": 3,
                    "ratio": 1.50
                }
                """);
    }

    @Test
    void jsonUnchangedIsIdentical() {
        String original = "{\n  \"name\": \"Alice\",\n  \"age\": 30\n}";
        assertThat(serializer.write(new Person("Alice", 30), original, DataFormat.JSON)).isEqualTo(original);
        String compact = "{\"name\":\"Alice\",\"age\":30}\n";
        assertThat(serializer.write(new Person("Bob", 30), compact, DataFormat.JSON))
                .isEqualTo("{\"name\":\"Bob\",\"age\":30}\n");
    }

    @Test
    void namingStrategy() {
        DataSerializer snake = new DataSerializer(PropertyNamingStrategies.SNAKE_CASE);
        record Item(String fullName) {
        }
        assertThat(snake.write(new Item("Alice"), "# comment\nfull_name: Bob\n", DataFormat.YAML))
                .isEqualTo("# comment\nfull_name: Alice\n");
    }

    @Test
    void directoryMappingIsNotSupported() {
        assertThatThrownBy(() -> serializer.write(new Dir(List.of()), DataFormat.YAML))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static Map<String, String> ordered(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        List<String> list = new ArrayList<>(List.of(keyValues));
        for (int i = 0; i < list.size(); i += 2) {
            map.put(list.get(i), list.get(i + 1));
        }
        return map;
    }
}
