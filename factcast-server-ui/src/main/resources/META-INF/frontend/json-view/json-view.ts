import { css, html, LitElement } from "lit";
import { customElement, query } from "lit/decorators.js";
import * as monaco from "monaco-editor";
import monacoCss from "monaco-editor/min/vs/editor/editor.main.css";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import { parse } from "@humanwhocodes/momoa";

@customElement("json-view")
class JsonView extends LitElement {
  private editor: any;

  @query("#monaco-editor")
  private editorDiv: HTMLDivElement | undefined;

  private annotations: { [key: string]: string } = {};

  private createLenses(path: string, member: any[]) {
    const nodesWithLenses: any[] = [];

    if (member.value.type === "String" || member.value.type === "Number") {
      if (this.annotations[path]) {
        const lens = {
          range: new monaco.Range(
            member.loc.start.line,
            member.loc.start.column,
            member.loc.end.line,
            member.loc.end.column
          ),
          command: {
            id: "",
            title: this.annotations[path],
          },
        };

        nodesWithLenses.push(lens);
      }
    } else if (member.value.type === "Object") {
      const subMembers = member.value.members;
      const subLenses = subMembers.map((e) =>
        this.createLenses(`${path}.${e.name.value}`, e)
      );
      nodesWithLenses.push(...subLenses);
    } else if (member.value.type === "Array") {
      const subMembers = member.value.elements.map((e, i) =>
        this.createLenses(`${path}[${i}]`, e)
      );
      nodesWithLenses.push(...subMembers);
    }

    return nodesWithLenses;
  }

  firstUpdated() {
    super.firstUpdated();

    const that = this;

    monaco.languages.registerCodeLensProvider("json", {
      async provideCodeLenses(model, token) {
        if (model.getValue() === "") {
          return {
            lenses: [],
            dispose: () => {},
          };
        }

        const ast = parse(model.getValue());

        const lenses: any[] = [];

        if (ast.body.type === "Array") {
          ast.body.elements.forEach((e, i) => {
            lenses.push(that.createLenses(`[${i}]`, e));
          });
        } else {
          ast.body.members.forEach((e) => {
            lenses.push(that.createLenses(e.name.value, e));
          });
        }

        lenses.flat(Infinity);

        return {
          lenses: lenses.flat(Infinity),
          dispose: () => {},
        };
      },
    });

    this.editor = monaco.editor.create(this.editorDiv, {
      language: "json",
      readOnly: true,
      minimap: { enabled: false },
      theme: "vs",
    });
  }

  public renderJson(json: string, annotations: string) {
    if (this.editor) {
      this.annotations = JSON.parse(annotations);
      this.editor.setValue(json);
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
