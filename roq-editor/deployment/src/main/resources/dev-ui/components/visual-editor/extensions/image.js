import { Image, mergeAttributes } from '../../../bundle.js'
import { html } from 'lit';

function isTemplatePlaceholder(src) {
    if (typeof src !== 'string') {
        return false;
    }
    // Treat values that look like template placeholders (e.g. Qute) as non-resolvable:
    // - must contain both '{' and '}'
    // - must not look like a full URL with a scheme (contains "://")
    if (src.includes('://')) {
        return false;
    }
    return src.includes('{') && src.includes('}');
}

function resolveSrc (options, src) {
    if (isTemplatePlaceholder(src)) {
        return  'assets/placeholder-image.svg';
    } else {
        const prefix =
            typeof options.urlPrefix === 'function'
                ? options.urlPrefix()
                : options.urlPrefix;
        return prefix + src;
    }
}


export const RoqImage = Image.extend({
    name: 'image', // keep same name to override built‑in behavior

    addOptions() {
        return {
            ...this.parent?.(),
            HTMLAttributes: { class: 'tiptap-image-img', onerror: `this.onerror=null;this.src='assets/placeholder-image.svg';` },
            urlPrefix: undefined, // string or () => string
        }
    },

    renderHTML({ HTMLAttributes }) {
        const attrs = mergeAttributes(this.options.HTMLAttributes, HTMLAttributes);
        attrs.src = resolveSrc(this.options, attrs.src);
        return ['img', attrs];
    },
    addNodeView() {

        if (!this.options.resize || !this.options.resize.enabled || typeof document === 'undefined') {
            return null
        }

        const { directions, minWidth, minHeight, alwaysPreserveAspectRatio } = this.options.resize


        return ({ node, getPos, HTMLAttributes, editor }) => {
            const el = document.createElement('img')

            Object.entries(HTMLAttributes).forEach(([key, value]) => {
                if (value != null) {
                    switch (key) {
                        case 'width':
                        case 'height':
                            break
                        default:
                            el.setAttribute(key, value)
                            break
                    }
                }
            })


            el.src = resolveSrc(this.options, HTMLAttributes.src);

            const nodeView = new ResizableNodeView({
                element: el,
                editor,
                node,
                getPos,
                onResize: (width, height) => {
                    el.style.width = `${width}px`
                    el.style.height = `${height}px`
                },
                onCommit: (width, height) => {
                    const pos = getPos()
                    if (pos === undefined) {
                        return
                    }

                    this.editor
                        .chain()
                        .setNodeSelection(pos)
                        .updateAttributes(this.name, {
                            width,
                            height,
                        })
                        .run()
                },
                onUpdate: (updatedNode, _decorations, _innerDecorations) => {
                    return updatedNode.type === node.type;


                },
                options: {
                    directions,
                    min: {
                        width: minWidth,
                        height: minHeight,
                    },
                    preserveAspectRatio: alwaysPreserveAspectRatio === true,
                },
            })

            const dom = nodeView.dom

            // when image is loaded, show the node view to get the correct dimensions
            dom.style.visibility = 'hidden'
            dom.style.pointerEvents = 'none'
            el.onload = () => {
                dom.style.visibility = ''
                dom.style.pointerEvents = ''
            }

            return nodeView
        }
    },

})

export function renderImageForm(ctx) {
    return html`
          <vaadin-text-field
            label="Source"
            autofocus
            id="prompt-src"
            .value=${ctx.values.src ?? ''}
            @value-changed=${(e) => ctx.update('src', e.detail.value)}
          >
          </vaadin-text-field>
          <vaadin-text-field
            label="Alternate text"
            id="prompt-alt"
            .value=${ctx.values.alt ?? ''}
            @value-changed=${(e) => ctx.update('alt', e.detail.value)}
          >
          </vaadin-text-field>
          <vaadin-text-field
            label="Title"
            id="prompt-title"
            .value=${ctx.values.title ?? ''}
            @value-changed=${(e) => ctx.update('title', e.detail.value)}
          >
          </vaadin-text-field>
        `;
}