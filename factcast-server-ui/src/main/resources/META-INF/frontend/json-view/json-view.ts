import { css, html, LitElement } from "lit";
import { customElement, query } from "lit/decorators.js";
import * as monaco from "monaco-editor";
import monacoCss from "monaco-editor/min/vs/editor/editor.main.css";
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import { parse } from "@humanwhocodes/momoa";

type FactMetaData = {
  annotations: Record<string, string>;
  hoverContent: Record<string, string[]>;
};

@customElement("json-view")
class JsonView extends LitElement {
  private editor: any;

  @query("#monaco-editor")
  private editorDiv: HTMLDivElement | undefined;

  private metaData: {
    range: monaco.Range;
    command: any;
    contents: any;
  }[] = [];

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

        return {
          lenses: that.metaData,
          dispose: () => {},
        };
      },
    });

    monaco.languages.registerHoverProvider("json", {
      async provideHover(model, position) {
        if (model.getValue() === "") {
          return null;
        }

        const payload = that.metaData.find(
          ({ range }) =>
            (range.startLineNumber < position.lineNumber ||
              (range.startLineNumber === position.lineNumber &&
                range.startColumn <= position.column)) &&
            (range.endLineNumber > position.lineNumber ||
              (range.endLineNumber === position.lineNumber &&
                range.endColumn >= position.column))
        );
        if (!payload) return null;

        return payload;
      },
    });

    this.editor = monaco.editor.create(this.editorDiv, {
      language: "json",
      readOnly: true,
      minimap: { enabled: false },
      theme: "vs",
    });
  }

  public renderJson(json: string, metaData: string) {
    if (this.editor) {
      this.metaData = this.parseMetaData(json, metaData);
      this.editor.setValue(json);
    }
  }

  private parseMetaData(content: string, metaData: string) {
    const parsedMetaData = JSON.parse(metaData) as FactMetaData;
    const ast = parse(content);

    const metaDatas: any[] = [];

    if (ast.body.type === "Array") {
      ast.body.elements.forEach((e, i) => {
        metaDatas.push(this.createIntelliSense(parsedMetaData, `[${i}]`, e));
      });
    } else {
      ast.body.members.forEach((e) => {
        metaDatas.push(
          this.createIntelliSense(parsedMetaData, e.name.value, e)
        );
      });
    }

    return metaDatas.flat(Infinity);
  }

  private createIntelliSense(
    parsedMetaData: FactMetaData,
    path: string,
    member: any
  ) {
    const fieldsWithEnrichment: any[] = [];

    if (member.value.type === "String" || member.value.type === "Number") {
      if (
        parsedMetaData.annotations[path] ||
        parsedMetaData.hoverContent[path]
      ) {
        const lens: any = {
          range: new monaco.Range(
            member.loc.start.line,
            member.loc.start.column,
            member.loc.end.line,
            member.loc.end.column
          ),
        };

        if (parsedMetaData.annotations[path]) {
          lens.command = {
            id: "",
            title: parsedMetaData.annotations[path],
          };
        }

        if (parsedMetaData.hoverContent[path]) {
          lens.contents = parsedMetaData.hoverContent[path].map((x) => ({
            value: x,
            isTrusted: true,
          }));
        }

        fieldsWithEnrichment.push(lens);
      }
    } else if (member.value.type === "Object") {
      const subMembers = member.value.members;
      const subLenses = subMembers.map((e) =>
        this.createIntelliSense(parsedMetaData, `${path}.${e.name.value}`, e)
      );
      fieldsWithEnrichment.push(...subLenses);
    } else if (member.value.type === "Array") {
      const subMembers = member.value.elements.map((e, i) =>
        this.createIntelliSense(parsedMetaData, `${path}[${i}]`, e)
      );
      fieldsWithEnrichment.push(...subMembers);
    }

    return fieldsWithEnrichment;
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
