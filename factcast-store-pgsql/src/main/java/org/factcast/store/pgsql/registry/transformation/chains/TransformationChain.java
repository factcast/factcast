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
package org.factcast.store.pgsql.registry.transformation.chains;

import java.util.List;

import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.factcast.store.pgsql.registry.transformation.TransformationKey;

import com.google.common.base.Preconditions;

import lombok.NonNull;
import lombok.Value;

@Value
public class TransformationChain implements Transformation {

	@NonNull
	TransformationChainId chainId;

	@NonNull
	TransformationKey key;

	int fromVersion;

	int toVersion;

	@NonNull
	String transformationCode;

	@Override
	public boolean isSynthetic() {
		return true;
	}

	public static TransformationChain of(@NonNull TransformationKey key, List<Transformation> orderedListOfSteps,
			TransformationChainId id) {

		Preconditions.checkArgument(!orderedListOfSteps.isEmpty());
		Preconditions.checkArgument(orderedListOfSteps.stream().allMatch(t -> key.equals(t.key())));

		int from = orderedListOfSteps.get(0).fromVersion();
		int to = orderedListOfSteps.get(orderedListOfSteps.size() - 1).toVersion();
		String compositeJson = createCompositeJson(orderedListOfSteps);

		return new TransformationChain(id, key, from, to, compositeJson);
	}

	private static String createCompositeJson(List<Transformation> orderedListOfSteps) {
		StringBuilder sb = new StringBuilder();
		sb.append("var functions = [];");
		orderedListOfSteps.forEach(f -> {
			sb.append("functions.push( ");
			sb.append(f.transformationCode());
			sb.append(" );");
		});
		sb.append("function transform(event) { functions.forEach( f => f(event) ); }");
		return sb.toString();
	}
}
