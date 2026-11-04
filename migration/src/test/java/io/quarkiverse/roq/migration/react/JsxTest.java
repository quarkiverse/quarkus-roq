package io.quarkiverse.roq.migration.react;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class JsxTest {

    @Test
    void parsesElementsAttributesAndExpressions() {
        Jsx.Parsed parsed = Jsx.parse("<a href=\"/\" className={x} {...props} hidden>Hi {name}<br/></a> rest", 0);

        Jsx.Element a = parsed.element();
        assertThat(a.name()).isEqualTo("a");
        assertThat(a.attributes()).containsExactly(new Jsx.Attribute("href", "/", null),
                new Jsx.Attribute("className", null, "x"), new Jsx.Attribute(null, null, "props"),
                new Jsx.Attribute("hidden", null, null));
        assertThat(a.children()).containsExactly(new Jsx.Text("Hi "), new Jsx.Expression("name"),
                new Jsx.Element("br", List.of(), List.of()));
        assertThat(parsed.end()).isEqualTo("<a href=\"/\" className={x} {...props} hidden>Hi {name}<br/></a>".length());
    }

    @Test
    void dropsWhitespaceWithLineBreaksAsJsxDoes() {
        Jsx.Element p = Jsx.parse("<p>\n    Sponsored by\n    <b>us</b>\n  and   others\n</p>", 0).element();

        assertThat(p.children()).containsExactly(new Jsx.Text("Sponsored by"),
                new Jsx.Element("b", List.of(), List.of(new Jsx.Text("us"))), new Jsx.Text("and   others"));
    }

    @Test
    void parsesFragmentsAndNestedJsxInExpressions() {
        Jsx.Element fragment = Jsx.parse("<><Header/>{ok && <p a={`x${y}`}>{z}</p>}</>", 0).element();

        assertThat(fragment.isFragment()).isTrue();
        assertThat(fragment.children()).hasSize(2);
        assertThat(fragment.children().get(1)).isEqualTo(new Jsx.Expression("ok && <p a={`x${y}`}>{z}</p>"));
    }

    @Test
    void reportsBadJsx() {
        assertThatThrownBy(() -> Jsx.parse("<a><b></a>", 0)).isInstanceOf(Jsx.JsxException.class)
                .hasMessageContaining("</a> closes <b>");
    }

    @Test
    void stripsCommentsButNotUrls() {
        assertThat(JsSource.stripComments("a // gone\nb /* gone */ c {/* gone */} 'x//y' https://example.org"))
                .isEqualTo("a \nb  c  'x//y' https://example.org");
    }
}
