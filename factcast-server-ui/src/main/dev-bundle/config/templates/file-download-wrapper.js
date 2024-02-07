import { html, LitElement, css } from "lit";
import "@polymer/polymer/polymer-legacy.js";

class FileDownloadWrapper extends LitElement {
  render() {
    return html` <a id="download-link"></a> `;
  }

  createRenderRoot() {
    // Do not use a shadow root
    return this;
  }

  static get is() {
    return "file-download-wrapper";
  }

  static get properties() {
    return {
      // Declare your properties here.
    };
  }
}
customElements.define(FileDownloadWrapper.is, FileDownloadWrapper);
