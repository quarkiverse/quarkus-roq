/**
 * Slash command extension for TipTap editor
 * Shows a dropdown menu when typing "/" at the start of a line
 */

import { html, render } from 'lit';
import { Extension, Suggestion } from '../../../bundle.js';
import './slash-menu.js';
import {showImageDialog} from "../../image-dialog.js";

// Define all available block types with their commands
// Image command needs special handling as it requires context from options
const getBlockTypes = (options) => [
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
        icon: '🖼️',
        command: ({ editor, range }) => {
            const pagePath = options.getPagePath ? options.getPagePath() : '';

            showImageDialog({ title: '', src: '', alt: '' }, pagePath).then(({ src, title, alt }) => {
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

const AI_COMMAND = {
    label: 'AI',
    icon: '✨',
    description: 'Generate content with AI',
    isAiCommand: true,
    command: ({ editor, range }) => {
        editor.chain().focus().deleteRange(range).run();
        editor.view.dom.dispatchEvent(new CustomEvent('generate-ai-content', {
            bubbles: true, composed: true,
            detail: { editor }
        }));
    }
};

/**
 * Create the SlashCommand extension
 */
export const SlashCommand = Extension.create({
  name: 'slashCommand',

    addOptions() {
        return {
            assistantIsAvailable: false,
            suggestion: {
                char: '/',
                startOfLine: true,
                command: ({ editor, range, props }) => {
                    props.command({ editor, range });
                },
                decorationContent: "Filter blocks..."
            },
            getPagePath: () => '',
        };
    },


  addCommands() {
    return {
      openSlashMenu:
        (pos) =>
          ({ editor, chain }) => {
            if (pos == null) {
              console.log('openSlashMenu: pos is null');
              return false;
            }
            // Focus at the position, then insert / to trigger the slash command
            chain().focus(pos).run();
            const { $from } = editor.state.selection;
            if ($from.parent.textContent.length > 0) {
              return chain().splitBlock().insertContent('/').run();
            }
            return chain().insertContent('/').run();
          },

    }
  },


    addProseMirrorPlugins() {
        const blockTypes = getBlockTypes(this.options);
        const { assistantIsAvailable } = this.options;

        return [
            Suggestion({
                editor: this.editor,
                ...this.options.suggestion,
                items: ({ query }) => {
                    const lowerQuery = query.toLowerCase();
                    const items = blockTypes.filter(item =>
                        item.label.toLowerCase().includes(lowerQuery)
                    );
                    if (assistantIsAvailable && 'ai'.includes(lowerQuery)) {
                        items.push(AI_COMMAND);
                    }
                    return items;
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
