/**
 * Editor context for sharing editor state across components
 */

import { createContext } from '../../bundle.js';

export const editorContext = createContext(Symbol('editor'));

