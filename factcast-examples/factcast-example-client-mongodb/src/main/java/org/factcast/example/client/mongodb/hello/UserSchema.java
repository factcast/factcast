/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.example.client.mongodb.hello;

import lombok.ToString;
import lombok.Value;

@ToString
@Value
public final class UserSchema {

  private final String firstName;

  private final String lastName;

  private final String displayName;

  UserSchema(String firstName, String lastName, String displayName) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.displayName = displayName;
  }

  // needs to be delombok for javadoc to work
  public static UserSchemaBuilder builder() {
    return new UserSchemaBuilder();
  }

  public String firstName() {
    return this.firstName;
  }

  public static class UserSchemaBuilder {
    private String firstName;
    private String lastName;
    private String displayName;

    UserSchemaBuilder() {}

    public UserSchemaBuilder firstName(String firstName) {
      this.firstName = firstName;
      return this;
    }

    public UserSchemaBuilder lastName(String lastName) {
      this.lastName = lastName;
      return this;
    }

    public UserSchemaBuilder displayName(String displayName) {
      this.displayName = displayName;
      return this;
    }

    public UserSchema build() {
      return new UserSchema(this.firstName, this.lastName, this.displayName);
    }

    public String toString() {
      return "UserSchema.UserSchemaBuilder(firstName="
          + this.firstName
          + ", lastName="
          + this.lastName
          + ", displayName="
          + this.displayName
          + ")";
    }
  }
}
