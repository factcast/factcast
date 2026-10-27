/*
 * Copyright © 2017-2026 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.server.ui.wicket;

import com.fasterxml.jackson.databind.*;
import java.io.Serializable;
import java.util.*;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.list.*;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.*;
import org.apache.wicket.util.string.Strings;
import org.danekja.java.util.function.serializable.SerializableConsumer;
import org.factcast.server.ui.plugins.JsonViewEntries;

/** A local, accessible JSON tree; plugin content is always escaped before rendering. */
class JsonPanel extends Panel {
  record QuickFilter(int criteria, UUID aggregateId, String key, String value)
      implements Serializable {}

  private record Option(String label, UUID aggregateId, String key, String value)
      implements Serializable {}

  private String json = "";
  private String metadata = "{}";
  private int conditionCount;
  private final List<Option> options = new ArrayList<>();
  private final SerializableConsumer<QuickFilter> applier;
  private final IModel<Integer> condition = Model.of(0);

  JsonPanel(String id, SerializableConsumer<QuickFilter> applier) {
    super(id);
    this.applier = applier;
    add(
        new Label(
                "tree",
                new LoadableDetachableModel<String>() {
                  @Override
                  protected String load() {
                    return renderTree();
                  }
                })
            .setEscapeModelStrings(false));
    var form = new Form<Void>("quickForm");
    form.add(new CsrfField());
    form.add(
        new DropDownChoice<Integer>(
            "condition",
            condition,
            new LoadableDetachableModel<List<Integer>>() {
              @Override
              protected List<Integer> load() {
                return java.util.stream.IntStream.range(0, conditionCount).boxed().toList();
              }
            },
            new IChoiceRenderer<Integer>() {
              @Override
              public Object getDisplayValue(Integer index) {
                return "Condition " + (index + 1);
              }
            }));
    form.add(
        new ListView<Option>("options", options) {
          @Override
          protected void populateItem(ListItem<Option> item) {
            item.add(
                new Button("apply") {
                  @Override
                  public void onSubmit() {
                    var option = item.getModelObject();
                    applier.accept(
                        new QuickFilter(
                            condition.getObject(),
                            option.aggregateId(),
                            option.key(),
                            option.value()));
                  }
                }.add(new Label("label", item.getModelObject().label())));
          }
        });
    form.setVisible(false);
    add(form);
  }

  void show(JsonViewEntries entries, int count) {
    json = entries.json();
    metadata = entries.meta();
    conditionCount = count;
    condition.setObject(0);
    options.clear();
    try {
      var filters = new ObjectMapper().readTree(metadata).path("filterOptions");
      filters
          .fields()
          .forEachRemaining(
              entry -> {
                var option = entry.getValue();
                if (option.hasNonNull("aggregateId")) {
                  UUID id = UUID.fromString(option.get("aggregateId").asText());
                  options.add(new Option("Filter aggregate ID: " + id, id, null, null));
                }
                var meta = option.path("meta");
                if (meta.has("key"))
                  for (var value : meta.path("value")) {
                    options.add(
                        new Option(
                            "Filter " + meta.path("key").asText() + " = " + value.asText(),
                            null,
                            meta.path("key").asText(),
                            value.asText()));
                  }
              });
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot display JSON metadata", e);
    }
    get("quickForm").setVisible(applier != null && count > 0 && !options.isEmpty());
  }

  void setConditionCount(int count) {
    conditionCount = count;
    if (condition.getObject() == null || condition.getObject() >= count) condition.setObject(0);
  }

  void clear() {
    json = "";
    metadata = "{}";
    options.clear();
    get("quickForm").setVisible(false);
  }

  private String renderTree() {
    if (json.isEmpty()) return "<p class=\"empty\">Run a query to display facts.</p>";
    try {
      var mapper = new ObjectMapper();
      var meta = mapper.readTree(metadata);
      var root = mapper.readTree(json);
      if (root.isEmpty()) return "<p class=\"empty\">No facts found.</p>";
      var html = new StringBuilder();
      int i = 0;
      for (var fact : root) {
        html.append("<article class=\"fact\"><div class=\"fact-heading\">Fact ")
            .append(++i)
            .append("</div>");
        var fields = fact.fields();
        while (fields.hasNext()) {
          var field = fields.next();
          append(
              html,
              field.getKey(),
              field.getValue(),
              "$.[" + (i - 1) + "]." + field.getKey() + ".$",
              meta);
        }
        html.append("</article>");
      }
      return html.toString();
    } catch (Exception e) {
      return "<pre>" + escape(json) + "</pre>";
    }
  }

  private static void append(
      StringBuilder html, String key, JsonNode node, String path, JsonNode meta) {
    String tooltip = values(meta.path("hoverContent"), path);
    String annotation = values(meta.path("annotations"), path);
    html.append("<div class=\"json-node\" title=\"").append(escape(tooltip)).append("\">");
    if (node.isContainerNode()) {
      html.append("<details open><summary><span class=\"json-key\">")
          .append(escape(key))
          .append("</span> ")
          .append(node.isArray() ? "[" + node.size() + "]" : "{" + node.size() + "}")
          .append("</summary>");
      if (node.isObject())
        node.fields()
            .forEachRemaining(
                field ->
                    append(
                        html, field.getKey(), field.getValue(), path + "." + field.getKey(), meta));
      else
        for (int i = 0; i < node.size(); i++)
          append(html, "[" + i + "]", node.get(i), path + "[" + i + "]", meta);
      html.append("</details>");
    } else {
      html.append("<span class=\"json-key\">")
          .append(escape(key))
          .append("</span>: <span class=\"json-value\">")
          .append(escape(node.toString()))
          .append("</span>");
    }
    if (!annotation.isEmpty())
      html.append(" <span class=\"annotation\">").append(escape(annotation)).append("</span>");
    html.append("</div>");
  }

  private static String values(JsonNode entries, String path) {
    var text = new StringBuilder();
    entries
        .fields()
        .forEachRemaining(
            entry -> {
              String normalized = entry.getKey().replaceAll("\\['([^']*)'\\]", ".$1");
              if (normalized.equals(path))
                for (var value : entry.getValue()) {
                  if (!text.isEmpty()) text.append(" · ");
                  text.append(value.asText());
                }
            });
    return text.toString();
  }

  private static String escape(String text) {
    return Strings.escapeMarkup(text, false, true).toString();
  }
}
