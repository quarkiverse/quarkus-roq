/**
 * Slash command extension for TipTap editor
 * Shows a dropdown menu when typing "/" at the start of a line
 */

import { html, render } from 'lit';
import { Extension, Suggestion } from '../../../bundle.js';
import './slash-menu.js';
import {showPrompt} from "../../prompt-dialog.js";
import {renderImageForm} from "./image.js";

// Define all available block types with their commands
const BLOCK_TYPES = [
    {
        label: 'Text',
        icon: 'T',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).setParagraph().run();
        }
    },
    {
        label: 'Heading 1',
        icon: 'H₁',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).setHeading({ level: 1 }).run();
        }
    },
    {
        label: 'Heading 2',
        icon: 'H₂',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).setHeading({ level: 2 }).run();
        }
    },
    {
        label: 'Heading 3',
        icon: 'H₃',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).setHeading({ level: 3 }).run();
        }
    },
    {
        label: 'Heading 4',
        icon: 'H₄',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).setHeading({ level: 4 }).run();
        }
    },
    {
        label: 'Heading 5',
        icon: 'H₅',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).setHeading({ level: 5 }).run();
        }
    },
    {
        label: 'Heading 6',
        icon: 'H₆',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).setHeading({ level: 6 }).run();
        }
    },
    {
        label: 'Bullet List',
        icon: '≡',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).toggleBulletList().run();
        }
    },
    {
        label: 'Numbered List',
        icon: '1≡',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).toggleOrderedList().run();
        }
    },
    {
        label: 'Image',
        icon: '🏞️',
        command: ({ editor, range }) => {
            showPrompt('Add an image:', { title: '', src: '', alt: ''}, renderImageForm).then(({ src, title, alt}) => {
                if (src) {
                    editor.chain().focus().deleteRange(range).setParagraph().setImage({ src, title, alt }).run();
                }
            });
        }
    },
    {
        label: 'Code Block',
        icon: '{}',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).toggleCodeBlock().run();
        }
    },
    {
        label: 'Raw Block',
        icon: '</>',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).insertContent({ type: 'rawBlock' }).run();
        }
    },
    {
        label: 'Table',
        icon: '▦',
        command: ({ editor, range }) => {
            editor.chain().focus().deleteRange(range).insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
        }
    }
];

/**
 * Create the SlashCommand extension
 */
export const SlashCommand = Extension.create({
    name: 'slashCommand',

    addOptions() {
        return {
            suggestion: {
                char: '/',
                startOfLine: true,
                command: ({ editor, range, props }) => {
                    props.command({ editor, range });
                },
                decorationContent: "Filter blocks..."
            },
        };
    },


    addCommands() {
        return {
            openSlashMenu:
                (pos) =>
                    ({editor, chain}) => {
                        if (pos == null) {
                            return false;
                        }
                        const $root = editor.$pos(pos);

                        if ($root.node.type.name !== 'doc') return false

                        const $from = $root.firstChild;
                        if(!$from.node.isTextblock)  return false;
                        console.log($from.textContent);
                        const hasContent = $from.textContent?.length > 0;
                        if(hasContent){
                            return chain()
                                .focus()
                                .insertContentAt($from.to, [
                                {
                                    type: 'paragraph',
                                    content: [
                                        {
                                            type: 'text',
                                            text: '/',
                                        },
                                    ],
                                }], { updateSelection:true })
                                .run();
                        }

                        return chain()
                            .focus()
                            .insertContentAt($from.pos, '/', { updateSelection:true })
                            .run()
                    },

        }
    },


    addProseMirrorPlugins() {
        return [
            Suggestion({
                editor: this.editor,
                ...this.options.suggestion,
                items: ({query}) => {
                    return BLOCK_TYPES.filter(item =>
                        item.label.toLowerCase().includes(query.toLowerCase())
                    );
                },
                render: () => {
                    let component;
                    let popup;

                    const updatePosition = (clientRect) => {
                        if (popup && clientRect) {
                            const rect = clientRect();
                            if (rect) {
                                popup.style.left = `${rect.left}px`;
                                popup.style.top = `${rect.bottom + 4}px`;
                            }
                        }
                    };

                    const renderPopup = (props) => {
                        const template = html`
                          <qwc-slash-menu
                            .items="${props.items}"
                            .query="${props.query}"
                            @item-selected="${(e) => {
                              const item = e.detail.item;
                              if (item && item.command) {
                                props.command(item);
                              }
                            }}"
                          ></qwc-slash-menu>
                        `;
                        render(template, popup);
                        component = popup.querySelector('qwc-slash-menu');
                    };

                    return {
                        onStart: (props) => {
                            // Create popup container
                            popup = document.createElement('div');
                            popup.style.position = 'absolute';
                            popup.style.zIndex = '1000';
                            document.body.appendChild(popup);

                            // Render the slash menu using Lit template
                            renderPopup(props);
                            updatePosition(props.clientRect);
                        },

                        onUpdate: (props) => {
                            renderPopup(props);
                            updatePosition(props.clientRect);
                        },

                        onKeyDown: (props) => {
                            if (props.event.key === 'Escape') {
                                if (popup) {
                                    popup.remove();
                                    popup = null;
                                }
                                return true;
                            }

                            if (component) {
                                return component.onKeyDown(props.event);
                            }

                            return false;
                        },

                        onExit: () => {
                            if (popup) {
                                popup.remove();
                                popup = null;
                            }
                            component = null;
                        },
                    };
                },
            }),
        ];
    },
});
