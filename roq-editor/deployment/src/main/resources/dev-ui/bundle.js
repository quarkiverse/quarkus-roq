import { Editor, Extension, Node, mergeAttributes } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import { Markdown } from '@tiptap/markdown'
import { Image } from '@tiptap/extension-image'
import { Link } from '@tiptap/extension-link'
import { BubbleMenu } from '@tiptap/extension-bubble-menu'
import { DragHandle } from '@tiptap/extension-drag-handle'
import { Placeholder } from '@tiptap/extension-placeholder'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import { CodeBlockLowlight } from '@tiptap/extension-code-block-lowlight'
import Suggestion from '@tiptap/suggestion'
import { common, createLowlight } from 'lowlight'
import { createContext, ContextProvider, ContextConsumer } from '@lit/context'
import { parse as yamlParse, stringify as yamlStringify } from 'yaml'
import { TextSelection } from 'prosemirror-state'

const ConfCodeBlockLowlight = CodeBlockLowlight.configure({
    lowlight: createLowlight(common)
})

export { Editor, Extension, Node, mergeAttributes, StarterKit, Markdown, Image, Link, BubbleMenu, DragHandle, Table, TableRow, TableCell, TableHeader, ConfCodeBlockLowlight, Suggestion, createContext, ContextProvider, ContextConsumer, TextSelection, Placeholder, yamlParse, yamlStringify };