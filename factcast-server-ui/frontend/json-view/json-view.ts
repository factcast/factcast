import { css, html, LitElement, PropertyValues } from "lit";
import { customElement, query } from "lit/decorators.js";
import * as monaco from "monaco-editor";
import monacoCss from "monaco-editor/min/vs/editor/editor.main.css";
// @ts-ignore
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
// @ts-ignore
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import { ElementNode, MemberNode, parse } from "@humanwhocodes/momoa";
import { IDisposable, IRange, languages } from "monaco-editor";

type FactMetaData = {
  annotations: Record<string, string>;
  hoverContent: Record<string, string[]>;
};

type EnrichedMember = { range: IRange } & Partial<languages.CodeLens> &
  Partial<languages.Hover>;

@customElement("json-view")
class JsonView extends LitElement {
  private editor: monaco.editor.IStandaloneCodeEditor | null = null;
  private codeLensProvider: IDisposable | null = null;
  private hoverProvider: IDisposable | null = null;

  @query("#monaco-editor")
  private editorDiv: HTMLDivElement | undefined;

  private metaData: EnrichedMember[] = [];

  firstUpdated(_changedProperties: PropertyValues) {
    super.firstUpdated(_changedProperties);
    this.setupEditor();
  }

  connectedCallback() {
    super.connectedCallback();
    this.setupEditor();
  }

  disconnectedCallback() {
    super.disconnectedCallback();

    this.editor?.dispose();
    this.codeLensProvider?.dispose();
    this.hoverProvider?.dispose();
    this.editor = null;
  }

  private setupEditor() {
    if (this.editor || !this.editorDiv) {
      return;
    }

    const that = this;

    this.codeLensProvider = monaco.languages.registerCodeLensProvider("json", {
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
        } as languages.CodeLensList;
      },
    });

    this.hoverProvider = monaco.languages.registerHoverProvider("json", {
      async provideHover(model, position, token) {
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

        return payload as languages.Hover;
      },
    });

    this.editor = monaco.editor.create(this.editorDiv, {
      language: "json",
      readOnly: true,
      scrollBeyondLastLine: false,
      minimap: { enabled: false },
      theme: "vs",
      automaticLayout: true,
      fontLigatures: "",
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

    const metaDatas: EnrichedMember[][] = [];

    if (ast.body.type === "Array") {
      ast.body.elements.forEach((e, i) => {
        metaDatas.push(this.createIntelliSense(parsedMetaData, `[${i}]`, e));
      });
    } else if (ast.body.type === "Object") {
      ast.body.members.forEach((e) => {
        metaDatas.push(
          this.createIntelliSense(parsedMetaData, e.name.value, e)
        );
      });
    }

    return metaDatas.flat(Infinity) as EnrichedMember[];
  }

  private createIntelliSense(
    parsedMetaData: FactMetaData,
    path: string,
    member: MemberNode | ElementNode
  ) {
    const fieldsWithEnrichment: EnrichedMember[] = [];

    if (member.value.type === "String" || member.value.type === "Number") {
      if (
        parsedMetaData.annotations[path] ||
        parsedMetaData.hoverContent[path]
      ) {
        const lens: EnrichedMember = {
          range: new monaco.Range(
            member.loc!!.start.line,
            member.loc!!.start.column,
            member.loc!!.end.line,
            member.loc!!.end.column
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
      const subLenses = subMembers
        .map((e: any) =>
          this.createIntelliSense(parsedMetaData, `${path}.${e.name.value}`, e)
        )
        .flat(Infinity) as EnrichedMember[];

      fieldsWithEnrichment.push(...subLenses);
    } else if (member.value.type === "Array") {
      const subMembers = member.value.elements
        .map((e: any, i: number) =>
          this.createIntelliSense(parsedMetaData, `${path}[${i}]`, e)
        )
        .flat(Infinity) as EnrichedMember[];

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
        flex-grow: 1;
      }

      #monaco-editor {
        width: 100%;
        height: 600px;
        border: 1px solid var(--lumo-contrast-20pct);
      }
    `,
  ];
}

// @ts-ignore
self.MonacoEnvironment = {
  getWorker(_: any, label: string) {
    switch (label) {
      case "json":
        return new jsonWorker();
      default:
        return new editorWorker();
    }
  },
};
