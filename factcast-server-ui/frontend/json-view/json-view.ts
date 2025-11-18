import { css, html, LitElement, PropertyValues } from "lit";
import { customElement, query } from "lit/decorators.js";
import * as monaco from "monaco-editor";
import { IDisposable, languages, Range } from "monaco-editor";
import monacoCss from "monaco-editor/min/vs/editor/editor.main.css?inline";
// @ts-ignore
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
// @ts-ignore
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import { JSONPath, JSONVisitor, visit } from "jsonc-parser";
import { JSONPath as jp } from "jsonpath-plus";

type FactFilterOptions = {
	aggregateId?: string;
	meta?: {
		key: string;
		value: string[];
	};
};

type UpdateFactFilterOptions = {
	aggregateId?: string;
	meta?: {
		key: string;
		value: string;
	};
};

type FactMetaData = {
	annotations: Record<string, string[]>;
	hoverContent: Record<string, string[]>;
	filterOptions: Record<string, FactFilterOptions>;
};

type EnrichedMember = { range: Range } & Partial<languages.CodeLens> &
	Partial<languages.Hover>;

type CompiledPath = {
	originalPath: string;
	compiledPath: JSONPath;
};

type MultiCriteriaUpdateFactFilterOptions = UpdateFactFilterOptions & {
	affectedCriteria: number;
};

type ServerInterface = {
	updateFilters: (filterOptionJson: string) => Promise<void>;
};

@customElement("json-view")
class JsonView extends LitElement {
	private $server?: ServerInterface;

	private editor: monaco.editor.IStandaloneCodeEditor | null = null;
	private codeLensProvider: IDisposable | null = null;
	private hoverProvider: IDisposable | null = null;

	@query("#monaco-editor")
	private editorDiv: HTMLDivElement | undefined;

	private metaData: EnrichedMember[] = [];
	private filterUpdateCommand: string | null = null;
	private quickFilterEnabled: boolean = false;
	private conditionCount: number = 1;

	constructor() {
		super();

		console.log(
			`Facts rendered during the lifetime of this component will be logged and can be used for further processing/aggregation 2.`
		);
	}

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

				const hoverContents = that.metaData.filter(
					({ contents, range }) =>
						contents != null && range.containsPosition(position)
				);

				if (hoverContents.length === 0) {
					return null;
				}

				return {
					range: hoverContents
						.slice(1)
						.reduce((acc, r) => acc.plusRange(r.range), hoverContents[0].range),
					contents: hoverContents.flatMap((x) => x.contents),
				} as languages.Hover;
			},
		});

		this.editor = monaco.editor.create(this.editorDiv, {
			language: "json",
			readOnly: true,
			scrollBeyondLastLine: false,
			minimap: { enabled: false },
			theme: "vs",
			fontLigatures: "",
			automaticLayout: true,
		});

		this.filterUpdateCommand = this.editor.addCommand(
			0,
			async (ctx, arg: MultiCriteriaUpdateFactFilterOptions) => {
				await this.$server?.updateFilters(JSON.stringify(arg));
			}
		);
	}

	public renderJson(
		json: string,
		metaData: string,
		enableQuickFilter: boolean,
		conditionCount: number
	) {
		this.quickFilterEnabled = enableQuickFilter;
		this.conditionCount = conditionCount;

		if (this.editor) {
			this.metaData = this.parseMetaData(json, metaData);
			this.editor.setValue(json);

			console.dir((JSON.parse(json) as []).reverse());
		}
	}

	public clear() {
		this.quickFilterEnabled = false;
		this.conditionCount = 0;
		this.editor?.setValue("");
	}

	private parseMetaData(content: string, metaData: string) {
		const parsedMetaData = JSON.parse(metaData) as FactMetaData;

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

		const filterOptionsMap: CompiledPath[] = Object.keys(
			parsedMetaData.filterOptions
		).map((path) => ({
			originalPath: path,
			compiledPath: this.compilePath(path),
		}));

		const visitor = new MetaDataJsonVisitor({
			factMetaData: parsedMetaData,
			filterUpdateCommand: this.filterUpdateCommand ?? "",
			conditionCount: this.conditionCount,
			withQuickFilters: this.quickFilterEnabled,
			filterOptionsMap,
			hoverMap,
			annotationMap,
		});
		visit(content, {
			onObjectProperty: (
				property: string,
				offset: number,
				length: number,
				startLine: number,
				startCharacter: number,
				pathSupplier: () => JSONPath
			) =>
				visitor.onObjectProperty(
					property,
					offset,
					length,
					startLine,
					startCharacter,
					pathSupplier
				),
		});

		return visitor.enrichedMembers;
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
				flex-grow: 1;
				display: flex;
			}

			#monaco-editor {
				flex-grow: 1;
				width: 100%;
				border: 1px solid var(--lumo-contrast-20pct);
			}
		`,
	];
}

class MetaDataJsonVisitor implements JSONVisitor {
	private readonly annotationMap: CompiledPath[];
	private readonly hoverMap: CompiledPath[];
	private readonly filterOptionsMap: CompiledPath[];
	private readonly factMetaData: FactMetaData;
	private readonly withQuickFilters: boolean;
	private readonly conditionCount: number;
	private readonly filterUpdateCommand: string;
	public readonly enrichedMembers: EnrichedMember[] = [];

	public constructor(data: {
		factMetaData: FactMetaData;
		withQuickFilters: boolean;
		conditionCount: number;
		annotationMap: CompiledPath[];
		hoverMap: CompiledPath[];
		filterOptionsMap: CompiledPath[];
		filterUpdateCommand: string;
	}) {
		this.factMetaData = data.factMetaData;
		this.annotationMap = data.annotationMap;
		this.hoverMap = data.hoverMap;
		this.filterOptionsMap = data.filterOptionsMap;
		this.withQuickFilters = data.withQuickFilters;
		this.conditionCount = data.conditionCount;
		this.filterUpdateCommand = data.filterUpdateCommand;
	}

	public onObjectProperty(
		property: string,
		offset: number,
		length: number,
		startLine: number,
		startCharacter: number,
		pathSupplier: () => JSONPath
	) {
		const finalPath = JSON.stringify([...pathSupplier(), property]);

		this.addEnrichedMembersForPropertyAnnotations(
			finalPath,
			property,
			startLine,
			startCharacter
		);
		this.addEnrichedMembersForPropertyHovers(
			finalPath,
			property,
			startLine,
			startCharacter
		);
		this.addEnrichedMembersForQuickFilters(
			finalPath,
			property,
			startLine,
			startCharacter
		);
	}

	private addEnrichedMembersForPropertyAnnotations(
		finalPath: string,
		property: string,
		startLine: number,
		startCharacter: number
	) {
		const annotation = this.annotationMap.find(
			(x) => JSON.stringify(x.compiledPath) === finalPath
		);

		if (!annotation) {
			return;
		}
		if (annotation?.originalPath) {
			const enrichedMember: EnrichedMember = {
				range: new monaco.Range(
					startLine + 1,
					startCharacter + 1, // +1  zero based index
					startLine + 1,
					startCharacter + property.length + 3 // +2 for the quote and zero based index
				),
			};
			enrichedMember.command = {
				id: "",
				title:
					this.factMetaData.annotations[annotation.originalPath].join(", "),
			};
			this.enrichedMembers.push(enrichedMember);
		}
	}

	private addEnrichedMembersForPropertyHovers(
		finalPath: string,
		property: string,
		startLine: number,
		startCharacter: number
	) {
		const hoverContent = this.hoverMap.find(
			(x) => JSON.stringify(x.compiledPath) === finalPath
		);

		if (!hoverContent) {
			return;
		}
		if (hoverContent?.originalPath) {
			const enrichedMember: EnrichedMember = {
				range: new monaco.Range(
					startLine + 1,
					startCharacter + 1, // +1  zero based index
					startLine + 1,
					startCharacter + property.length + 3 // +2 for the quote and zero based index
				),
			};
			enrichedMember.contents = this.factMetaData.hoverContent[
				hoverContent.originalPath
			].map((x) => ({
				isTrusted: true,
				value: x,
			}));
			this.enrichedMembers.push(enrichedMember);
		}
	}

	private addEnrichedMembersForQuickFilters(
		finalPath: string,
		property: string,
		startLine: number,
		startCharacter: number
	) {
		if (!this.withQuickFilters) return;

		const filterOptionsContent = this.filterOptionsMap.find(
			(x) => JSON.stringify(x.compiledPath) === finalPath
		);

		if (!filterOptionsContent) return;

		if (filterOptionsContent.originalPath) {
			const filter =
				this.factMetaData.filterOptions[filterOptionsContent.originalPath];

			if (Object.keys(filter).length === 0) {
				// no filter options defined for this property
				return;
			}

			if (filter.aggregateId) {
				const enrichedMember = this.buildQuickFilterForAggregateId(
					startCharacter,
					property,
					filter.aggregateId,
					startLine
				);
				this.enrichedMembers.push(enrichedMember);
			} else if (filter.meta) {
				if (filter.meta.value.length === 1) {
					const enrichedMember = this.buildQuickFilterForSingleMeta(
						startCharacter,
						property,
						filter.meta.key,
						filter.meta.value[0],
						startLine
					);
					this.enrichedMembers.push(enrichedMember);
				} else if (filter.meta.value.length > 1) {
					const enrichedMembers = this.buildQuickFilterForMultiMeta(
						startCharacter,
						property,
						filter.meta.key,
						filter.meta.value,
						startLine
					);
					this.enrichedMembers.push(...enrichedMembers);
				}
			} else {
				console.error("Unsupported filter option:", filter);
			}
		}
	}

	private buildQuickFilterForMultiMeta(
		startCharacter: number,
		property: string,
		metaKey: string,
		metaValues: string[],
		startLine: number
	) {
		const valuesStartColumn = startCharacter + 1 + property.length + 7; // starts at the first value after the opening brackets

		return metaValues.map((value, index) => {
			const startOfValueColumn = metaValues
				.slice(0, index)
				.map((x) => x.length)
				.reduce((a, b) => a + b + 4, 0);

			const enrichedMember: EnrichedMember = {
				range: new monaco.Range(
					startLine + 1,
					valuesStartColumn + startOfValueColumn,
					startLine + 1,
					valuesStartColumn + startOfValueColumn + value.length + 2
				),
				contents: this.buildFilterCommandLinks(`${metaKey}:${value}`, {
					meta: { key: metaKey, value },
				}),
			};
			return enrichedMember;
		});
	}

	private buildQuickFilterForSingleMeta(
		startCharacter: number,
		property: string,
		metaKey: string,
		metaValue: string,
		startLine: number
	) {
		// expand range to cover value as well
		const rangeEnd =
			startCharacter +
			1 + // for zero based index
			property.length +
			7 + // for quotes around property and value and the " : " in the middle
			metaValue.length;

		const enrichedMember: EnrichedMember = {
			range: new monaco.Range(
				startLine + 1,
				startCharacter + 1, // +1  zero based index
				startLine + 1,
				rangeEnd
			),
			contents: this.buildFilterCommandLinks(`${metaKey}:${metaValue}`, {
				meta: { key: metaKey, value: metaValue },
			}),
		};
		return enrichedMember;
	}

	private buildQuickFilterForAggregateId(
		startCharacter: number,
		property: string,
		aggregateId: string,
		startLine: number
	) {
		// expand range to cover value as well
		const rangeEnd =
			startCharacter +
			1 + // for zero based index
			property.length +
			7 + // for quotes around property and value and the " : " in the middle
			aggregateId.length;

		const enrichedMember: EnrichedMember = {
			range: new monaco.Range(
				startLine + 1,
				startCharacter + 1, // +1  zero based index
				startLine + 1,
				rangeEnd
			),
			contents: this.buildFilterCommandLinks(`Aggregate-ID ${aggregateId}`, {
				aggregateId,
			}),
		};
		return enrichedMember;
	}

	private buildFilterCommandLinks(
		forText: string,
		options: UpdateFactFilterOptions
	): monaco.IMarkdownString[] {
		if (this.conditionCount === 1) {
			const encodedArgs = encodeURIComponent(
				JSON.stringify({ ...options, affectedCriteria: 0 })
			);
			return [
				{
					isTrusted: true,
					value: `[Filter for ${forText}](command:${this.filterUpdateCommand}?${encodedArgs})`,
				},
			];
		}
		const encodedArgsForAll = encodeURIComponent(
			JSON.stringify({ ...options, affectedCriteria: -1 })
		);
		const allLinks: monaco.IMarkdownString[] = [
			{
				isTrusted: true,
				value: `[Filter for ${forText} on all conditions](command:${this.filterUpdateCommand}?${encodedArgsForAll})`,
			},
		];
		for (let i = 0; i < this.conditionCount; i++) {
			const encodedArgs = encodeURIComponent(
				JSON.stringify({ ...options, affectedCriteria: i })
			);
			allLinks.push({
				isTrusted: true,
				value: `[Filter for ${forText} on condition ${i + 1}](command:${
					this.filterUpdateCommand
				}?${encodedArgs})`,
			});
		}
		return allLinks;
	}
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
