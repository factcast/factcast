import { css, html, LitElement, PropertyValues } from "lit";
import { customElement, query } from "lit/decorators.js";
import * as monaco from "monaco-editor";
import monacoCss from "monaco-editor/min/vs/editor/editor.main.css";
// @ts-ignore
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
// @ts-ignore
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import { IDisposable, IRange, languages } from "monaco-editor";
import { visit, JSONPath } from "jsonc-parser";
import { JSONPath as jp } from "jsonpath-plus";

type FactMetaData = {
	annotations: Record<string, string[]>;
	hoverContent: Record<string, string[]>;
};

type EnrichedMember = { range: IRange } & Partial<languages.CodeLens> &
	Partial<languages.Hover>;

type CompiledPath = {
	originalPath: string;
	compiledPath: JSONPath;
};

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
			async provideCodeLenses(model) {
				if (model.getValue() === "") {
					return {
						lenses: [],
						dispose: () => {},
					};
				}

				return {
					lenses: that.metaData.filter((x) => x.command != null),
					dispose: () => {},
				} as languages.CodeLensList;
			},
		});

		this.hoverProvider = monaco.languages.registerHoverProvider("json", {
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

				if (!payload.contents) return null;

				return payload as languages.Hover;
			},
		});

		this.editor = monaco.editor.create(this.editorDiv, {
			language: "json",
			readOnly: true,
			scrollBeyondLastLine: false,
			minimap: { enabled: false },
			theme: "vs",
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
		const enrichedMembers: EnrichedMember[] = [];

		const annotationMap: CompiledPath[] = Object.keys(
			parsedMetaData.annotations
		).map((path) => ({
			originalPath: path,
			compiledPath: this.compilePath(path),
		}));

		const hoverMap: CompiledPath[] = Object.keys(
			parsedMetaData.hoverContent
		).map((path) => ({
			originalPath: path,
			compiledPath: this.compilePath(path),
		}));

		visit(content, {
			onObjectProperty(
				property: string,
				offset: number,
				length: number,
				startLine: number,
				startCharacter: number,
				pathSupplier: () => JSONPath
			) {
				const finalPath = JSON.stringify([...pathSupplier(), property]);
				const annotation = annotationMap.find(
					(x) => JSON.stringify(x.compiledPath) === finalPath
				);

				const hoverContent = hoverMap.find(
					(x) => JSON.stringify(x.compiledPath) === finalPath
				);

				if (!annotation && !hoverContent) return;
				const enrichedMember: EnrichedMember = {
					range: new monaco.Range(
						startLine + 1,
						startCharacter + 1, // +1  zero based index
						startLine + 1,
						startCharacter + property.length + 3 // +2 for the quote and zero based index
					),
				};

				if (annotation?.originalPath) {
					enrichedMember.command = {
						id: "",
						title:
							parsedMetaData.annotations[annotation.originalPath].join(", "),
					};
				}

				if (hoverContent?.originalPath) {
					enrichedMember.contents = parsedMetaData.hoverContent[
						hoverContent.originalPath
					].map((x) => ({
						isTrusted: true,
						value: x,
					}));
				}

				enrichedMembers.push(enrichedMember);
			},
		});

		return enrichedMembers;
	}

	private compilePath(path: string) {
		return jp
			.toPathArray(path)
			.filter((x) => x !== "..")
			.filter((x) => x !== "$")
			.map((x) => this.getNumber(x));
	}

	private getNumber(s: string) {
		const num = parseInt(s, 10);
		return isNaN(num) ? s : num;
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
				flex-grow: 1;
				display: flex;
			}

			#monaco-editor {
				height: 100%;
				flex-grow: 1;
				width: 100%;
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
