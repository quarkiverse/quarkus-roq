import { Node, mergeAttributes } from '../bundle.js';
import { html, render } from 'lit';

export const QuteBlock = Node.create({
  name: 'quteBlock',
  group: 'block',
  atom: true,
  selectable: false,

  addAttributes() {
    return {
      content: {
        default: '',
        parseHTML: element => element.innerHTML?.trim() || '',
        renderHTML: attributes => {
          return {}
        },
      },
    }
  },

  parseHTML() {
    return [
      { tag: 'div[data-qute]' },
    ]
  },

  renderHTML({ HTMLAttributes, node }) {
    return [
      'div',
      mergeAttributes({ 'data-qute': '' }, this.options.HTMLAttributes, HTMLAttributes),
      node.attrs.content || ''
    ]
  },

  addNodeView() {
    return ({ node, getPos, editor }) => {
      const dom = document.createElement('div')
      dom.setAttribute('data-qute', '')
      dom.classList.add('qute-block')

      const stopPropagation = (e) => e.stopPropagation()

      const handleInput = (e) => {
        const pos = getPos()
        if (typeof pos === 'number') {
          editor.commands.command(({ tr }) => {
            tr.setNodeMarkup(pos, undefined, {
              ...node.attrs,
              content: e.target.textContent || ''
            })
            return true
          })
        }
      }

      const handleClick = (e) => {
        e.stopPropagation()
        e.target.focus()
      }

      const template = html`
        <div class="qute-template-label" contenteditable="false">Qute Template</div>
        <pre 
          class="qute-template-content" 
          contenteditable="true"
          @keydown=${stopPropagation}
          @keyup=${stopPropagation}
          @keypress=${stopPropagation}
          @mousedown=${stopPropagation}
          @click=${handleClick}
          @input=${handleInput}
        >${node.attrs.content || ''}</pre>
      `

      render(template, dom)

      const contentElement = dom.querySelector('.qute-template-content')

      return {
        dom,
        update: (updatedNode) => {
          if (updatedNode.type.name !== 'quteBlock') return false
          if (contentElement.textContent !== updatedNode.attrs.content) {
            contentElement.textContent = updatedNode.attrs.content || ''
          }
          return true
        },
      }
    }
  },

  renderMarkdown: (node) => {
    const content = node.attrs?.content || ''
    return `<div data-qute>\n${content}\n</div>\n`
  },
})
