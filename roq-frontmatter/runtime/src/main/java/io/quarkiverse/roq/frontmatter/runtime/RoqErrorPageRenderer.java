package io.quarkiverse.roq.frontmatter.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;

final class RoqErrorPageRenderer {

    private static final String ROQ_PACKAGE_PREFIX = "io.quarkiverse.roq.";

    private RoqErrorPageRenderer() {
    }

    static Throwable roqCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getClass().getName().startsWith(ROQ_PACKAGE_PREFIX)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    static String render(String requestPath, Page page, Throwable throwable) {
        String errorTitle = throwable.getClass().getSimpleName();
        String errorMessage = throwable.getMessage();
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));

        StringBuilder html = new StringBuilder(8_192);
        html.append("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Roq error</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f3f6fb;
                      --panel: rgba(255, 255, 255, 0.96);
                      --text: #172033;
                      --muted: #5d6b82;
                      --border: rgba(23, 32, 51, 0.12);
                      --accent: #0f766e;
                      --accent-soft: rgba(15, 118, 110, 0.12);
                      --danger: #b42318;
                      --shadow: 0 24px 80px rgba(17, 24, 39, 0.14);
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      min-height: 100vh;
                      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                      color: var(--text);
                      background:
                        radial-gradient(circle at top, rgba(15, 118, 110, 0.14), transparent 42%),
                        linear-gradient(180deg, #edf4ff 0%, var(--bg) 100%);
                    }
                    main {
                      min-height: 100vh;
                      display: grid;
                      place-items: center;
                      padding: clamp(1rem, 4vw, 3rem);
                    }
                    .panel {
                      width: min(920px, 100%);
                      background: var(--panel);
                      border: 1px solid var(--border);
                      border-radius: 28px;
                      box-shadow: var(--shadow);
                      padding: clamp(1.5rem, 4vw, 3rem);
                      backdrop-filter: blur(12px);
                    }
                    .badge {
                      display: inline-flex;
                      align-items: center;
                      gap: 0.5rem;
                      padding: 0.45rem 0.8rem;
                      border-radius: 999px;
                      background: var(--accent-soft);
                      color: var(--accent);
                      font-size: 0.85rem;
                      font-weight: 700;
                      letter-spacing: 0.04em;
                      text-transform: uppercase;
                    }
                    h1 {
                      margin: 1rem 0 0.5rem;
                      font-size: clamp(2rem, 4vw, 3.5rem);
                      line-height: 1.04;
                      letter-spacing: -0.04em;
                    }
                    .lead {
                      margin: 0;
                      max-width: 68ch;
                      font-size: 1.06rem;
                      line-height: 1.7;
                      color: var(--muted);
                    }
                    .grid {
                      margin-top: 1.75rem;
                      display: grid;
                      gap: 1rem;
                    }
                    .card {
                      border: 1px solid var(--border);
                      border-radius: 20px;
                      background: rgba(255, 255, 255, 0.72);
                      padding: 1rem 1.1rem;
                    }
                    .card h2 {
                      margin: 0 0 0.6rem;
                      font-size: 0.94rem;
                      color: var(--muted);
                      text-transform: uppercase;
                      letter-spacing: 0.08em;
                    }
                    .meta {
                      display: grid;
                      gap: 0.6rem;
                    }
                    .meta-row {
                      display: flex;
                      flex-wrap: wrap;
                      gap: 0.45rem 0.75rem;
                      align-items: baseline;
                    }
                    .label {
                      min-width: 7rem;
                      font-size: 0.88rem;
                      font-weight: 700;
                      color: var(--muted);
                    }
                    code {
                      padding: 0.15rem 0.45rem;
                      border-radius: 8px;
                      background: rgba(15, 23, 42, 0.06);
                      font-family: ui-monospace, SFMono-Regular, SF Mono, Consolas, Liberation Mono, monospace;
                      font-size: 0.94em;
                      word-break: break-word;
                    }
                    .message {
                      margin: 0;
                      color: var(--danger);
                      font-weight: 700;
                    }
                    details {
                      margin-top: 1.25rem;
                      border: 1px solid var(--border);
                      border-radius: 18px;
                      background: rgba(15, 23, 42, 0.03);
                      overflow: hidden;
                    }
                    summary {
                      cursor: pointer;
                      list-style: none;
                      padding: 1rem 1.1rem;
                      font-weight: 700;
                    }
                    summary::-webkit-details-marker { display: none; }
                    summary::after {
                      content: "+";
                      float: right;
                      color: var(--muted);
                    }
                    details[open] summary::after {
                      content: "-";
                    }
                    .details-body {
                      border-top: 1px solid var(--border);
                      padding: 1rem 1.1rem 1.1rem;
                    }
                    pre {
                      margin: 0;
                      overflow: auto;
                      padding: 1rem;
                      border-radius: 14px;
                      background: #0b1020;
                      color: #e5eefc;
                      font-size: 0.9rem;
                      line-height: 1.6;
                      white-space: pre-wrap;
                      word-break: break-word;
                    }
                    @media (max-width: 640px) {
                      .panel { border-radius: 22px; }
                      .label { min-width: 5.5rem; }
                    }
                  </style>
                </head>
                <body>
                  <main>
                    <section class="panel" aria-labelledby="roq-error-title">
                      <div class="badge">Roq exception</div>
                      <h1 id="roq-error-title">We couldn't render this page</h1>
                      <p class="lead">
                        Roq ran into a problem while rendering
                """);
        html.append("<code>").append(escapeHtml(requestPath)).append("</code>");
        if (page != null) {
            html.append(" from <code>").append(escapeHtml(page.id())).append("</code>");
        }
        html.append("""
                        .
                      </p>
                      <div class="grid">
                        <div class="card">
                          <h2>What happened</h2>
                          <div class="meta">
                """);
        html.append(metaRow("Exception", errorTitle));
        if (errorMessage != null && !errorMessage.isBlank()) {
            html.append(metaRow("Message", errorMessage));
        }
        if (page != null) {
            html.append(metaRow("Template", page.id()));
        }
        html.append("""
                          </div>
                        </div>
                        <div class="card">
                          <h2>Next step</h2>
                          <p class="lead" style="margin: 0;">
                            Fix the content, layout, or asset path that triggered this exception, then reload the page.
                          </p>
                        </div>
                      </div>
                      <details>
                        <summary>Show technical details</summary>
                        <div class="details-body">
                          <pre>
                """);
        html.append(escapeHtml(stackTrace.toString()));
        html.append("""
                          </pre>
                        </div>
                      </details>
                    </section>
                  </main>
                </body>
                </html>
                """);
        return html.toString();
    }

    private static String metaRow(String label, String value) {
        return "<div class=\"meta-row\">\n"
                + "  <div class=\"label\">" + escapeHtml(label) + "</div>\n"
                + "  <div><code>" + escapeHtml(value) + "</code></div>\n"
                + "</div>\n";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

}
