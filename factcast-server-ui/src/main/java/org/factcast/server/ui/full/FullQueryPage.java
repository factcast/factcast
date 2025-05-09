/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.full;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.plugins.JsonViewEntries;
import org.factcast.server.ui.plugins.JsonViewPluginService;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.BeanValidationUrlStateBinder;
import org.factcast.server.ui.utils.Notifications;
import org.factcast.server.ui.views.*;
import org.factcast.server.ui.views.filter.FactCriteria;
import org.factcast.server.ui.views.filter.FilterCriteriaViews;
import org.factcast.server.ui.views.filter.MetaTuple;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.olli.FileDownloadWrapper;

@Route(value = "ui/full", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Query")
@PermitAll
@SuppressWarnings({"java:S110", "java:S1948"})
@Slf4j
@NoCoverageReportToBeGenerated
public class FullQueryPage extends VerticalLayout implements HasUrlParameter<String> {

  // externalizable state
  private final FullFilterBean formBean;

  // fields
  private final JsonView jsonView = new JsonView(this::updateQuickFilters);

  private final BeanValidationUrlStateBinder<FullFilterBean> binder;
  private final FactRepository repo;

  private final JsonViewPluginService jsonViewPluginService;
  private final FilterCriteriaViews<FullFilterBean> factCriteriaViews;
  private final Button queryBtn = new Button("Query");
  private final Button exportJsonBtn = new Button("Export JSON");
  private JsonViewEntries queryResult;

  public FullQueryPage(
      @NonNull FactRepository repo, @NonNull JsonViewPluginService jsonViewPluginService) {
    setWidthFull();
    setHeightFull();

    this.repo = repo;
    this.jsonViewPluginService = jsonViewPluginService;

    formBean = new FullFilterBean(repo.latestSerial());

    var serialPanel = new SerialInputPanel(repo).withLimitAndOffset();
    binder = createBinding(serialPanel);

    factCriteriaViews = new FilterCriteriaViews<>(repo, binder, formBean);

    final var accordion = new Accordion();
    accordion.setWidthFull();
    accordion.add("Conditions", factCriteriaViews);

    final var form = new FormContent(accordion, serialPanel, formButtons());

    add(form);
    add(jsonView);
    add(serialPanel.fromSerialHelperOverlay());
    add(serialPanel.toSerialHelperOverlay());

    serialPanel.updateFrom();
    serialPanel.updateTo();

    factCriteriaViews.addFilterCriteriaCountUpdateListener(
        (oldCount, newCount) -> {
          // we need to update the jsonView, so that proper tooltip actions are shown for the
          // update filter component numbers
          if (queryResult != null) {
            jsonView.renderFacts(queryResult, formBean.getCriteria().size());
          }
        });
  }

  private void updateQuickFilters(JsonView.QuickFilterOptions options) {
    if (options.affectedCriteria() < 0
        || options.affectedCriteria() >= formBean.getCriteria().size()) {
      // apply to all
      formBean.getCriteria().forEach(fc -> applyQuickFilter(options, fc));
    } else {
      applyQuickFilter(options, formBean.getCriteria().get(options.affectedCriteria()));
    }

    binder.readBean(formBean);
    factCriteriaViews.rebuild();
    runQuery();
  }

  private void applyQuickFilter(JsonView.QuickFilterOptions options, FactCriteria factCriteria) {
    if (options.aggregateId() != null) {
      factCriteria.setAggId(options.aggregateId());
    }

    if (options.meta() != null) {
      final var metaTuple = new MetaTuple();
      metaTuple.setKey(options.meta().key());
      metaTuple.setValue(options.meta().value());

      // first remove meta tuple with the same key, it does not make sense to have the same key
      // filtered twice with different values
      factCriteria.getMeta().removeIf(t -> t.getKey().equals(metaTuple.getKey()));
      factCriteria.getMeta().add(metaTuple);
    }
  }

  private BeanValidationUrlStateBinder<FullFilterBean> createBinding(SerialInputPanel serialPanel) {
    var b = new BeanValidationUrlStateBinder<>(FullFilterBean.class);
    b.forField(serialPanel.from()).withNullRepresentation(BigDecimal.ZERO).bind("from");
    b.forField(serialPanel.to()).bind("to");
    b.forField(serialPanel.since()).bind("since");
    b.forField(serialPanel.until()).bind("until");
    b.forField(serialPanel.limit())
        .withNullRepresentation(FullFilterBean.DEFAULT_LIMIT)
        .bind("limit");
    b.forField(serialPanel.offset()).withNullRepresentation(0).bind("offset");

    b.readBean(formBean);
    return b;
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();

    binder.readFromQueryParams(location.getQueryParameters(), formBean);

    factCriteriaViews.rebuild();
  }

  private void runQuery() {
    try {
      queryBtn.setEnabled(false);
      binder.writeBean(formBean);
      log.info("{} runs query for {}", getLoggedInUserName(), formBean);

      List<Fact> dataFromStore = repo.fetchChunk(formBean);
      JsonViewEntries processedByPlugins = jsonViewPluginService.process(dataFromStore);
      jsonView.renderFacts(processedByPlugins, formBean.getCriteria().size());
      queryResult = processedByPlugins;
      exportJsonBtn.setEnabled(true);
    } catch (ValidationException e) {
      Notifications.warn(e.getMessage());
    } catch (Exception e) {
      Notifications.error(e.getMessage());
    } finally {
      queryBtn.setEnabled(true);
    }
  }

  @NonNull
  private HorizontalLayout formButtons() {
    queryBtn.addClickShortcut(Key.ENTER);
    queryBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    queryBtn.setDisableOnClick(true);
    queryBtn.addClickListener(event -> runQuery());

    final var resetBtn = new Button("Reset");
    resetBtn.addClickListener(
        event -> {
          formBean.reset();
          binder.readBean(formBean);
          factCriteriaViews.rebuild();
          jsonView.clear();
        });

    final var jsonDownload = configureDownloadWrapper(exportJsonBtn);
    final var hl = new HorizontalLayout(queryBtn, resetBtn, jsonDownload);
    hl.setWidthFull();
    hl.addClassName("label-padding");

    return hl;
  }

  private FileDownloadWrapper configureDownloadWrapper(Button button) {
    button.setEnabled(false);
    FileDownloadWrapper buttonWrapper =
        new FileDownloadWrapper(
            new StreamResource(
                "events.json", () -> new ByteArrayInputStream(queryResult.json().getBytes())));
    buttonWrapper.wrapComponent(button);
    return buttonWrapper;
  }

  private String getLoggedInUserName() {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      return authentication.getName();
    } catch (Exception e) {
      log.warn("Cannot retrieve logged in user");
      return "UNKNOWN";
    }
  }
}
