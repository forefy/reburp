package com.reburp

/**
 * OpenAPI fragments for `/api/ui`, which drives Burp's own Swing window from inside the JVM.
 * Only `suiteFrame()` is an official Montoya handle; the rest walks Burp's component tree, so
 * the descriptions say "best-effort" where the target depends on Burp's internal layout.
 */

internal fun uiPaths(): String = """
    "/api/ui/screenshot": {
      "get": {
        "tags": ["UI"],
        "summary": "Screenshot a Burp window, or one element in it, as a PNG",
        "description": "**[Montoya API]** Renders the suite frame returned by `swingUtils().suiteFrame()` off-screen with `printAll` and returns it as a PNG. Because it paints the live component tree rather than grabbing the screen, it works while the window is minimized, occluded, or on another desktop. Pass `window` to render a dialog or popup window instead (ids from `GET /api/ui/windows`), or `id` to crop to one element (ids from `GET /api/ui/elements`). The call is not written to the activity log, so the raw image bytes never land there.",
        "operationId": "uiScreenshot",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "window", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Window id such as `w1`. Defaults to `w0`, the suite frame." },
          { "name": "id", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Element id to crop to, such as `w0:0/1/3`. Overrides `window`." }
        ],
        "responses": {
          "200": {
            "description": "PNG image",
            "content": { "image/png": { "schema": { "type": "string", "format": "binary" } } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/tabs": {
      "get": {
        "tags": ["UI"],
        "summary": "List Burp's top-level tabs, the nested tab bars showing under them, and what is selected",
        "description": "**[Montoya API]** Walks the suite frame for its main tab bar (the tabbed pane carrying a *Dashboard* tab, else the one with the most tabs) and returns the tab titles in order with the selected index. `nested` lists every other tab bar currently showing, such as an extension's own sub-tabs or Repeater's request tabs, so their titles can be passed to `POST /api/ui/select-tab`.",
        "operationId": "uiListTabs",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiTabsResponse" },
              "example": { "tabs": ["Dashboard", "Target", "Proxy", "Intruder", "Repeater", "Collaborator", "reburp", "Autorize"], "selected": 7, "selected_title": "Autorize", "nested": [ { "tabs": ["Request/Response Viewers", "Configuration", "Users"], "selected": 1, "selected_title": "Configuration" } ] }
            } }
          },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/select-tab": {
      "post": {
        "tags": ["UI"],
        "summary": "Select a tab by name or index, in Burp's main bar or any nested tab bar",
        "description": "**[Montoya API]** Switches to the tab whose title contains `name` (case-insensitive substring) or sits at `index`. The main tab bar is tried first; a name that matches nothing there falls back to the tab bars currently showing inside the open tab, such as Autorize's *Configuration* or *Interception Filters*, so Burp's own tab names keep winning. `index` always refers to the main bar and takes precedence over `name`. A follow-up `GET /api/ui/screenshot` then shows that tab's content.",
        "operationId": "uiSelectTab",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiSelectTabRequest" },
            "example": { "name": "interception filters" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Selected nested tab", "matched": "Interception Filters" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/click-button": {
      "post": {
        "tags": ["UI"],
        "summary": "Click a visible button by its label",
        "description": "**[Montoya API]** Finds the first showing, enabled button anywhere in the Burp window whose text contains `text` (case-insensitive substring) and invokes its click action on the Swing event thread. Checkboxes, radio buttons, toggles and menu items count as buttons, so this also toggles an option or picks an entry from an open context menu. Best-effort: it matches on visible text only, so prefer labels that are unique on the current tab. For anything without a label, use `GET /api/ui/elements` and `POST /api/ui/click`.",
        "operationId": "uiClickButton",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiClickButtonRequest" },
            "example": { "text": "Poll now" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Clicked button", "matched": "Poll now" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/select-row": {
      "post": {
        "tags": ["UI"],
        "summary": "Select a row in the current tab's main table",
        "description": "**[Montoya API]** Targets the largest visible table with rows (the content table of the open tab, such as Proxy HTTP history or Collaborator interactions) and selects the row at `index`, or the newest row when `last` is true. A real mouse click is dispatched on the cell so Burp's detail panes update, which a plain selection change would not do. Follow with `GET /api/ui/screenshot` to see that row's request and response. To pick a row in a specific table, use `POST /api/ui/select` with the table's element id.",
        "operationId": "uiSelectRow",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiSelectRowRequest" },
            "example": { "last": true }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Selected row", "matched": "row 41 of 42" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/windows": {
      "get": {
        "tags": ["UI"],
        "summary": "List the showing windows: the suite frame, dialogs and popups",
        "description": "**[Montoya API]** Returns every AWT window that is currently showing, with the suite frame first as `w0`. Dialogs Burp opens (confirmations, option editors) and heavyweight popup menus appear here while they are open, and their ids feed `GET /api/ui/elements`, `GET /api/ui/screenshot` and `POST /api/ui/click`.",
        "operationId": "uiListWindows",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiWindowsResponse" },
              "example": { "windows": [ { "id": "w0", "type": "JFrame", "title": "Burp Suite Professional", "bounds": { "x": 0, "y": 25, "width": 1440, "height": 875 }, "focused": true } ] }
            } }
          },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/elements": {
      "get": {
        "tags": ["UI"],
        "summary": "Inventory the visible elements of a window with stable ids",
        "description": "**[Montoya API]** Walks one window's Swing tree and lists the elements an agent would act on: buttons, checkboxes, text fields and areas, combo boxes, lists, trees, tables, tab bars, spinners, sliders, labels with text, and open popup menus. Each carries an `id` of the form `w<window>:<child>/<child>/...`, its class name, visible text, bounds in window coordinates, and for choosers their items and selection. Ids stay valid while the layout is unchanged, which is the span between this call and the action that uses it; after a tab switch or a dialog opening, list again. Filter with `text` (matched against text, name, tooltip and items) and `type` (matched against the class name); pass `all=true` to include plain containers and `hidden=true` to include elements that are not currently showing.",
        "operationId": "uiListElements",
        "x-api-source": "montoya",
        "parameters": [
          { "name": "window", "in": "query", "required": false, "schema": { "type": "string", "default": "w0" }, "description": "Window id from GET /api/ui/windows." },
          { "name": "text", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Case-insensitive substring to match against each element's text, name, tooltip and items." },
          { "name": "type", "in": "query", "required": false, "schema": { "type": "string" }, "description": "Case-insensitive substring of the Swing class name, such as `button`, `text`, `combo`, `table`." },
          { "name": "all", "in": "query", "required": false, "schema": { "type": "boolean", "default": false }, "description": "Include plain containers (panels, scroll panes) as well." },
          { "name": "hidden", "in": "query", "required": false, "schema": { "type": "boolean", "default": false }, "description": "Include elements that exist but are not showing, such as the content of unselected tabs." },
          { "name": "limit", "in": "query", "required": false, "schema": { "type": "integer", "default": 500, "minimum": 1, "maximum": 5000 }, "description": "Maximum number of elements to return; `truncated` says whether the cap was hit." }
        ],
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiElementsResponse" },
              "example": { "window": "w0", "count": 2, "truncated": false, "elements": [
                { "id": "w0:0/1/0/2/4", "type": "JButton", "text": "Clear table", "bounds": { "x": 167, "y": 263, "width": 120, "height": 24 }, "enabled": true, "showing": true, "focused": false, "children": 0 },
                { "id": "w0:0/1/0/2/9", "type": "JComboBox", "text": "Scope items only", "bounds": { "x": 239, "y": 470, "width": 383, "height": 24 }, "enabled": true, "showing": true, "focused": false, "items": ["Scope items only", "URL Contains (simple string)"], "selected": 0, "children": 2 }
              ] }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/click": {
      "post": {
        "tags": ["UI"],
        "summary": "Click any element by id, or a point in a window",
        "description": "**[Montoya API]** Dispatches real mouse press, release and click events at the centre of the element `id`, or at (`x`, `y`) in window coordinates of `window`, to whichever component is deepest at that point. `button` may be left, middle or right; a right click raises the platform's popup trigger, so Burp's context menus (Send to Repeater, Add to scope, ...) open and their items can then be picked with `POST /api/ui/click-button` by label or listed with `GET /api/ui/elements`. `count` of 2 double-clicks. Because the events go through the component's own listeners, this reaches anything the user could click, including extension panels and dialogs.",
        "operationId": "uiClick",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiClickRequest" },
            "example": { "id": "w0:0/1/0/2/4" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Clicked", "matched": "JButton 'Clear table'" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/type": {
      "post": {
        "tags": ["UI"],
        "summary": "Type into a text field, text area or editable combo box",
        "description": "**[Montoya API]** Focuses the text component `id` and either replaces its content with `text` (the default) or inserts `text` at the caret. With `enter` true an Enter key press follows, which submits single-line fields. Returns 400 when the element is not an editable text component.",
        "operationId": "uiType",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiTypeRequest" },
            "example": { "id": "w0:0/1/0/2/7", "text": "example.com", "enter": true }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Replaced text", "matched": "11 chars" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/select": {
      "post": {
        "tags": ["UI"],
        "summary": "Select an entry in a combo box, list, tab bar, table or tree",
        "description": "**[Montoya API]** Picks the entry at `index`, or the first whose label contains `text` (case-insensitive), inside the element `id`. Labels are the combo or list item strings, tab titles, a table row's cell values joined with ` | `, or a tree node's display text. Table rows get a real click so Burp's detail panes follow, as with `POST /api/ui/select-row`.",
        "operationId": "uiSelect",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiSelectRequest" },
            "example": { "id": "w0:0/1/0/2/9", "text": "URL Contains" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Selected item", "matched": "URL Contains (simple string)" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/key": {
      "post": {
        "tags": ["UI"],
        "summary": "Send a key press to an element, or to whatever has focus",
        "description": "**[Montoya API]** Dispatches key pressed, typed and released events for `key`, named as in `java.awt.event.KeyEvent` without the `VK_` prefix (ENTER, TAB, ESCAPE, DELETE, F5, a single letter or digit) with optional `modifiers` (shift, ctrl, alt, meta). Goes to the element `id` when given, otherwise to the current focus owner. Useful for confirming or dismissing dialogs and for shortcuts such as meta+R.",
        "operationId": "uiKey",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiKeyRequest" },
            "example": { "key": "ESCAPE" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Pressed key", "matched": "ESCAPE" }
            } }
          },
          "400": { "${'$'}ref": "#/components/responses/BadRequest" },
          "404": { "${'$'}ref": "#/components/responses/NotFound" },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    }
"""

internal fun uiSchemas(): String = """
      "UiTabGroup": {
        "type": "object",
        "description": "One tab bar: its titles and which one is selected.",
        "properties": {
          "tabs": { "type": "array", "items": { "type": "string" }, "description": "Tab titles in display order." },
          "selected": { "type": "integer", "description": "Index of the selected tab in `tabs`, or -1 when none is selected." },
          "selected_title": { "type": "string", "nullable": true, "description": "Title of the selected tab, or null when none is selected." }
        }
      },

      "UiTabsResponse": {
        "type": "object",
        "description": "The titles of Burp's top-level tabs, which one is selected, and the nested tab bars showing under it.",
        "properties": {
          "tabs": { "type": "array", "items": { "type": "string" }, "description": "Main tab titles in display order. Index positions match `selected` and the `index` field of select-tab." },
          "selected": { "type": "integer", "description": "Index of the selected main tab in `tabs`, or -1 when none is selected." },
          "selected_title": { "type": "string", "nullable": true, "description": "Title of the selected main tab, or null when none is selected." },
          "nested": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/UiTabGroup" }, "description": "Every other tab bar currently showing (an extension's sub-tabs, Repeater's request tabs), in tree order. Their titles work with select-tab's `name`." }
        }
      },

      "UiSelectTabRequest": {
        "type": "object",
        "description": "Which tab to select. Give one of `name` or `index`; `index` takes precedence when both are present and only addresses the main bar.",
        "properties": {
          "name": { "type": "string", "nullable": true, "description": "Case-insensitive substring of the tab title, for example `proxy`, `collab` or `interception filters`. Main tabs are matched before nested ones." },
          "index": { "type": "integer", "nullable": true, "description": "Zero-based position in the main tab list returned by GET /api/ui/tabs." }
        }
      },

      "UiClickButtonRequest": {
        "type": "object",
        "required": ["text"],
        "properties": {
          "text": { "type": "string", "description": "Case-insensitive substring of the button's visible label. The first showing, enabled match anywhere in the window is clicked." }
        }
      },

      "UiSelectRowRequest": {
        "type": "object",
        "description": "Which row to select in the current tab's main table. Give `index`, or set `last` to pick the newest row; `last` takes precedence.",
        "properties": {
          "index": { "type": "integer", "nullable": true, "description": "Zero-based row index in the table's current view order." },
          "last": { "type": "boolean", "default": false, "description": "Select the final row instead of one by index." }
        }
      },

      "UiActionResponse": {
        "type": "object",
        "description": "Outcome of a UI action. A 200 always carries `ok: true`; failures come back as 4xx/5xx with an ErrorResponse instead.",
        "properties": {
          "ok": { "type": "boolean" },
          "message": { "type": "string", "description": "What was done, for example `Selected tab`, `Clicked` or `Replaced text`." },
          "matched": { "type": "string", "nullable": true, "description": "The element the request resolved to: the tab title, the button's full label, `row N of M`, or the clicked component's type and text." }
        }
      },

      "UiBounds": {
        "type": "object",
        "description": "A rectangle in the coordinates of the window that contains the element (origin top-left).",
        "properties": {
          "x": { "type": "integer" },
          "y": { "type": "integer" },
          "width": { "type": "integer" },
          "height": { "type": "integer" }
        }
      },

      "UiWindow": {
        "type": "object",
        "properties": {
          "id": { "type": "string", "description": "Window id, `w0` for the suite frame; use it in the `window` fields and query parameters." },
          "type": { "type": "string", "description": "Swing class name, for example JFrame, JDialog or a popup's heavyweight window class." },
          "title": { "type": "string", "nullable": true, "description": "Window title where it has one." },
          "bounds": { "${'$'}ref": "#/components/schemas/UiBounds" },
          "focused": { "type": "boolean", "description": "Whether this window currently owns keyboard focus." }
        }
      },

      "UiWindowsResponse": {
        "type": "object",
        "properties": {
          "windows": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/UiWindow" }, "description": "Showing windows, suite frame first." }
        }
      },

      "UiElement": {
        "type": "object",
        "description": "One Swing component. Which optional fields are present depends on its kind.",
        "properties": {
          "id": { "type": "string", "description": "Stable path id, `w<window>:<child>/<child>/...`, for the click, type, select, key and screenshot calls." },
          "type": { "type": "string", "description": "Swing class name, for example JButton, JCheckBox, JTextField, JComboBox, JTable, JTabbedPane, JMenuItem." },
          "text": { "type": "string", "nullable": true, "description": "Visible text: a button or label caption, a text field's content (first 200 chars), the selected tab, item or value of a chooser." },
          "name": { "type": "string", "nullable": true, "description": "The component's programmatic name, when the author set one." },
          "tooltip": { "type": "string", "nullable": true },
          "bounds": { "${'$'}ref": "#/components/schemas/UiBounds" },
          "enabled": { "type": "boolean" },
          "showing": { "type": "boolean", "description": "Whether the element is currently painted; false only appears with `hidden=true`." },
          "focused": { "type": "boolean", "description": "Whether the element owns keyboard focus." },
          "items": { "type": "array", "nullable": true, "items": { "type": "string" }, "description": "For combo boxes and lists their entries (first 100), for tab bars the tab titles." },
          "selected": { "type": "integer", "nullable": true, "description": "Selected index for combo boxes, lists, tab bars, tables (row) and trees (row); -1 when nothing is selected." },
          "rows": { "type": "integer", "nullable": true, "description": "Row count for tables, lists and trees." },
          "children": { "type": "integer", "description": "Number of direct child components." }
        }
      },

      "UiElementsResponse": {
        "type": "object",
        "properties": {
          "window": { "type": "string", "description": "The window that was listed." },
          "count": { "type": "integer", "description": "Number of elements returned." },
          "truncated": { "type": "boolean", "description": "True when `limit` cut the listing short." },
          "elements": { "type": "array", "items": { "${'$'}ref": "#/components/schemas/UiElement" } }
        }
      },

      "UiClickRequest": {
        "type": "object",
        "description": "Where to click. Give an element `id`, or `x` and `y` in the coordinates of `window`.",
        "properties": {
          "id": { "type": "string", "nullable": true, "description": "Element id from GET /api/ui/elements; the click lands at its centre." },
          "x": { "type": "integer", "nullable": true, "description": "Window x coordinate, used with `y` when no `id` is given." },
          "y": { "type": "integer", "nullable": true, "description": "Window y coordinate." },
          "window": { "type": "string", "nullable": true, "description": "Window id for `x`/`y`; defaults to `w0`." },
          "button": { "type": "string", "default": "left", "enum": ["left", "middle", "right"], "description": "Right raises the popup trigger, which opens context menus." },
          "count": { "type": "integer", "default": 1, "minimum": 1, "maximum": 3, "description": "2 for a double-click." }
        }
      },

      "UiTypeRequest": {
        "type": "object",
        "required": ["id", "text"],
        "properties": {
          "id": { "type": "string", "description": "Element id of a text field, text area or editable combo box." },
          "text": { "type": "string" },
          "replace": { "type": "boolean", "default": true, "description": "Replace the existing content (true) or insert at the caret (false)." },
          "enter": { "type": "boolean", "default": false, "description": "Press Enter afterwards." }
        }
      },

      "UiSelectRequest": {
        "type": "object",
        "required": ["id"],
        "description": "Which entry to select inside a chooser element. Give `index` or `text`; `index` wins when both are present.",
        "properties": {
          "id": { "type": "string", "description": "Element id of a combo box, list, tab bar, table or tree." },
          "text": { "type": "string", "nullable": true, "description": "Case-insensitive substring of the entry's label." },
          "index": { "type": "integer", "nullable": true, "description": "Zero-based entry, tab, row or tree-row index." }
        }
      },

      "UiKeyRequest": {
        "type": "object",
        "required": ["key"],
        "properties": {
          "key": { "type": "string", "description": "KeyEvent name without `VK_` (ENTER, ESCAPE, TAB, DELETE, F5, UP, a letter or digit)." },
          "id": { "type": "string", "nullable": true, "description": "Element to send the key to; defaults to the focus owner." },
          "modifiers": { "type": "array", "items": { "type": "string", "enum": ["shift", "ctrl", "alt", "meta"] }, "default": [], "description": "Held modifier keys." }
        }
      }
"""
