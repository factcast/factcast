import { LitElement, html, css } from "lit";
import { customElement, query } from "lit/decorators.js";
import * as monaco from "monaco-editor";
import monacoCss from "monaco-editor/min/vs/editor/editor.main.css";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";

@customElement("json-view")
class JsonView extends LitElement {
  private editor: any;

  @query("#monaco-editor")
  private editorDiv: HTMLDivElement | undefined;

  firstUpdated() {
    super.firstUpdated();

    this.editor = monaco.editor.create(this.editorDiv, {
      language: "json",
      readOnly: true,
      minimap: { enabled: false },
      theme: "vs",
    });
  }

  public renderFact(fact: string) {
    if (this.editor) {
      this.editor.setValue(fact);
    }
  }

  render() {
    return html` <div id="monaco-editor"></div>`;
  }

  static styles = [
    monacoCss,
    css`
      :host {
        width: 100%;
        height: 100%;
      }

      #monaco-editor {
        width: 100%;
        height: 100%;
      }
    `,
  ];
}

self.MonacoEnvironment = {
  getWorker(_, label) {
    switch (label) {
      case "json":
        return new jsonWorker();
      default:
        return new editorWorker();
    }
  },
};
