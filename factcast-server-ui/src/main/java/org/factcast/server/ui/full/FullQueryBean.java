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

import java.util.*;

public class FullQueryBean {
  private String ns = "1";

  private String type = "2";
  private List<String> agg = new LinkedList<>();

  public FullQueryBean() {}

  public String getNs() {
    return this.ns;
  }

  public String getType() {
    return this.type;
  }

  public List<String> getAgg() {
    return this.agg;
  }

  public void setNs(String ns) {
    this.ns = ns;
  }

  public void setType(String type) {
    this.type = type;
  }

  public FullQueryBean setAgg(List<String> agg) {
    this.agg = agg;
    return this;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof FullQueryBean)) return false;
    final FullQueryBean other = (FullQueryBean) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$ns = this.getNs();
    final Object other$ns = other.getNs();
    if (this$ns == null ? other$ns != null : !this$ns.equals(other$ns)) return false;
    final Object this$type = this.getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
    final Object this$agg = this.getAgg();
    final Object other$agg = other.getAgg();
    if (this$agg == null ? other$agg != null : !this$agg.equals(other$agg)) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof FullQueryBean;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $ns = this.getNs();
    result = result * PRIME + ($ns == null ? 43 : $ns.hashCode());
    final Object $type = this.getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $agg = this.getAgg();
    result = result * PRIME + ($agg == null ? 43 : $agg.hashCode());
    return result;
  }

  public String toString() {
    return "FormBean(ns="
        + this.getNs()
        + ", type="
        + this.getType()
        + ", agg="
        + this.getAgg()
        + ")";
  }
}
