---
title: Markdown Markup Test
description: All Markdown content types to verify theme styling
content-toc: true
---

# Heading 1
This is a paragraph under heading 1. It contains **strong text**, *emphasized text*, and `inline code`.

## Heading 2
This is a paragraph under heading 2 with a [link to example.com](https://example.com).

### Heading 3
This is a paragraph under heading 3.

#### Heading 4
This is a paragraph under heading 4.

##### Heading 5
This is a paragraph under heading 5.

###### Heading 6
This is a paragraph under heading 6.

## Blockquotes

> This is a blockquote.
> It can span multiple lines.
>
> It can even have multiple paragraphs.

## Strong and Emphasis

This paragraph contains **bold/strong text**, *italic/emphasized text*, and ***bold and italic*** text.

## Code

### Inline Code

This paragraph contains `inline code` within regular text.

### Code Blocks

```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

```javascript
function greet(name) {
    return `Hello, $\{name}!`;
}

console.log(greet("World"));
```

## Lists

### Unordered Lists

- First item
- Second item
- Third item
  - Nested item 1
  - Nested item 2
- Fourth item

### Ordered Lists

1. First step
2. Second step
3. Third step
   1. Nested step 1
   2. Nested step 2
4. Fourth step

## Tables

| Header 1 | Header 2 | Header 3 |
|----------|----------|----------|
| Cell 1   | Cell 2   | Cell 3   |
| Cell 4   | Cell 5   | Cell 6   |
| Cell 7   | Cell 8   | Cell 9   |
| Cell 10  | Cell 11  | Cell 12  |

### Table with Alignment

| Left Aligned | Center Aligned | Right Aligned |
|:-------------|:--------------:|--------------:|
| Left         | Center         | Right         |
| Left         | Center         | Right         |

## Horizontal Rule

---

## Paragraphs

This is a standard paragraph with regular text. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.

This is another paragraph. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.

## Links

Here is an [inline link](https://example.com) and here is a [link with title](https://example.com "Example Title").

## Mixed Content

Here's a paragraph with **strong text**, *emphasized text*, `inline code`, and a [link](https://example.com) all in one.

> And here's a blockquote that contains **strong text**, *emphasized text*, and `inline code` as well.

### Complex List

1. First item with **strong text**
2. Second item with `inline code`
3. Third item with a [link](https://example.com)
   - Nested unordered item
   - Another nested item with *emphasis*
4. Fourth item

## Code in Other Elements

### Code in Headings: `inline-code`

This heading has inline code: `variable = value`

### Code in Lists

- Item with `inline code`
- Item with code block:
  ```python
  def hello():
      print("Hello")
  ```

### Code in Blockquotes

> This blockquote contains `inline code` and shows how code is styled within quotes.