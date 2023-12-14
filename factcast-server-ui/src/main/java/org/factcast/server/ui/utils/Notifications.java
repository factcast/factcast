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
package org.factcast.server.ui.utils;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import java.util.*;
import lombok.experimental.UtilityClass;
import org.factcast.core.util.NoCoverageReportToBeGenerated;

@UtilityClass
@NoCoverageReportToBeGenerated
public class Notifications {
  public static void error(String... message) {
    createNotification(NotificationVariant.LUMO_ERROR, message);
  }

  public static void warn(String... message) {
    createNotification(NotificationVariant.LUMO_WARNING, message);
  }

  public static void success(String... message) {
    createNotification(NotificationVariant.LUMO_SUCCESS, message);
  }

  static void createNotification(NotificationVariant lumoError, String... message) {
    final var notification = new Notification();
    notification.setDuration(10000);
    notification.addThemeVariants(lumoError);
    notification.setPosition(Notification.Position.BOTTOM_START);

    final var statusText = new VerticalLayout();
    statusText.setPadding(false);
    Arrays.stream(message).forEach(s -> statusText.add(new Div(new Text(s))));

    final var closeButton = new Button(new Icon("lumo", "cross"));
    closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
    closeButton.setAriaLabel("Close");
    closeButton.addClickListener(event -> notification.close());

    final var layout = new HorizontalLayout(statusText, closeButton);
    layout.setAlignItems(FlexComponent.Alignment.CENTER);

    notification.add(layout);
    notification.open();
  }
}
