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

import com.vaadin.componentfactory.Popup;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.button.*;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.router.*;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.plugins.*;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.utils.*;
import org.factcast.server.ui.views.*;
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

  private final FullQueryBean formBean;

  // fields
  private final DatePicker since = new DatePicker("First Serial of Day");
  private final DatePicker until = new DatePicker("Last Serial of Day");
  private final IntegerField limit = new IntegerField("Limit");
  private final IntegerField offset = new IntegerField("Offset");
  private final BigDecimalField from = new BigDecimalField("Start serial");
  private final BigDecimalField to = new BigDecimalField("End serial");
  private final Popup fromSerialHelperOverlay = new Popup();
  private final Popup toSerialHelperOverlay = new Popup();
  private final JsonView jsonView = new JsonView(this::updateQuickFilters);

  private final BeanValidationUrlStateBinder<FullQueryBean> binder;
  private final FactRepository repo;

  private final JsonViewPluginService jsonViewPluginService;
  private final FilterCriteriaViews factCriteriaViews;
  private final Button queryBtn = new Button("Query");
  private final Button exportJsonBtn = new Button("Export JSON");
  private JsonViewEntries queryResult;

  public FullQueryPage(
      @NonNull FactRepository repo, @NonNull JsonViewPluginService jsonViewPluginService) {
    setWidthFull();
    setHeightFull();

    this.repo = repo;
    this.jsonViewPluginService = jsonViewPluginService;

    formBean = new FullQueryBean(repo.latestSerial());

    fromSerialHelperOverlay.setTarget(from.getElement());
    from.setId("starting-serial");
    from.setAutocomplete(Autocomplete.OFF);
    from.addValueChangeListener(e -> updateEndSerialIfLowerThanStartSerial());
    toSerialHelperOverlay.setTarget(to.getElement());
    to.setId("ending-serial");
    to.setAutocomplete(Autocomplete.OFF);
    to.addValueChangeListener(e -> updateEndSerialIfLowerThanStartSerial());
    since.addOpenedChangeListener(
        e -> {
          if (!e.isOpened()) {
            updateFrom();
          }
        });
    until.addOpenedChangeListener(
        e -> {
          if (!e.isOpened()) {
            updateTo();
          }
        });
    until.setMin(since.getValue());

    binder = createBinding();

    factCriteriaViews = new FilterCriteriaViews(repo, binder, formBean);

    final var accordion = new Accordion();
    accordion.setWidthFull();
    accordion.add("Conditions", factCriteriaViews);

    final var form = new FormContent(accordion, new SerialPanel(), formButtons());

    add(form);
    add(jsonView);
    add(fromSerialHelperOverlay);
    add(toSerialHelperOverlay);

    updateFrom();
    updateTo();

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

  private BeanValidationUrlStateBinder<FullQueryBean> createBinding() {
    var b = new BeanValidationUrlStateBinder<>(FullQueryBean.class);
    b.forField(from).withNullRepresentation(BigDecimal.ZERO).bind("from");
    b.forField(to).bind("to");
    b.forField(since).bind("since");
    b.forField(until).bind("until");
    b.forField(limit).withNullRepresentation(FullQueryBean.DEFAULT_LIMIT).bind("limit");
    b.forField(offset).withNullRepresentation(0).bind("offset");

    b.readBean(formBean);
    return b;
  }

  @Override
  public void setParameter(BeforeEvent event, @OptionalParameter String parameter) {
    final var location = event.getLocation();

    binder.readFromQueryParams(location.getQueryParameters(), formBean);
    factCriteriaViews.rebuild();
  }

  private void updateFrom() {
    Optional.ofNullable(since.getValue())
        .ifPresentOrElse(
            value -> {
              from.setValue(BigDecimal.valueOf(repo.lastSerialBefore(value).orElse(0)));
              updateEndSerialIfLowerThanStartSerial();
              until.setMin(value);
            },
            () -> from.setValue(null));
  }

  private void updateEndSerialIfLowerThanStartSerial() {
    if (until.getValue() != null && since.getValue().isAfter(until.getValue())) {
      until.setValue(since.getValue()); // that would give us one day of facts
    }
    if (to.getValue() != null && from.getValue().compareTo(to.getValue()) > 0) {
      to.setValue(null); // remove the limit
    }
  }

  private void updateTo() {
    Optional.ofNullable(until.getValue())
        .flatMap(repo::firstSerialAfter)
        .map(BigDecimal::valueOf)
        .ifPresentOrElse(to::setValue, () -> to.setValue(null));
  }

  @NoCoverageReportToBeGenerated
  class SerialPanel extends HorizontalLayout {
    public SerialPanel() {
      setClassName("flex-wrap");
      setJustifyContentMode(JustifyContentMode.BETWEEN);

      final var fromOverlayContent = getFromVerticalLayout();
      fromOverlayContent.setSpacing(false);
      fromOverlayContent.getThemeList().add("spacing-xs");
      fromOverlayContent.setAlignItems(FlexComponent.Alignment.STRETCH);
      fromSerialHelperOverlay.add(fromOverlayContent);

      final var toOverlayContent = getToVerticalLayout();
      toOverlayContent.setSpacing(false);
      toOverlayContent.getThemeList().add("spacing-xs");
      toOverlayContent.setAlignItems(FlexComponent.Alignment.STRETCH);
      toSerialHelperOverlay.add(toOverlayContent);

      from.setWidth("auto");
      to.setWidth("auto");
      limit.setWidth("auto");
      offset.setWidth("auto");
      add(from, to, limit, offset);
    }

    private VerticalLayout getFromVerticalLayout() {
      Button latestSerial = new Button("Latest serial");
      latestSerial.addClickListener(
          event -> {
            from.setValue(BigDecimal.valueOf(repo.latestSerial()));
            fromSerialHelperOverlay.hide();
          });

      Button fromScratch = new Button("From scratch");
      fromScratch.addClickListener(
          event -> {
            from.setValue(BigDecimal.ZERO);
            fromSerialHelperOverlay.hide();
          });

      final var heading = new H4("Select start serial ");
      return new VerticalLayout(heading, since, latestSerial, fromScratch);
    }

    private VerticalLayout getToVerticalLayout() {
      Button latestSerial = new Button("Remove last serial");
      latestSerial.addClickListener(
          event -> {
            to.setValue(null);
            toSerialHelperOverlay.hide();
          });
      final var heading = new H4("Select last serial");
      return new VerticalLayout(heading, until, latestSerial);
    }
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
