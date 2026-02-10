/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.server.ui.views;

import com.vaadin.componentfactory.Popup;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.Getter;
import org.factcast.core.util.NoCoverageReportToBeGenerated;
import org.factcast.server.ui.port.FactRepository;

@Getter
@NoCoverageReportToBeGenerated
public class SerialInputPanel extends HorizontalLayout {
  private final DatePicker since = new DatePicker("First Serial of Day");
  private final DatePicker until = new DatePicker("Last Serial of Day");
  private final BigDecimalField from = new BigDecimalField("Starting Serial");
  private final BigDecimalField to = new BigDecimalField("End serial");
  private final IntegerField limit = new IntegerField("Limit");
  private final IntegerField offset = new IntegerField("Offset");
  private final Popup fromSerialHelperOverlay = new Popup();
  private final Popup toSerialHelperOverlay = new Popup();

  private final transient FactRepository repo;

  public SerialInputPanel(FactRepository repo) {
    this.repo = repo;

    setClassName("flex-wrap");
    setJustifyContentMode(JustifyContentMode.BETWEEN);

    fromSerialHelperOverlay.setTarget(from.getElement());
    from.setId("starting-serial");
    from.setAutocomplete(Autocomplete.OFF);
    from.addValueChangeListener(e -> setDefaultFromSerialAndUpdateEndSerial());
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
    add(from, to);
  }

  public SerialInputPanel withLimitAndOffset() {
    limit.setWidth("auto");
    offset.setWidth("auto");
    add(limit, offset);
    return this;
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

  public void updateFrom() {
    Optional.ofNullable(since.getValue())
        .ifPresentOrElse(
            value -> {
              from.setValue(BigDecimal.valueOf(repo.lastSerialBefore(value).orElse(0)));
              updateEndSerialIfLowerThanStartSerial();
              until.setMin(value);
            },
            () -> from.setValue(null));
  }

  private void setDefaultFromSerialAndUpdateEndSerial() {
    if (from.getValue() == null) {
      from.setValue(BigDecimal.ZERO);
    }

    updateEndSerialIfLowerThanStartSerial();
  }

  private void updateEndSerialIfLowerThanStartSerial() {
    if (until.getValue() != null && since.getValue().isAfter(until.getValue())) {
      until.setValue(since.getValue()); // that would give us one day of facts
    }
    if (to.getValue() != null && from.getValue().compareTo(to.getValue()) > 0) {
      to.setValue(null); // remove the limit
    }
  }

  public void updateTo() {
    Optional.ofNullable(until.getValue())
        .flatMap(repo::firstSerialAfter)
        .map(BigDecimal::valueOf)
        .ifPresentOrElse(to::setValue, () -> to.setValue(null));
  }
}
