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
        "summary": "Screenshot the whole Burp window as a PNG",
        "description": "**[Montoya API]** Renders the suite frame returned by `swingUtils().suiteFrame()` off-screen with `printAll` and returns it as a PNG. Because it paints the live component tree rather than grabbing the screen, it works while the window is minimized, occluded, or on another desktop. The call is not written to the activity log, so the raw image bytes never land there.",
        "operationId": "uiScreenshot",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "PNG image of the Burp window",
            "content": { "image/png": { "schema": { "type": "string", "format": "binary" } } }
          },
          "500": { "${'$'}ref": "#/components/responses/InternalServerError" },
          "503": { "${'$'}ref": "#/components/responses/UiUnavailable" }
        }
      }
    },

    "/api/ui/tabs": {
      "get": {
        "tags": ["UI"],
        "summary": "List Burp's top-level tabs and which one is selected",
        "description": "**[Montoya API]** Walks the suite frame for its main tab bar (the tabbed pane carrying a *Dashboard* tab, else the one with the most tabs) and returns the tab titles in order with the selected index. Use the titles with `POST /api/ui/select-tab`.",
        "operationId": "uiListTabs",
        "x-api-source": "montoya",
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiTabsResponse" },
              "example": { "tabs": ["Dashboard", "Target", "Proxy", "Intruder", "Repeater", "Collaborator", "reburp"], "selected": 2, "selected_title": "Proxy" }
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
        "summary": "Select a top-level Burp tab by name or index",
        "description": "**[Montoya API]** Switches the main tab bar to the tab whose title contains `name` (case-insensitive substring) or sits at `index`. When both are given, `index` wins. A follow-up `GET /api/ui/screenshot` then shows that tab's content.",
        "operationId": "uiSelectTab",
        "x-api-source": "montoya",
        "requestBody": {
          "required": true,
          "content": { "application/json": {
            "schema": { "${'$'}ref": "#/components/schemas/UiSelectTabRequest" },
            "example": { "name": "proxy" }
          } }
        },
        "responses": {
          "200": {
            "description": "OK",
            "content": { "application/json": {
              "schema": { "${'$'}ref": "#/components/schemas/UiActionResponse" },
              "example": { "ok": true, "message": "Selected tab", "matched": "Proxy" }
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
        "description": "**[Montoya API]** Finds the first showing, enabled button anywhere in the Burp window whose text contains `text` (case-insensitive substring) and invokes its click action on the Swing event thread. Best-effort: it matches on visible text only, so prefer labels that are unique on the current tab.",
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
        "description": "**[Montoya API]** Targets the largest visible table with rows (the content table of the open tab, such as Proxy HTTP history or Collaborator interactions) and selects the row at `index`, or the newest row when `last` is true. A real mouse click is dispatched on the cell so Burp's detail panes update, which a plain selection change would not do. Follow with `GET /api/ui/screenshot` to see that row's request and response.",
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
    }
"""

internal fun uiSchemas(): String = """
      "UiTabsResponse": {
        "type": "object",
        "description": "The titles of Burp's top-level tabs and which one is currently selected.",
        "properties": {
          "tabs": { "type": "array", "items": { "type": "string" }, "description": "Tab titles in display order. Index positions match `selected` and the `index` field of select-tab." },
          "selected": { "type": "integer", "description": "Index of the selected tab in `tabs`, or -1 when none is selected." },
          "selected_title": { "type": "string", "nullable": true, "description": "Title of the selected tab, or null when none is selected." }
        }
      },

      "UiSelectTabRequest": {
        "type": "object",
        "description": "Which tab to select. Give one of `name` or `index`; `index` takes precedence when both are present.",
        "properties": {
          "name": { "type": "string", "nullable": true, "description": "Case-insensitive substring of the tab title, for example `proxy` or `collab`." },
          "index": { "type": "integer", "nullable": true, "description": "Zero-based position in the list returned by GET /api/ui/tabs." }
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
          "message": { "type": "string", "description": "What was done, for example `Selected tab` or `Clicked button`." },
          "matched": { "type": "string", "nullable": true, "description": "The element the request resolved to: the tab title, the button's full label, or `row N of M`." }
        }
      }
"""
